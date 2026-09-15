// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Cluster harness using `cluster_manager.py`.

use glide::{
    GlideClusterClient, GlideClusterClientConfiguration, ProtocolVersion, Route, TlsConfig,
};
use std::path::PathBuf;
use std::process::{Command, Stdio};
use std::time::Duration;

/// Extract the first unsigned integer value for `"field"` in a JSON fragment,
/// e.g. `field_u64(r#""port": 6379,"#, "\"port\"") == Some(6379)`. A tiny
/// dependency-free parser sufficient for cluster_manager.py's `SERVERS_JSON`.
fn field_u64(fragment: &str, field: &str) -> Option<u64> {
    let idx = fragment.find(field)?;
    let after = &fragment[idx + field.len()..];
    let digits: String = after
        .chars()
        .skip_while(|c| !c.is_ascii_digit())
        .take_while(|c| c.is_ascii_digit())
        .collect();
    digits.parse().ok()
}

const CLUSTER_MANAGER: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../utils/cluster_manager.py");
const TLS_CERTIFICATES_DIR: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../utils/tls_crts");

/// Returns the CA certificate bytes (`ca.crt`).
fn ca_pem() -> Vec<u8> {
    read_cert("ca.crt")
}

/// Returns the server certificate bytes (`server.crt`).
fn server_cert_pem() -> Vec<u8> {
    read_cert("server.crt")
}

/// Returns the server private key bytes (`server.key`).
fn server_key_pem() -> Vec<u8> {
    read_cert("server.key")
}

/// Returns the bytes for the given certificate.
fn read_cert(name: &str) -> Vec<u8> {
    let path = PathBuf::from(TLS_CERTIFICATES_DIR).join(name);
    std::fs::read(&path)
        .unwrap_or_else(|e| panic!("could not read TLS certificate {}: {e}", path.display()))
}

/// A cluster created with `cluster_manager.py`.
pub struct ClusterHarness {
    /// The `--cluster-folder` used to stop the cluster on drop.
    folder: String,

    /// Primary node ports.
    pub primary_ports: Vec<u16>,

    /// Replica node ports.
    pub replica_ports: Vec<u16>,

    /// Whether the cluster is TLS-enabled.
    tls: bool,
}

impl ClusterHarness {
    /// Start a 3-primary cluster using `cluster_manager.py`.
    /// Panics if the cluster cannot be created.
    pub fn start() -> ClusterHarness {
        Self::start_via_cluster_manager(3, 1, false, false)
    }

    /// Start a 3-primary TLS cluster using `cluster_manager.py`.
    /// Panics if the cluster cannot be created.
    pub fn start_tls() -> ClusterHarness {
        Self::start_via_cluster_manager(3, 1, true, false)
    }

    /// Start a 3-primary TLS cluster with mTLS using `cluster_manager.py`.
    /// Panics if the cluster cannot be created.
    pub fn start_tls_mtls() -> ClusterHarness {
        Self::start_via_cluster_manager(3, 1, true, true)
    }

    /// Starts a cluster with:
    /// - the specified number of shards
    /// - the specified number of replicas per shard
    /// - TLS enabled if specified
    /// - mTLS enabled if specified
    ///
    /// Panics if the script fails.
    fn start_via_cluster_manager(
        shards: usize,
        replicas: usize,
        tls: bool,
        mtls: bool,
    ) -> ClusterHarness {
        let shards = shards.to_string();
        let replicas = replicas.to_string();

        // [1] Run `cluster_manager.py`.
        let mut args: Vec<&str> = vec![CLUSTER_MANAGER];

        if tls {
            args.push("--tls");
        }

        args.extend([
            "start",
            "--cluster-mode",
            "-n",
            shards.as_str(),
            "-r",
            replicas.as_str(),
        ]);

        if mtls {
            args.push("--tls-auth-clients");
        }

        let result = Command::new("python3").args(&args).output();

        let out = match result {
            Ok(out) => out,
            Err(e) => panic!("could not run cluster_manager.py: {e}"),
        };

        assert!(
            out.status.success(),
            "cluster_manager.py exited with {}:\n{}",
            out.status,
            String::from_utf8_lossy(&out.stderr)
        );

        // [2] Parse CLUSTER_FOLDER and CLUSTER_NODES from output.
        let stdout = String::from_utf8_lossy(&out.stdout);

        let mut folder: Option<String> = None;
        let mut nodes: Vec<(String, u16)> = Vec::new();
        for line in stdout.lines() {
            if let Some(rest) = line.strip_prefix("CLUSTER_FOLDER=") {
                folder = Some(rest.trim().to_string());
            } else if let Some(rest) = line.strip_prefix("CLUSTER_NODES=") {
                for addr in rest.trim().split(',') {
                    if let Some((h, p)) = addr.rsplit_once(':')
                        && let Ok(port) = p.parse::<u16>()
                    {
                        nodes.push((h.to_string(), port));
                    }
                }
            }
        }

        let folder = folder.unwrap_or_else(|| {
            panic!("cluster_manager.py output missing CLUSTER_FOLDER=:\n{stdout}")
        });
        assert!(
            !nodes.is_empty(),
            "cluster_manager.py output missing CLUSTER_NODES=:\n{stdout}"
        );

        // [3] Parse SERVERS_JSON from output.
        let mut primary_ports: Vec<u16> = Vec::new();
        let mut replica_ports: Vec<u16> = Vec::new();
        if let Some(json_line) = stdout.lines().find(|l| l.starts_with("SERVERS_JSON=")) {
            let json = &json_line["SERVERS_JSON=".len()..];
            for obj in json.split('{').skip(1) {
                let port = field_u64(obj, "\"port\"").map(|v| v as u16);
                let is_primary = obj
                    .split_once("\"is_primary\"")
                    .map(|(_, r)| r.contains("true"))
                    .unwrap_or(false);
                if let Some(port) = port {
                    if is_primary {
                        primary_ports.push(port);
                    } else {
                        replica_ports.push(port);
                    }
                }
            }
        }

        // If `cluster_manager.py` didn't report a primary node,
        // treat every node as a primary.
        if primary_ports.is_empty() {
            primary_ports = nodes.iter().map(|(_, p)| *p).collect();
        }

        ClusterHarness {
            folder,
            primary_ports,
            replica_ports,
            tls,
        }
    }

