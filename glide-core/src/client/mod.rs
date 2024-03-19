/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */
mod types;

use crate::scripts_container::get_script;
use futures::FutureExt;
use logger_core::log_info;
use redis::aio::ConnectionLike;
use redis::cluster_async::ClusterConnection;
use redis::cluster_routing::{RoutingInfo, SingleNodeRoutingInfo};
use redis::RedisResult;
use redis::{Cmd, ErrorKind, Value};
pub use standalone_client::StandaloneClient;
use std::io;
use std::ops::Deref;
use std::time::Duration;
pub use types::*;

use self::value_conversion::{convert_to_expected_type, expected_type_for_cmd};
mod reconnecting_connection;
mod standalone_client;
mod value_conversion;

pub const HEARTBEAT_SLEEP_DURATION: Duration = Duration::from_secs(1);

pub const DEFAULT_RESPONSE_TIMEOUT: Duration = Duration::from_millis(250);
pub const DEFAULT_CONNECTION_ATTEMPT_TIMEOUT: Duration = Duration::from_millis(250);
pub const DEFAULT_PERIODIC_CHECKS_INTERVAL: Duration = Duration::from_secs(60);
pub const INTERNAL_CONNECTION_TIMEOUT: Duration = Duration::from_millis(250);

pub(super) fn get_port(address: &NodeAddress) -> u16 {
    const DEFAULT_PORT: u16 = 6379;
    if address.port == 0 {
        DEFAULT_PORT
    } else {
        address.port
    }
}

pub(super) fn get_redis_connection_info(
    connection_request: &ConnectionRequest,
) -> redis::RedisConnectionInfo {
    let protocol = connection_request.protocol.unwrap_or_default();
    let db = connection_request.database_id;
    let client_name = connection_request.client_name.clone();
    match &connection_request.authentication_info {
        Some(info) => redis::RedisConnectionInfo {
            db,
            username: info.username.clone(),
            password: info.password.clone(),
            protocol,
            client_name,
        },
        None => redis::RedisConnectionInfo {
            db,
            protocol,
            client_name,
            ..Default::default()
        },
    }
}

pub(super) fn get_connection_info(
    address: &NodeAddress,
    tls_mode: TlsMode,
    redis_connection_info: redis::RedisConnectionInfo,
) -> redis::ConnectionInfo {
    let addr = if tls_mode != TlsMode::NoTls {
        redis::ConnectionAddr::TcpTls {
            host: address.host.to_string(),
            port: get_port(address),
            insecure: tls_mode == TlsMode::InsecureTls,
            tls_params: None,
        }
    } else {
        redis::ConnectionAddr::Tcp(address.host.to_string(), get_port(address))
    };
    redis::ConnectionInfo {
        addr,
        redis: redis_connection_info,
    }
}

#[derive(Clone)]
pub enum ClientWrapper {
    Standalone(StandaloneClient),
    Cluster { client: ClusterConnection },
}

#[derive(Clone)]
pub struct Client {
    internal_client: ClientWrapper,
    request_timeout: Duration,
}

async fn run_with_timeout<T>(
    timeout: Duration,
    future: impl futures::Future<Output = RedisResult<T>> + Send,
) -> redis::RedisResult<T> {
    tokio::time::timeout(timeout, future)
        .await
        .map_err(|_| io::Error::from(io::ErrorKind::TimedOut).into())
        .and_then(|res| res)
}

