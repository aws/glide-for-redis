/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use super::get_redis_connection_info;
use super::reconnecting_connection::ReconnectingConnection;
use super::{ConnectionRequest, NodeAddress, TlsMode};
use crate::retry_strategies::RetryStrategy;
use futures::{future, stream, StreamExt};
#[cfg(standalone_heartbeat)]
use logger_core::log_debug;
use logger_core::log_warn;
use rand::Rng;
use redis::cluster_routing::{self, is_readonly_cmd, ResponsePolicy, Routable, RoutingInfo};
use redis::{PushInfo, RedisError, RedisResult, Value};
use std::sync::atomic::AtomicUsize;
use std::sync::Arc;
use tokio::sync::mpsc;
#[cfg(standalone_heartbeat)]
use tokio::task;

#[derive(Debug)]
enum ReadFrom {
    Primary,
    PreferReplica {
        latest_read_replica_index: Arc<std::sync::atomic::AtomicUsize>,
    },
}

#[derive(Debug)]
struct DropWrapper {
    /// Connection to the primary node in the client.
    primary_index: usize,
    nodes: Vec<ReconnectingConnection>,
    read_from: ReadFrom,
}

impl Drop for DropWrapper {
    fn drop(&mut self) {
        for node in self.nodes.iter() {
            node.mark_as_dropped();
        }
    }
}

#[derive(Clone, Debug)]
pub struct StandaloneClient {
    inner: Arc<DropWrapper>,
}

pub enum StandaloneClientConnectionError {
    NoAddressesProvided,
    FailedConnection(Vec<(Option<String>, RedisError)>),
    PrimaryConflictFound(String),
}

impl std::fmt::Debug for StandaloneClientConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            StandaloneClientConnectionError::NoAddressesProvided => {
                write!(f, "No addresses provided")
            }
            StandaloneClientConnectionError::FailedConnection(errs) => {
                match errs.len() {
                    0 => {
                        writeln!(f, "Failed without explicit error")?;
                    }
                    1 => {
                        let (ref address, ref error) = errs[0];
                        match address {
                            Some(address) => {
                                writeln!(f, "Received error for address `{address}`: {error}")?
                            }
                            None => writeln!(f, "Received error: {error}")?,
                        }
                    }
                    _ => {
                        writeln!(f, "Received errors:")?;
                        for (address, error) in errs {
                            match address {
                                Some(address) => writeln!(f, "{address}: {error}")?,
                                None => writeln!(f, "{error}")?,
                            }
                        }
                    }
                };
                Ok(())
            }
            StandaloneClientConnectionError::PrimaryConflictFound(found_primaries) => {
                writeln!(
                    f,
                    "Primary conflict. More than one primary found in a Standalone setup: {found_primaries}"
                )
            }
        }
    }
}

impl StandaloneClient {
    pub async fn create_client(
        connection_request: ConnectionRequest,
        push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
    ) -> Result<Self, StandaloneClientConnectionError> {
        if connection_request.addresses.is_empty() {
            return Err(StandaloneClientConnectionError::NoAddressesProvided);
        }
        let mut redis_connection_info = get_redis_connection_info(&connection_request);
        let pubsub_connection_info = redis_connection_info.clone();
        redis_connection_info.pubsub_subscriptions = None;
        let retry_strategy = RetryStrategy::new(connection_request.connection_retry_strategy);

        let tls_mode = connection_request.tls_mode;
        let node_count = connection_request.addresses.len();
        // randomize pubsub nodes, maybe a batter option is to always use the primary
        let pubsub_node_index = rand::thread_rng().gen_range(0..node_count);
        let pubsub_addr = &connection_request.addresses[pubsub_node_index];
        let mut stream = stream::iter(connection_request.addresses.iter())
            .map(|address| async {
                get_connection_and_replication_info(
                    address,
                    &retry_strategy,
                    if address.to_string() != pubsub_addr.to_string() {
                        &redis_connection_info
                    } else {
                        &pubsub_connection_info
                    },
                    tls_mode.unwrap_or(TlsMode::NoTls),
                    &push_sender,
                )
                .await
                .map_err(|err| (format!("{}:{}", address.host, address.port), err))
            })
            .buffer_unordered(node_count);

        let mut nodes = Vec::with_capacity(node_count);
        let mut addresses_and_errors = Vec::with_capacity(node_count);
        let mut primary_index = None;
        while let Some(result) = stream.next().await {
            match result {
                Ok((connection, replication_status)) => {
                    nodes.push(connection);
                    if redis::from_owned_redis_value::<String>(replication_status)
                        .is_ok_and(|val| val.contains("role:master"))
                    {
                        if let Some(primary_index) = primary_index {
                            // More than one primary found
                            return Err(StandaloneClientConnectionError::PrimaryConflictFound(
                                format!(
                                    "Primary nodes: {:?}, {:?}",
                                    nodes.pop(),
                                    nodes.get(primary_index)
                                ),
                            ));
                        }
                        primary_index = Some(nodes.len().saturating_sub(1));
                    }
                }
                Err((address, (connection, err))) => {
                    nodes.push(connection);
                    addresses_and_errors.push((Some(address), err));
                }
            }
        }

        let Some(primary_index) = primary_index else {
            if addresses_and_errors.is_empty() {
                addresses_and_errors.insert(
                    0,
                    (
                        None,
                        RedisError::from((redis::ErrorKind::ClientError, "No primary node found")),
                    ),
                )
            };
            return Err(StandaloneClientConnectionError::FailedConnection(
                addresses_and_errors,
            ));
        };
        if !addresses_and_errors.is_empty() {
            log_warn(
                "client creation",
                format!(
                    "Failed to connect to {addresses_and_errors:?}, will attempt to reconnect."
                ),
            );
        }
        let read_from = get_read_from(connection_request.read_from);

        #[cfg(standalone_heartbeat)]
        for node in nodes.iter() {
            Self::start_heartbeat(node.clone());
        }

        Ok(Self {
            inner: Arc::new(DropWrapper {
                primary_index,
                nodes,
                read_from,
            }),
        })
    }