    /// The seed `host:port` used to connect a cluster client.
    pub fn seed_port(&self) -> u16 {
        self.primary_ports[0]
    }

    /// Connect a cluster client.
    pub async fn client(&self) -> GlideClusterClient {
        self.client_with_protocol(ProtocolVersion::RESP3).await
    }

    /// Connect a cluster client to this cluster with the given protocol.
    pub async fn client_with_protocol(&self, protocol: ProtocolVersion) -> GlideClusterClient {
        self.connect(self.config().protocol(protocol)).await
    }

    /// Connect a secure-TLS client.
    pub async fn client_with_tls(&self) -> GlideClusterClient {
        self.connect(self.config_with_tls()).await
    }

    /// Connect a mutual-TLS client.
    pub async fn client_with_mtls(&self) -> GlideClusterClient {
        let config = self
            .config_with_tls()
            .client_identity(server_cert_pem(), server_key_pem());
        self.connect(config).await
    }

    /// Connect an insecure-TLS client.
    pub async fn client_with_insecure_tls(&self) -> GlideClusterClient {
        self.connect(self.config().tls(TlsConfig::InsecureTls))
            .await
    }

    /// A base client configuration.
    pub fn config(&self) -> GlideClusterClientConfiguration {
        GlideClusterClientConfiguration::with_address("127.0.0.1", self.seed_port())
            .connection_timeout(Duration::from_secs(10))
            .request_timeout(Duration::from_secs(10))
    }

    /// A base client configuration with secure TLS enabled and the shared CA trusted.
    pub fn config_with_tls(&self) -> GlideClusterClientConfiguration {
        self.config()
            .tls(TlsConfig::SecureTls)
            .tls_ca_cert(ca_pem())
    }

    /// Connect with bounded retry, then warm up the topology. Under load a
    /// freshly-formed cluster can briefly refuse or time out the initial
    /// connection; a single attempt shouldn't fail the whole test. Panics if the
    /// connection cannot be established — use for the positive path.
    async fn connect(&self, config: GlideClusterClientConfiguration) -> GlideClusterClient {
        let mut client = None;
        let mut last_err = None;
        for attempt in 0..10u32 {
            match GlideClusterClient::connect(config.clone()).await {
                Ok(c) => {
                    client = Some(c);
                    break;
                }
                Err(e) => {
                    last_err = Some(e);
                    tokio::time::sleep(Duration::from_millis(100 * (attempt + 1) as u64)).await;
                }
            }
        }
        let Some(client) = client else {
            panic!("could not connect a cluster client: {last_err:?}");
        };

        // Converge the connection map before handing the client to a test: right
        // after a cluster forms, the client's topology snapshot can lag, so the
        // first routed op may hit a `MOVED` to a not-yet-connected node
        // (`ConnectionNotFoundForRoute`). A retried broadcast PING forces the
        // client to connect to every primary, eliminating that startup race.
        warm_up_cluster(&client).await;
        client
    }
}

impl Drop for ClusterHarness {
    fn drop(&mut self) {
        // Build arguments.
        let mut args: Vec<&str> = vec![CLUSTER_MANAGER];
        if self.tls {
            args.push("--tls");
        }
        args.extend(["stop", "--cluster-folder", &self.folder]);

        // Run `cluster_manager.py`.
        let _ = Command::new("python3")
            .args(&args)
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
    }
}

/// Whether an error is a *transient* cluster-topology error — the kind that can
/// occur while a freshly-formed cluster client's slot→node connection map is
/// still converging. Right after a cluster forms, a command can hit a `MOVED`
/// redirect to a node not yet in the connection map, surfacing as
/// `ConnectionNotFoundForRoute`; a topology refresh is triggered but the
/// in-flight op fails. `TRYAGAIN` (multi-key op spanning a slot mid-migration)
/// and `LOADING` (a node still reading its dataset into memory) are likewise
/// transient right after startup. These are all safe to retry.
pub fn is_transient_cluster_error(e: &glide::GlideError) -> bool {
    let m = e.to_string();
    m.contains("ConnectionNotFoundForRoute")
        || m.contains("Requested connection not found")
        || m.contains("connection map")
        || m.contains("MOVED")
        || m.contains("Moved")
        || m.contains("TRYAGAIN")
        || m.contains("TryAgain")
        || m.contains("LOADING")
        || m.contains("Loading")
        || m.contains("loading the dataset")
}

/// Force a freshly-connected cluster client to connect to every primary so its
/// slot→node connection map is fully populated before a test issues routed or
/// scan commands. Best-effort: retries a broadcast `PING` on transient topology
/// errors, and returns once it succeeds (or after a bounded number of attempts /
/// on a non-transient error, leaving the test to surface any real problem).
async fn warm_up_cluster(client: &GlideClusterClient) {
    for attempt in 0..20u32 {
        let mut ping = redis::Cmd::new();
        ping.arg("PING");
        match client.route_command(ping, Route::AllPrimaries).await {
            Ok(_) => return,
            Err(e) if is_transient_cluster_error(&e) => {
                tokio::time::sleep(Duration::from_millis(50 * (attempt + 1) as u64)).await;
            }
            Err(_) => return,
        }
    }
}