impl Client {
    pub fn send_command<'a>(
        &'a mut self,
        cmd: &'a Cmd,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, Value> {
        let expected_type = expected_type_for_cmd(cmd);
        run_with_timeout(self.request_timeout, async move {
            match self.internal_client {
                ClientWrapper::Standalone(ref mut client) => client.send_command(cmd).await,

                ClientWrapper::Cluster { ref mut client } => {
                    let routing = routing
                        .or_else(|| RoutingInfo::for_routable(cmd))
                        .unwrap_or(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random));
                    client.route_command(cmd, routing).await
                }
            }
            .and_then(|value| convert_to_expected_type(value, expected_type))
        })
        .boxed()
    }

    fn get_transaction_values(
        pipeline: &redis::Pipeline,
        mut values: Vec<Value>,
        command_count: usize,
        offset: usize,
    ) -> RedisResult<Value> {
        assert_eq!(values.len(), 1);
        let value = values.pop();
        let values = match value {
            Some(Value::Array(values)) => values,
            Some(Value::Nil) => {
                return Ok(Value::Nil);
            }
            Some(value) => {
                if offset == 2 {
                    vec![value]
                } else {
                    return Err((
                        ErrorKind::ResponseError,
                        "Received non-array response for transaction",
                        format!("Response: `{value:?}`"),
                    )
                        .into());
                }
            }
            _ => {
                return Err((
                    ErrorKind::ResponseError,
                    "Received empty response for transaction",
                )
                    .into());
            }
        };
        Self::convert_transaction_values_to_expected_types(pipeline, values, command_count)
    }

    fn convert_transaction_values_to_expected_types(
        pipeline: &redis::Pipeline,
        values: Vec<Value>,
        command_count: usize,
    ) -> RedisResult<Value> {
        let values = values
            .into_iter()
            .zip(pipeline.cmd_iter().map(expected_type_for_cmd))
            .map(|(value, expected_type)| convert_to_expected_type(value, expected_type))
            .try_fold(
                Vec::with_capacity(command_count),
                |mut acc, result| -> RedisResult<_> {
                    acc.push(result?);
                    Ok(acc)
                },
            )?;
        Ok(Value::Array(values))
    }

    pub fn send_transaction<'a>(
        &'a mut self,
        pipeline: &'a redis::Pipeline,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, Value> {
        let command_count = pipeline.cmd_iter().count();
        let offset = command_count + 1;
        run_with_timeout(self.request_timeout, async move {
            let values = match self.internal_client {
                ClientWrapper::Standalone(ref mut client) => {
                    client.send_pipeline(pipeline, offset, 1).await
                }

                ClientWrapper::Cluster { ref mut client } => match routing {
                    Some(RoutingInfo::SingleNode(route)) => {
                        client.route_pipeline(pipeline, offset, 1, route).await
                    }
                    _ => client.req_packed_commands(pipeline, offset, 1).await,
                },
            }?;

            Self::get_transaction_values(pipeline, values, command_count, offset)
        })
        .boxed()
    }

    pub async fn invoke_script<'a, T: Deref<Target = str>>(
        &'a mut self,
        hash: &'a str,
        keys: Vec<T>,
        args: Vec<T>,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisResult<Value> {
        let eval = eval_cmd(hash, keys, args);
        let result = self.send_command(&eval, routing.clone()).await;
        let Err(err) = result else {
            return result;
        };
        if err.kind() == ErrorKind::NoScriptError {
            let Some(code) = get_script(hash) else {
                return Err(err);
            };
            let load = load_cmd(code.as_str());
            self.send_command(&load, None).await?;
            self.send_command(&eval, routing).await
        } else {
            Err(err)
        }
    }
}

fn load_cmd(code: &str) -> Cmd {
    let mut cmd = redis::cmd("SCRIPT");
    cmd.arg("LOAD").arg(code);
    cmd
}

fn eval_cmd<T: Deref<Target = str>>(hash: &str, keys: Vec<T>, args: Vec<T>) -> Cmd {
    let mut cmd = redis::cmd("EVALSHA");
    cmd.arg(hash).arg(keys.len());
    for key in keys {
        cmd.arg(&*key);
    }
    for arg in args {
        cmd.arg(&*arg);
    }
    cmd
}

fn to_duration(time_in_millis: Option<u32>, default: Duration) -> Duration {
    time_in_millis
        .map(|val| Duration::from_millis(val as u64))
        .unwrap_or(default)
}

async fn create_cluster_client(
    request: ConnectionRequest,
) -> RedisResult<redis::cluster_async::ClusterConnection> {
    // TODO - implement timeout for each connection attempt
    let tls_mode = request.tls_mode.unwrap_or_default();
    let redis_connection_info = get_redis_connection_info(&request);
    let initial_nodes: Vec<_> = request
        .addresses
        .into_iter()
        .map(|address| get_connection_info(&address, tls_mode, redis_connection_info.clone()))
        .collect();
    let read_from = request.read_from.unwrap_or_default();
    let read_from_replicas = !matches!(read_from, ReadFrom::Primary); // TODO - implement different read from replica strategies.
    let periodic_checks = match request.periodic_checks {
        Some(PeriodicCheck::Disabled) => None,
        Some(PeriodicCheck::Enabled) => Some(DEFAULT_PERIODIC_CHECKS_INTERVAL),
        Some(PeriodicCheck::ManualInterval(interval)) => Some(interval),
        None => Some(DEFAULT_PERIODIC_CHECKS_INTERVAL),
    };
    let mut builder = redis::cluster::ClusterClientBuilder::new(initial_nodes)
        .connection_timeout(INTERNAL_CONNECTION_TIMEOUT);
    if read_from_replicas {
        builder = builder.read_from_replicas();
    }
    if let Some(interval_duration) = periodic_checks {
        builder = builder.periodic_topology_checks(interval_duration);
    }
    builder = builder.use_protocol(request.protocol.unwrap_or_default());
    if let Some(client_name) = redis_connection_info.client_name {
        builder = builder.client_name(client_name);
    }
    if tls_mode != TlsMode::NoTls {
        let tls = if tls_mode == TlsMode::SecureTls {
            redis::cluster::TlsMode::Secure
        } else {
            redis::cluster::TlsMode::Insecure
        };
        builder = builder.tls(tls);
    }
    let client = builder.build()?;
    client.get_async_connection().await
}

#[derive(thiserror::Error)]
pub enum ConnectionError {
    Standalone(standalone_client::StandaloneClientConnectionError),
    Cluster(redis::RedisError),
    Timeout,
}