    fn get_primary_connection(&self) -> &ReconnectingConnection {
        self.inner.nodes.get(self.inner.primary_index).unwrap()
    }

    fn round_robin_read_from_replica(
        &self,
        latest_read_replica_index: &Arc<AtomicUsize>,
    ) -> &ReconnectingConnection {
        let initial_index = latest_read_replica_index.load(std::sync::atomic::Ordering::Relaxed);
        let mut check_count = 0;
        loop {
            check_count += 1;

            // Looped through all replicas, no connected replica was found.
            if check_count > self.inner.nodes.len() {
                return self.get_primary_connection();
            }
            let index = (initial_index + check_count) % self.inner.nodes.len();
            if index == self.inner.primary_index {
                continue;
            }
            let Some(connection) = self.inner.nodes.get(index) else {
                continue;
            };
            if connection.is_connected() {
                let _ = latest_read_replica_index.compare_exchange_weak(
                    initial_index,
                    index,
                    std::sync::atomic::Ordering::Relaxed,
                    std::sync::atomic::Ordering::Relaxed,
                );
                return connection;
            }
        }
    }

    fn get_connection(&self, readonly: bool) -> &ReconnectingConnection {
        if self.inner.nodes.len() == 1 || !readonly {
            return self.get_primary_connection();
        }

        match &self.inner.read_from {
            ReadFrom::Primary => self.get_primary_connection(),
            ReadFrom::PreferReplica {
                latest_read_replica_index,
            } => self.round_robin_read_from_replica(latest_read_replica_index),
        }
    }

    async fn send_request(
        cmd: &redis::Cmd,
        reconnecting_connection: &ReconnectingConnection,
    ) -> RedisResult<Value> {
        let mut connection = reconnecting_connection.get_connection().await?;
        let result = connection.send_packed_command(cmd).await;
        match result {
            Err(err) if err.is_unrecoverable_error() => {
                log_warn("send request", format!("received disconnect error `{err}`"));
                reconnecting_connection.reconnect();
                Err(err)
            }
            _ => result,
        }
    }

    async fn send_request_to_all_nodes(
        &mut self,
        cmd: &redis::Cmd,
        response_policy: Option<ResponsePolicy>,
    ) -> RedisResult<Value> {
        let requests = self
            .inner
            .nodes
            .iter()
            .map(|node| Self::send_request(cmd, node));

        // TODO - once Value::Error will be merged, these will need to be updated to handle this new value.
        match response_policy {
            Some(ResponsePolicy::AllSucceeded) => {
                future::try_join_all(requests)
                    .await
                    .map(|mut results| results.pop().unwrap()) // unwrap is safe, since at least one function succeeded
            }
            Some(ResponsePolicy::OneSucceeded) => future::select_ok(requests.map(Box::pin))
                .await
                .map(|(result, _)| result),
            Some(ResponsePolicy::FirstSucceededNonEmptyOrAllEmpty) => {
                future::select_ok(requests.map(|request| {
                    Box::pin(async move {
                        let result = request.await?;
                        match result {
                            Value::Nil => {
                                Err((redis::ErrorKind::ResponseError, "no value found").into())
                            }
                            _ => Ok(result),
                        }
                    })
                }))
                .await
                .map(|(result, _)| result)
            }
            Some(ResponsePolicy::Aggregate(op)) => future::try_join_all(requests)
                .await
                .and_then(|results| cluster_routing::aggregate(results, op)),
            Some(ResponsePolicy::AggregateLogical(op)) => future::try_join_all(requests)
                .await
                .and_then(|results| cluster_routing::logical_aggregate(results, op)),
            Some(ResponsePolicy::CombineArrays) => future::try_join_all(requests)
                .await
                .and_then(cluster_routing::combine_array_results),
            Some(ResponsePolicy::Special) | None => {
                // This is our assumption - if there's no coherent way to aggregate the responses, we just collect them in an array, and pass it to the user.
                // TODO - once Value::Error is merged, we can use join_all and report separate errors and also pass successes.
                future::try_join_all(requests).await.map(Value::Array)
            }
        }
    }