impl std::fmt::Debug for ConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Standalone(arg0) => f.debug_tuple("Standalone").field(arg0).finish(),
            Self::Cluster(arg0) => f.debug_tuple("Cluster").field(arg0).finish(),
            Self::Timeout => write!(f, "Timeout"),
        }
    }
}

impl std::fmt::Display for ConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ConnectionError::Standalone(err) => write!(f, "{err:?}"),
            ConnectionError::Cluster(err) => write!(f, "{err}"),
            ConnectionError::Timeout => f.write_str("connection attempt timed out"),
        }
    }
}

fn format_optional_value<T>(name: &'static str, value: Option<T>) -> String
where
    T: std::fmt::Display,
{
    if let Some(value) = value {
        format!("\n{name}: {value}")
    } else {
        String::new()
    }
}

fn sanitized_request_string(request: &ConnectionRequest) -> String {
    let addresses = request
        .addresses
        .iter()
        .map(|address| format!("{}:{}", address.host, address.port))
        .collect::<Vec<_>>()
        .join(", ");
    let tls_mode = request
        .tls_mode
        .map(|tls_mode| {
            format!(
                "\nTLS mode: {}",
                match tls_mode {
                    TlsMode::NoTls => "No TLS",
                    TlsMode::SecureTls => "Secure",
                    TlsMode::InsecureTls => "Insecure",
                }
            )
        })
        .unwrap_or_default();
    let cluster_mode = if request.cluster_mode_enabled {
        "\nCluster mode"
    } else {
        "\nStandalone mode"
    };
    let request_timeout = format_optional_value("Request timeout", request.request_timeout);
    let database_id = format!("\ndatabase ID: {}", request.database_id);
    let rfr_strategy = request
        .read_from
        .map(|rfr| {
            format!(
                "\nRead from Replica mode: {}",
                match rfr {
                    ReadFrom::Primary => "Only primary",
                    ReadFrom::PreferReplica => "Prefer replica",
                }
            )
        })
        .unwrap_or_default();
    let connection_retry_strategy = request.connection_retry_strategy.as_ref().map(|strategy|
            format!("\nreconnect backoff strategy: number of increasing duration retries: {}, base: {}, factor: {}",
        strategy.number_of_retries, strategy.exponent_base, strategy.factor)).unwrap_or_default();
    let protocol = request
        .protocol
        .map(|protocol| format!("\nProtocol: {protocol:?}"))
        .unwrap_or_default();
    let client_name = request
        .client_name
        .as_ref()
        .map(|client_name| format!("\nClient name: {client_name}"))
        .unwrap_or_default();
    let periodic_checks = if request.cluster_mode_enabled {
        match request.periodic_checks {
            Some(PeriodicCheck::Disabled) => "\nPeriodic Checks: Disabled".to_string(),
            Some(PeriodicCheck::Enabled) => format!(
                "\nPeriodic Checks: Enabled with default interval of {:?}",
                DEFAULT_PERIODIC_CHECKS_INTERVAL
            ),
            Some(PeriodicCheck::ManualInterval(interval)) => format!(
                "\nPeriodic Checks: Enabled with manual interval of {:?}s",
                interval.as_secs()
            ),
            None => String::new(),
        }
    } else {
        String::new()
    };

    format!(
        "\nAddresses: {addresses}{tls_mode}{cluster_mode}{request_timeout}{rfr_strategy}{connection_retry_strategy}{database_id}{protocol}{client_name}{periodic_checks}",
    )
}

impl Client {
    pub async fn new(request: ConnectionRequest) -> Result<Self, ConnectionError> {
        const DEFAULT_CLIENT_CREATION_TIMEOUT: Duration = Duration::from_secs(10);

        log_info(
            "Connection configuration",
            sanitized_request_string(&request),
        );
        let request_timeout = to_duration(request.request_timeout, DEFAULT_RESPONSE_TIMEOUT);
        tokio::time::timeout(DEFAULT_CLIENT_CREATION_TIMEOUT, async move {
            let internal_client = if request.cluster_mode_enabled {
                let client = create_cluster_client(request)
                    .await
                    .map_err(ConnectionError::Cluster)?;
                ClientWrapper::Cluster { client }
            } else {
                ClientWrapper::Standalone(
                    StandaloneClient::create_client(request)
                        .await
                        .map_err(ConnectionError::Standalone)?,
                )
            };

            Ok(Self {
                internal_client,
                request_timeout,
            })
        })
        .await
        .map_err(|_| ConnectionError::Timeout)
        .and_then(|res| res)
    }
}

pub trait GlideClientForTests {
    fn send_command<'a>(
        &'a mut self,
        cmd: &'a Cmd,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, redis::Value>;
}

impl GlideClientForTests for Client {
    fn send_command<'a>(
        &'a mut self,
        cmd: &'a Cmd,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, redis::Value> {
        self.send_command(cmd, routing)
    }
}

impl GlideClientForTests for StandaloneClient {
    fn send_command<'a>(
        &'a mut self,
        cmd: &'a Cmd,
        _routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, redis::Value> {
        self.send_command(cmd).boxed()
    }
}