    async fn send_request_to_single_node(
        &mut self,
        cmd: &redis::Cmd,
        readonly: bool,
    ) -> RedisResult<Value> {
        let reconnecting_connection = self.get_connection(readonly);
        Self::send_request(cmd, reconnecting_connection).await
    }

    pub async fn send_command(&mut self, cmd: &redis::Cmd) -> RedisResult<Value> {
        let Some(cmd_bytes) = Routable::command(cmd) else {
            return self.send_request_to_single_node(cmd, false).await;
        };

        if RoutingInfo::is_all_nodes(cmd_bytes.as_slice()) {
            let response_policy = ResponsePolicy::for_command(cmd_bytes.as_slice());
            return self.send_request_to_all_nodes(cmd, response_policy).await;
        }
        self.send_request_to_single_node(cmd, is_readonly_cmd(cmd_bytes.as_slice()))
            .await
    }

    pub async fn send_pipeline(
        &mut self,
        pipeline: &redis::Pipeline,
        offset: usize,
        count: usize,
    ) -> RedisResult<Vec<Value>> {
        let reconnecting_connection = self.get_primary_connection();
        let mut connection = reconnecting_connection.get_connection().await?;
        let result = connection
            .send_packed_commands(pipeline, offset, count)
            .await;
        match result {
            Err(err) if err.is_unrecoverable_error() => {
                log_warn(
                    "pipeline request",
                    format!("received disconnect error `{err}`"),
                );
                reconnecting_connection.reconnect();
                Err(err)
            }
            _ => result,
        }
    }

    #[cfg(standalone_heartbeat)]
    fn start_heartbeat(reconnecting_connection: ReconnectingConnection) {
        task::spawn(async move {
            loop {
                tokio::time::sleep(super::HEARTBEAT_SLEEP_DURATION).await;
                if reconnecting_connection.is_dropped() {
                    log_debug(
                        "StandaloneClient",
                        "heartbeat stopped after connection was dropped",
                    );
                    // Client was dropped, heartbeat can stop.
                    return;
                }

                let Some(mut connection) = reconnecting_connection.try_get_connection().await
                else {
                    log_debug(
                        "StandaloneClient",
                        "heartbeat stopped while connection is reconnecting",
                    );
                    // Client is reconnecting..
                    continue;
                };
                log_debug("StandaloneClient", "performing heartbeat");
                if connection
                    .send_packed_command(&redis::cmd("PING"))
                    .await
                    .is_err_and(|err| err.is_connection_dropped() || err.is_connection_refusal())
                {
                    log_debug("StandaloneClient", "heartbeat triggered reconnect");
                    reconnecting_connection.reconnect();
                }
            }
        });
    }
}

async fn get_connection_and_replication_info(
    address: &NodeAddress,
    retry_strategy: &RetryStrategy,
    connection_info: &redis::RedisConnectionInfo,
    tls_mode: TlsMode,
    push_sender: &Option<mpsc::UnboundedSender<PushInfo>>,
) -> Result<(ReconnectingConnection, Value), (ReconnectingConnection, RedisError)> {
    let result = ReconnectingConnection::new(
        address,
        retry_strategy.clone(),
        connection_info.clone(),
        tls_mode,
        push_sender.clone(),
    )
    .await;
    let reconnecting_connection = match result {
        Ok(reconnecting_connection) => reconnecting_connection,
        Err(tuple) => return Err(tuple),
    };

    let mut multiplexed_connection = match reconnecting_connection.get_connection().await {
        Ok(multiplexed_connection) => multiplexed_connection,
        Err(err) => {
            reconnecting_connection.reconnect();
            return Err((reconnecting_connection, err));
        }
    };

    match multiplexed_connection
        .send_packed_command(redis::cmd("INFO").arg("REPLICATION"))
        .await
    {
        Ok(replication_status) => Ok((reconnecting_connection, replication_status)),
        Err(err) => Err((reconnecting_connection, err)),
    }
}

fn get_read_from(read_from: Option<super::ReadFrom>) -> ReadFrom {
    match read_from {
        Some(super::ReadFrom::Primary) => ReadFrom::Primary,
        Some(super::ReadFrom::PreferReplica) => ReadFrom::PreferReplica {
            latest_read_replica_index: Default::default(),
        },
        None => ReadFrom::Primary,
    }
}
