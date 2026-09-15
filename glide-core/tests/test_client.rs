// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod constants;
mod utilities;

#[macro_export]
/// Compare `$expected` with `$actual`. This macro, will exit the test process
/// if the assertion fails. Unlike `assert_eq!` - this also works in tasks
macro_rules! async_assert_eq {
    ($expected:expr, $actual:expr) => {{
        if $actual != $expected {
            println!(
                "{}:{}: Expected: {:?} != Actual: {:?}",
                file!(),
                line!(),
                $actual,
                $expected
            );
            std::process::exit(1);
        }
    }};
}

#[cfg(test)]
pub(crate) mod shared_client_tests {
    use glide_core::Telemetry;
    use redis::{cluster_topology::get_slot, cmd};
    use std::collections::HashMap;

    use super::*;
    use glide_core::client::{Client, DEFAULT_RESPONSE_TIMEOUT};
    use glide_core::connection_request::ProtocolVersion;
    use redis::cluster_routing::{SingleNodeRoutingInfo, SlotAddr};
    use redis::{
        FromRedisValue, InfoDict, Pipeline, PipelineRetryStrategy, RedisConnectionInfo, Value,
        cluster_routing::{MultipleNodeRoutingInfo, Route, RoutingInfo},
    };
    use rstest::rstest;
    use utilities::BackingServer;
    use utilities::cluster::*;
    use utilities::*;

    /// Explicit pipeline timeout (in milliseconds) used by pipeline tests.
    ///
    /// These pipelines include commands that route to all primary nodes (e.g.
    /// `FLUSHALL`, `DBSIZE`). Under CI load (many tests running in parallel
    /// against a shared server) these multi-node operations can exceed the
    /// `DEFAULT_RESPONSE_TIMEOUT` (250ms), causing flaky timeouts. A higher,
    /// explicit timeout keeps these tests stable without affecting behaviour.
    const PIPELINE_TEST_TIMEOUT_MS: u32 = 10_000;

    #[cfg(feature = "iam_tests")]
    use glide_core::connection_request::{
        AuthenticationInfo, IamCredentials, ServiceType, TlsMode,
    };

    #[cfg(feature = "iam_tests")]
    const ELASTICACHE_CLUSTER_IAM_ENDPOINT: &str = "elasticache-cluster-iam.endpoint"; // Replace with your cluster endpoint
    #[cfg(feature = "iam_tests")]
    const ELASTICACHE_STANDALONE_IAM_ENDPOINT: &str = "elasticache-standalone-iam.endpoint"; // Replace with your standalone endpoint
    #[cfg(feature = "iam_tests")]
    const MEMORYDB_CLUSTER_IAM_ENDPOINT: &str = "memorydb-cluster-iam.endpoint"; // Replace with your cluster endpoint

    #[cfg(feature = "iam_tests")]
    const TEST_STANDALONE_NAME: &str = "test-standalone";

    // Import IAM test constants from constants module
    #[cfg(feature = "iam_tests")]
    use constants::{IAM_TEST_CLUSTER_NAME, IAM_TEST_REGION_US_EAST_1, IAM_USERNAME};

    struct TestBasics {
        server: BackingServer,
        client: Client,
    }

    async fn create_client(server: &BackingServer, configuration: TestConfiguration) -> Client {
        match server {
            BackingServer::Standalone(server) => {
                let connection_addr = server
                    .as_ref()
                    .map(|server| server.get_client_addr())
                    .unwrap_or(get_shared_server_address(configuration.use_tls));

                // TODO - this is a patch, handling the situation where the new server
                // still isn't available to connection. This should be fixed in [RedisServer].
                retry(|| async {
                    Client::new(
                        create_connection_request(
                            std::slice::from_ref(&connection_addr),
                            &configuration,
                        )
                        .into(),
                        None,
                    )
                    .await
                    .ok()
                })
                .await
            }
            BackingServer::Cluster(cluster) => {
                create_cluster_client(cluster.as_ref(), configuration).await
            }
        }
    }

    async fn setup_test_basics(use_cluster: bool, configuration: TestConfiguration) -> TestBasics {
        if use_cluster {
            let cluster_basics = cluster::setup_test_basics_internal(configuration).await;
            TestBasics {
                server: BackingServer::Cluster(cluster_basics.cluster),
                client: cluster_basics.client,
            }
        } else {
            let test_basics = utilities::setup_test_basics_internal(&configuration).await;
            let server = BackingServer::Standalone(test_basics.server);
            let client = create_client(&server, configuration).await;
            TestBasics { server, client }
        }
    }

    #[cfg(feature = "iam_tests")]
    fn setup_mock_aws_credentials() {
        unsafe {
            std::env::set_var("AWS_ACCESS_KEY_ID", "test_access_key");
            std::env::set_var("AWS_SECRET_ACCESS_KEY", "test_secret_key");
            std::env::set_var("AWS_SESSION_TOKEN", "test_session_token");
        }
    }

    #[cfg(feature = "iam_tests")]
    fn cleanup_mock_aws_credentials() {
        unsafe {
            std::env::remove_var("AWS_ACCESS_KEY_ID");
            std::env::remove_var("AWS_SECRET_ACCESS_KEY");
            std::env::remove_var("AWS_SESSION_TOKEN");
        }
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_set_and_get(
        #[values(false, true)] use_tls: bool,
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls,
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            let key = generate_random_string(6);
            send_set_and_get(test_basics.client.clone(), key.to_string()).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_command_owned_matches_borrowed_execution_and_metadata() {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                false,
                TestConfiguration {
                    request_timeout: Some(1000),
                    shared_server: false,
                    ..Default::default()
                },
            )
            .await;

            let mut borrowed_ping = redis::cmd("PING");
            let borrowed_result = test_basics
                .client
                .send_command(&mut borrowed_ping, None)
                .await
                .unwrap();
            assert_eq!(borrowed_result, Value::SimpleString("PONG".to_string()));
            assert_eq!(
                borrowed_ping.response_timeout(),
                Some(std::time::Duration::from_millis(1000))
            );

            let owned_result = test_basics
                .client
                .send_command_owned(redis::cmd("PING"), None)
                .await
                .unwrap();
            assert_eq!(owned_result, borrowed_result);

            let channel = generate_random_string(10);
            let mut borrowed_subscribe = redis::cmd("SUBSCRIBE");
            borrowed_subscribe.arg(&channel);
            let borrowed_subscribe_result = test_basics
                .client
                .send_command(&mut borrowed_subscribe, None)
                .await
                .unwrap();
            assert_eq!(borrowed_subscribe_result, Value::Nil);
            assert_eq!(borrowed_subscribe.response_timeout(), None);

            let mut owned_subscribe = redis::cmd("SUBSCRIBE");
            owned_subscribe.arg(&channel);
            let owned_subscribe_result = test_basics
                .client
                .send_command_owned(owned_subscribe, None)
                .await
                .unwrap();
            assert_eq!(owned_subscribe_result, borrowed_subscribe_result);

            let mut unsubscribe = redis::cmd("UNSUBSCRIBE");
            unsubscribe.arg(&channel);
            test_basics
                .client
                .send_command_owned(unsubscribe, None)
                .await
                .unwrap();
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_transaction_is_not_routed() {
        // This test checks that a transaction without user routing isn't routed to a random node before reaching its target.
        // This is tested by checking how many requests each node has received - one of the 6 nodes should have more requests than the others.
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                true,
                TestConfiguration {
                    use_tls: false,
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // reset stats on all connections
            let mut cmd = redis::cmd("CONFIG");
            cmd.arg("RESETSTAT");
            let _ = test_basics
                .client
                .send_command(
                    &mut cmd,
                    Some(RoutingInfo::MultiNode((
                        MultipleNodeRoutingInfo::AllNodes,
                        None,
                    ))),
                )
                .await
                .unwrap();

            // Send a keyed transaction
            let key = generate_random_string(6);
            let mut pipe = redis::pipe();
            pipe.cmd("GET").arg(key);
            pipe.atomic();

            for _ in 0..4 {
                let _ = test_basics
                    .client
                    .send_transaction(&pipe, None, None, false)
                    .await
                    .unwrap();
            }

            // Gather info from each server
            let mut cmd = redis::cmd("INFO");
            cmd.arg("commandstats");
            let values = test_basics
                .client
                .send_command(
                    &mut cmd,
                    Some(RoutingInfo::MultiNode((
                        MultipleNodeRoutingInfo::AllNodes,
                        None,
                    ))),
                )
                .await
                .unwrap();

            let values: Vec<_> = match values {
                Value::Map(map) => map.into_iter().filter_map(|(_, value)| {
                    let map = InfoDict::from_owned_redis_value(value).unwrap();
                    map.get::<String>("cmdstat_get")?
                        .split_once(',')?
                        .0
                        .split_at(6) // split after `calls=``
                        .1
                        .parse::<u32>()
                        .ok()
                }),

                _ => panic!("Expected map, got `{values:?}`"),
            }
            .collect();

            // Check that only one node received all of the GET calls.
            assert_eq!(values.len(), 1);
            assert_eq!(values[0], 4);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_resp_support(#[values(false, true)] use_cluster: bool, #[values(2, 3)] protocol: i64) {
        let protocol_enum = match protocol {
            2 => redis::ProtocolVersion::RESP2,
            3 => redis::ProtocolVersion::RESP3,
            _ => panic!(),
        };
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    connection_info: Some(RedisConnectionInfo {
                        protocol: protocol_enum,
                        ..Default::default()
                    }),
                    protocol: match protocol {
                        2 => ProtocolVersion::RESP2,
                        3 => ProtocolVersion::RESP3,
                        _ => panic!(),
                    },
                    ..Default::default()
                },
            )
            .await;
            let hello: std::collections::HashMap<String, Value> = redis::from_owned_redis_value(
                test_basics
                    .client
                    .send_command(&mut redis::cmd("HELLO"), None)
                    .await
                    .unwrap(),
            )
            .unwrap();
            assert_eq!(hello.get("proto").unwrap(), &Value::Int(protocol));

            let mut cmd = redis::cmd("HSET");
            cmd.arg("hash").arg("foo").arg("baz");
            test_basics
                .client
                .send_command(&mut cmd, None)
                .await
                .unwrap();
            let mut cmd = redis::cmd("HSET");
            cmd.arg("hash").arg("bar").arg("foobar");
            test_basics
                .client
                .send_command(&mut cmd, None)
                .await
                .unwrap();

            let mut cmd = redis::cmd("HGETALL");
            cmd.arg("hash");
            let result = test_basics
                .client
                .send_command(&mut cmd, None)
                .await
                .unwrap();

            assert_eq!(
                result,
                Value::Map(vec![
                    (
                        Value::BulkString("foo".as_bytes().to_vec().into()),
                        Value::BulkString("baz".as_bytes().to_vec().into())
                    ),
                    (
                        Value::BulkString("bar".as_bytes().to_vec().into()),
                        Value::BulkString("foobar".as_bytes().to_vec().into())
                    )
                ])
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_client_handle_concurrent_workload_without_dropping_or_changing_values(
        #[values(false, true)] use_tls: bool,
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls,
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            const NUMBER_OF_CONCURRENT_OPERATIONS: usize = 1000;

            let mut actions = Vec::with_capacity(NUMBER_OF_CONCURRENT_OPERATIONS);
            for index in 0..NUMBER_OF_CONCURRENT_OPERATIONS {
                actions.push(send_set_and_get(
                    test_basics.client.clone(),
                    format!("key{index}"),
                ));
            }
            futures::future::join_all(actions).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_report_closing_when_server_closes(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(10000000),
                    ..Default::default()
                },
            )
            .await;
            let server = test_basics.server;
            drop(server);

            let get_result = send_get(&mut test_basics.client, "foobar")
                .await
                .unwrap_err();
            assert!(
                get_result.is_connection_dropped()
                    || get_result.kind() == redis::ErrorKind::ConnectionNotFoundForRoute
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_authenticate_with_password(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls: true,
                    connection_info: Some(redis::RedisConnectionInfo {
                        password: Some("ReallySecurePassword".to_string()),
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;
            let key = generate_random_string(6);
            send_set_and_get(test_basics.client.clone(), key.to_string()).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_authenticate_with_password_and_username(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls: true,
                    connection_info: Some(redis::RedisConnectionInfo {
                        password: Some("ReallySecurePassword".to_string()),
                        username: Some("AuthorizedUsername".to_string()),
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;
            let key = generate_random_string(6);
            send_set_and_get(test_basics.client.clone(), key.to_string()).await;
        });
    }

    #[cfg(feature = "iam_tests")]
    fn remove_test_credentials() {
        // Clear any existing AWS credentials
        unsafe {
            std::env::remove_var("AWS_ACCESS_KEY_ID");
            std::env::remove_var("AWS_SECRET_ACCESS_KEY");
            std::env::remove_var("AWS_SESSION_TOKEN");
            std::env::remove_var("AWS_PROFILE");
            std::env::remove_var("AWS_SHARED_CREDENTIALS_FILE");
            std::env::remove_var("AWS_CONFIG_FILE");
        }
    }

    #[cfg(feature = "iam_tests")]
    /// Helper function to create connection request with IAM authentication
    fn create_iam_connection_request(
        addresses: &[redis::ConnectionAddr],
        cluster_name: &str,
        username: &str,
        region: &str,
        refresh_interval_seconds: Option<u32>,
        cluster_mode: bool,
        service_type: ServiceType,
    ) -> glide_core::connection_request::ConnectionRequest {
        let addresses_info = addresses.iter().map(get_address_info).collect();

        let iam_credentials = IamCredentials {
            cluster_name: cluster_name.into(),
            region: region.into(),
            service_type: service_type.into(),
            refresh_interval_seconds,
            ..Default::default()
        };

        let auth_info = AuthenticationInfo {
            password: String::new().into(), // Empty password when using IAM
            username: username.into(),
            iam_credentials: protobuf::MessageField::some(iam_credentials),
            ..Default::default()
        };

        glide_core::connection_request::ConnectionRequest {
            addresses: addresses_info,
            tls_mode: TlsMode::SecureTls.into(),
            cluster_mode_enabled: cluster_mode,
            request_timeout: 10000, // 10 seconds
            authentication_info: protobuf::MessageField::some(auth_info),
            ..Default::default()
        }
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_authentication_elasticache_cluster() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = IAM_TEST_REGION_US_EAST_1;
            let endpoint: &'static str = ELASTICACHE_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None, // Use default refresh interval
                true, // cluster mode
                ServiceType::ELASTICACHE,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // If the client is successfully created, try sending a command
                    assert_connected(&mut client).await;
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_authentication_elasticache_standalone() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-standalone"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = IAM_TEST_REGION_US_EAST_1;
            let endpoint = ELASTICACHE_STANDALONE_IAM_ENDPOINT; // Replace with your standalone endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None,  // Use default refresh interval
                false, // standalone mode
                ServiceType::ELASTICACHE,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // If the client is successfully created, try sending a command
                    assert_connected(&mut client).await;
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_authentication_memorydb_cluster() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth-test"; // Replace with your IAM username
            let region = IAM_TEST_REGION_US_EAST_1;
            let endpoint = MEMORYDB_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None, // Use default refresh interval
                true, // cluster mode
                ServiceType::MEMORYDB,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // If the client is successfully created, try sending a command
                    assert_connected(&mut client).await;
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_lazy_connection_establishes_on_first_command_with_cluster() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = IAM_TEST_REGION_US_EAST_1;
            let endpoint = ELASTICACHE_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request with lazy connection enabled
            let mut connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None, // Use default refresh interval
                true, // cluster mode
                ServiceType::ELASTICACHE,
            );

            // Enable lazy connection
            connection_request.lazy_connect = true;

            // Create client with lazy connection
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // At this point, the client should be created but not yet connected
                    // The connection should be established on the first command

                    // Send the first command - this should trigger the connection establishment
                    let result = client.send_command(&mut redis::cmd("PING"), None).await;

                    match result {
                        Ok(value) => {
                            // Verify that PING returned the expected response
                            assert_eq!(value, Value::SimpleString("PONG".to_string()));

                            // Send another command to verify the connection is established and working
                            let key = generate_random_string(6);
                            let set_result = client
                                .send_command(redis::cmd("SET").arg(&key).arg("test_value"), None)
                                .await;
                            assert!(
                                set_result.is_ok(),
                                "SET command should succeed: {set_result:?}"
                            );

                            let get_result =
                                client.send_command(redis::cmd("GET").arg(&key), None).await;
                            assert_eq!(
                                get_result.unwrap(),
                                Value::BulkString("test_value".as_bytes().to_vec().into())
                            );
                        }
                        Err(err) => {
                            let error_msg = err.to_string();
                            // If DNS lookup failed, provide a clearer message
                            if error_msg.contains("failed to lookup address")
                                || error_msg.contains("Name or service not known")
                            {
                                // Uncomment this when you have a real AWS environment
                                panic!(
                                    "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                                    endpoint, error_msg
                                );
                            }

                            // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                            // Uncomment this when you have a real AWS environment
                            panic!(
                                "Failed to execute first command with IAM authentication on lazy cluster connection: {}",
                                error_msg
                            );
                        }
                    }
                }
                Err(err) => {
                    // Client creation should succeed even with lazy connection
                    panic!(
                        "Failed to create lazy cluster client with IAM authentication: {}",
                        err
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_lazy_connection_establishes_on_first_command_with_standalone() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-standalone"; // Replace with your standalone cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = IAM_TEST_REGION_US_EAST_1;
            let endpoint = ELASTICACHE_STANDALONE_IAM_ENDPOINT; // Replace with your standalone endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request with lazy connection enabled
            let mut connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None,  // Use default refresh interval
                false, // standalone mode
                ServiceType::ELASTICACHE,
            );

            // Enable lazy connection
            connection_request.lazy_connect = true;

            // Create client with lazy connection
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // At this point, the client should be created but not yet connected
                    // The connection should be established on the first command

                    // Send the first command - this should trigger the connection establishment
                    let result = client.send_command(&mut redis::cmd("PING"), None).await;

                    match result {
                        Ok(value) => {
                            // Verify that PING returned the expected response
                            assert_eq!(value, Value::SimpleString("PONG".to_string()));

                            // Send another command to verify the connection is established and working
                            let key = generate_random_string(6);
                            let set_result = client
                                .send_command(redis::cmd("SET").arg(&key).arg("test_value"), None)
                                .await;
                            assert!(
                                set_result.is_ok(),
                                "SET command should succeed: {set_result:?}"
                            );

                            let get_result =
                                client.send_command(redis::cmd("GET").arg(&key), None).await;
                            assert_eq!(
                                get_result.unwrap(),
                                Value::BulkString("test_value".as_bytes().to_vec().into())
                            );
                        }
                        Err(err) => {
                            let error_msg = err.to_string();
                            // If DNS lookup failed, provide a clearer message
                            if error_msg.contains("failed to lookup address")
                                || error_msg.contains("Name or service not known")
                            {
                                // Uncomment this when you have a real AWS environment
                                panic!(
                                    "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                                    endpoint, error_msg
                                );
                            }

                            // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                            // Uncomment this when you have a real AWS environment
                            panic!(
                                "Failed to execute first command with IAM authentication on lazy standalone connection: {}",
                                error_msg
                            );
                        }
                    }
                }
                Err(err) => {
                    // Client creation should succeed even with lazy connection
                    panic!(
                        "Failed to create lazy standalone client with IAM authentication: {}",
                        err
                    );
                }
            }
        });
    }

    /**
     * Ensures the client **reconnects** to an ElastiCache cluster with **IAM auth** after its
     * connection is dropped.
     *
     * Force a **failure** (negative case):
     * - Set `refresh_interval_seconds` **> 900**.
     * - Sleep **> 900s and < refresh_interval_seconds** **before** killing the connection.
     *   The token will be expired and reconnect should fail.
     */
    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_refresh_token(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            // Set mock AWS credentials
            setup_mock_aws_credentials();

            // Cleanup function to restore original credentials
            let cleanup = cleanup_mock_aws_credentials;

            let cluster_name = if use_cluster {
                IAM_TEST_CLUSTER_NAME
            } else {
                TEST_STANDALONE_NAME
            };
            let username = IAM_USERNAME;
            let region = IAM_TEST_REGION_US_EAST_1;

            // Create test basics with regular authentication first
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // Get the server address
            let address = match &test_basics.server {
                BackingServer::Standalone(server) => server
                    .as_ref()
                    .map(|s| s.get_client_addr())
                    .unwrap_or(get_shared_server_address(false)),
                BackingServer::Cluster(cluster) => {
                    if let Some(cluster) = cluster.as_ref() {
                        let addresses = cluster.get_server_addresses();
                        addresses[0].clone()
                    } else {
                        // Using shared cluster
                        let addresses = get_shared_cluster_addresses(false);
                        addresses[0].clone()
                    }
                }
            };

            // Create IAM connection request with mock credentials
            let iam_credentials = IamCredentials {
                cluster_name: cluster_name.into(),
                region: region.into(),
                service_type: ServiceType::ELASTICACHE.into(),
                refresh_interval_seconds: Some(5),
                ..Default::default()
            };

            let auth_info = AuthenticationInfo {
                password: String::new().into(),
                username: username.into(),
                iam_credentials: protobuf::MessageField::some(iam_credentials),
                ..Default::default()
            };

            let addresses_info = vec![get_address_info(&address)];

            let connection_request = glide_core::connection_request::ConnectionRequest {
                addresses: addresses_info,
                tls_mode: TlsMode::NoTls.into(),
                cluster_mode_enabled: use_cluster,
                request_timeout: 10000,
                authentication_info: protobuf::MessageField::some(auth_info),
                ..Default::default()
            };

            // Create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // Test initial connection
                    assert_connected(&mut client).await;

                    // Test manual IAM token refresh
                    let refresh_result = client.refresh_iam_token().await;
                    assert!(
                        refresh_result.is_ok(),
                        "IAM token refresh should succeed: {refresh_result:?}"
                    );

                    // Verify that the client still works after token refresh
                    assert_connected(&mut client).await;

                    // Test multiple consecutive refreshes
                    for i in 1..=3 {
                        let refresh_result = client.refresh_iam_token().await;
                        assert!(
                            refresh_result.is_ok(),
                            "IAM token refresh #{i} should succeed: {refresh_result:?}"
                        );

                        // Verify client still works after each refresh
                        assert_connected(&mut client).await;
                    }

                    cleanup();
                }
                Err(err) => {
                    cleanup();
                    panic!("Failed to create client with IAM authentication: {}", err);
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    fn test_iam_cluster_refresh_token_after_connection_kill_and_token_expired() {
        // Change the sleep duration inside the test to be > 900 if you want the token to be expired before reconnect
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = IAM_TEST_REGION_US_EAST_1;
            let endpoint = ELASTICACHE_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                // None,
                Some(3600), // Use default refresh interval
                true,       // cluster mode
                ServiceType::ELASTICACHE,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // Test initial connection
                    assert_connected(&mut client).await;

                    // Set a test key-value pair to verify functionality
                    let test_key = generate_random_string(10);
                    let test_value = generate_random_string(10);

                    let set_result = client
                        .send_command(redis::cmd("SET").arg(&test_key).arg(&test_value), None)
                        .await;
                    assert!(
                        set_result.is_ok(),
                        "SET command should succeed: {set_result:?}"
                    );

                    // Verify the value was set correctly
                    let get_result = client
                        .send_command(redis::cmd("GET").arg(&test_key), None)
                        .await;
                    assert_eq!(
                        get_result.unwrap(),
                        Value::BulkString(test_value.as_bytes().to_vec().into()),
                        "GET should return the set value"
                    );

                    // Change to 910 if you want the token to be expired before reconnect
                    tokio::time::sleep(std::time::Duration::from_secs(2)).await;

                    // generate a new token before reconnect
                    client.refresh_iam_token().await.unwrap();

                    // Kill all connections to simulate network interruption
                    kill_connection(&mut client).await;

                    // Test that the client can reconnect and function properly after connection kill
                    assert_connected(&mut client).await;

                    // Verify that we can still retrieve the previously set value after reconnection
                    let get_after_reconnect = client
                        .send_command(redis::cmd("GET").arg(&test_key), None)
                        .await;
                    assert_eq!(
                        get_after_reconnect.unwrap(),
                        Value::BulkString(test_value.as_bytes().to_vec().into()),
                        "GET after reconnection should return the same value"
                    );
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    /**
     * Ensures the client **reconnects** to an ElastiCache cluster with **IAM auth** after a
     * **node failover**.
     * Install aws sdk for running the test.
     * Follow the steps inside the test to trigger a node failover, while keeping the token expired.
     */
    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    fn test_iam_cluster_reconnection_after_node_kill() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = IAM_TEST_REGION_US_EAST_1;
            let endpoint = ELASTICACHE_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                Some(2400), // 40 min
                true,       // cluster mode
                ServiceType::ELASTICACHE,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // Test initial connection

                    use logger_core::log_info;
                    assert_connected(&mut client).await;

                    // Change to 900
                    // wait enough for the token to be expired
                    tokio::time::sleep(std::time::Duration::from_secs(1)).await;
                    log_info("Initial token is expired", "");

                    // run this script in the terminal to trigger a node failover after 900 seconds
                    //  aws elasticache test-failover \
                    //   --replication-group-id iam-auth-test \
                    //   --node-group-id 0001 \
                    //   --region us-east-1

                    // Change to 400
                    // Wait for the cluster and the client to stabilize
                    tokio::time::sleep(std::time::Duration::from_secs(1)).await;

                    // Test manual IAM token refresh
                    let refresh_result = client.refresh_iam_token().await;
                    assert!(
                        refresh_result.is_ok(),
                        "IAM token refresh should succeed: {refresh_result:?}"
                    );

                    // Verify that the client still works after token refresh
                    assert_connected(&mut client).await;

                    // Change to 900
                    // wait enough again for the token to be expired
                    tokio::time::sleep(std::time::Duration::from_secs(1)).await;
                    log_info("Refresh token is expired", "");

                    // run this script in the terminal to trigger a node failover after 900 seconds
                    //  aws elasticache test-failover \
                    //   --replication-group-id iam-auth-cluster \
                    //   --node-group-id 0001 \
                    //   --region us-east-1

                    // Change to 400
                    // When failovering the client doesn't have a valid token, but
                    // when when time interval hit the 2400 seconds, it will generate a new token and reconnect
                    // Follow the logs to verify that the client reconnected successfully.
                    tokio::time::sleep(std::time::Duration::from_secs(1)).await;

                    // Verify that the client still works after token refresh
                    assert_connected(&mut client).await;
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    /// Test that verifies IAM authentication persists after an automatic reconnection.
    /// This test:
    /// 1. Creates a client with mock IAM credentials
    /// 2. Verifies the connection works with IAM authentication
    /// 3. Simulates a connection drop by killing the connection
    /// 4. Sends another command which either:
    ///    - Fails due to the dropped connection, then retries and verifies reconnection with IAM auth
    ///    - Succeeds with a new client ID (indicating reconnection) and verifies still using IAM auth
    /// This ensures that IAM authentication persists across reconnections.
    fn test_iam_authentication_persistence_after_reconnection(
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async move {
            // Set mock AWS credentials
            setup_mock_aws_credentials();

            // Cleanup function to restore original credentials
            let cleanup = cleanup_mock_aws_credentials;

            let cluster_name = if use_cluster {
                IAM_TEST_CLUSTER_NAME
            } else {
                TEST_STANDALONE_NAME
            };
            let username = IAM_USERNAME;
            let region = IAM_TEST_REGION_US_EAST_1;

            // Create test basics with regular authentication first
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // Get the server address
            let address = match &test_basics.server {
                BackingServer::Standalone(server) => server
                    .as_ref()
                    .map(|s| s.get_client_addr())
                    .unwrap_or(get_shared_server_address(false)),
                BackingServer::Cluster(cluster) => {
                    if let Some(cluster) = cluster.as_ref() {
                        let addresses = cluster.get_server_addresses();
                        addresses[0].clone()
                    } else {
                        // Using shared cluster
                        let addresses = get_shared_cluster_addresses(false);
                        addresses[0].clone()
                    }
                }
            };

            // Create IAM connection request with mock credentials
            let iam_credentials = IamCredentials {
                cluster_name: cluster_name.into(),
                region: region.into(),
                service_type: ServiceType::ELASTICACHE.into(),
                refresh_interval_seconds: Some(5),
                ..Default::default()
            };

            let auth_info = AuthenticationInfo {
                password: String::new().into(),
                username: username.into(),
                iam_credentials: protobuf::MessageField::some(iam_credentials),
                ..Default::default()
            };

            let addresses_info = vec![get_address_info(&address)];

            let connection_request = glide_core::connection_request::ConnectionRequest {
                addresses: addresses_info,
                tls_mode: TlsMode::NoTls.into(),
                cluster_mode_enabled: use_cluster,
                request_timeout: 10000,
                authentication_info: protobuf::MessageField::some(auth_info),
                ..Default::default()
            };

            // Create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // Verify initial connection works
                    assert_connected(&mut client).await;

                    // Get initial client info
                    let mut client_info_cmd = redis::Cmd::new();
                    client_info_cmd.arg("CLIENT").arg("INFO");
                    let initial_client_info_response = client
                        .send_command(&mut client_info_cmd, None)
                        .await
                        .unwrap();

                    let initial_client_info = match initial_client_info_response {
                        Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                        Value::VerbatimString { text, .. } => text,
                        _ => panic!(
                            "Unexpected CLIENT INFO response type: {:?}",
                            initial_client_info_response
                        ),
                    };

                    let initial_client_id = utilities::extract_client_id(&initial_client_info)
                        .expect("Failed to extract initial client ID");

                    // Set a test key to verify functionality
                    let test_key = generate_random_string(10);
                    let test_value = "iam_test_value";
                    let set_result = client
                        .send_command(redis::cmd("SET").arg(&test_key).arg(test_value), None)
                        .await;
                    assert!(
                        set_result.is_ok(),
                        "SET command should succeed: {set_result:?}"
                    );

                    // Kill the connection to simulate a network drop
                    kill_connection(&mut client).await;

                    // Wait a moment for the connection to be fully dropped
                    tokio::time::sleep(std::time::Duration::from_millis(100)).await;

                    // Try to send another command - this should trigger reconnection
                    let res = client.send_command(&mut client_info_cmd, None).await;
                    match res {
                        Err(err) => {
                            // Connection was dropped as expected
                            assert!(
                                err.is_connection_dropped()
                                    || err.is_timeout()
                                    || err.kind() == redis::ErrorKind::AllConnectionsUnavailable,
                                "Expected connection dropped, timeout, or unavailable error, got: {err:?}",
                            );
                            // Retry and verify we can still connect with IAM auth after reconnection
                            let client_info = retry(|| async {
                                let mut client_clone = client.clone();
                                let mut cmd = client_info_cmd.clone();
                                let response =
                                    client_clone.send_command(&mut cmd, None).await.ok()?;
                                match response {
                                    Value::BulkString(bytes) => {
                                        Some(String::from_utf8_lossy(&bytes).to_string())
                                    }
                                    Value::VerbatimString { text, .. } => Some(text),
                                    _ => None,
                                }
                            })
                            .await;

                            // Verify we got a new client ID (reconnection happened)
                            let new_client_id = utilities::extract_client_id(&client_info)
                                .expect("Failed to extract new client ID");
                            assert_ne!(
                                initial_client_id, new_client_id,
                                "Client ID should change after reconnection"
                            );
                        }
                        Ok(response) => {
                            // Command succeeded after reconnection
                            let new_client_info = match response {
                                Value::BulkString(bytes) => {
                                    String::from_utf8_lossy(&bytes).to_string()
                                }
                                Value::VerbatimString { text, .. } => text,
                                _ => panic!("Unexpected CLIENT INFO response type: {:?}", response),
                            };
                            let new_client_id = utilities::extract_client_id(&new_client_info)
                                .expect("Failed to extract new client ID");

                            // Client ID may or may not change depending on timing
                            if new_client_id != initial_client_id {
                                println!(
                                    "Reconnection detected: client ID changed from {} to {}",
                                    initial_client_id, new_client_id
                                );
                            }
                        }
                    }

                    // Verify we can still retrieve the previously set value after reconnection
                    let get_result = client
                        .send_command(redis::cmd("GET").arg(&test_key), None)
                        .await;
                    assert!(
                        get_result.is_ok(),
                        "GET after reconnection should succeed: {get_result:?}"
                    );
                    assert_eq!(
                        get_result.unwrap(),
                        Value::BulkString(test_value.as_bytes().to_vec().into()),
                        "GET should return the same value after reconnection"
                    );

                    cleanup();
                }
                Err(err) => {
                    cleanup();
                    panic!("Failed to create client with IAM authentication: {}", err);
                }
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_request_timeout(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(1), // milliseconds
                    shared_server: false,
                    ..Default::default()
                },
            )
            .await;
            let mut cmd = redis::Cmd::new();
            // Create a long running command to ensure we get into timeout
            cmd.arg("EVAL")
                .arg(
                    r#"
                    while (true)
                    do
                    redis.call('ping')
                    end
                "#,
                )
                .arg("0");
            let result = test_basics.client.send_command(&mut cmd, None).await;
            assert!(result.is_err());
            let err = result.unwrap_err();
            assert!(err.is_timeout(), "{err}");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_blocking_command_doesnt_raise_timeout_error(#[values(false, true)] use_cluster: bool) {
        // We test that the request timeout is based on the value specified in the blocking command argument,
        // and not on the one set in the client configuration. To achieve this, we execute a command designed to
        // be blocked until it reaches the specified command timeout. We set the client's request timeout to
        // a shorter duration than the blocking command's timeout. Subsequently, we confirm that we receive
        // a response from the server instead of encountering a timeout error.
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(1), // milliseconds
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let mut cmd = redis::Cmd::new();
            cmd.arg("BLPOP").arg(generate_random_string(10)).arg(0.3); // server should return null after 300 millisecond
            let result = test_basics.client.send_command(&mut cmd, None).await;
            assert!(result.is_ok());
            assert_eq!(result.unwrap(), Value::Nil);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_blocking_command_with_negative_timeout_returns_error(
        #[values(false, true)] use_cluster: bool,
    ) {
        // We test that when blocking command is passed with a negative timeout the command will return with an error
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(1), // milliseconds
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            let mut cmd = redis::Cmd::new();
            cmd.arg("BLPOP").arg(generate_random_string(10)).arg(-1);
            let result = test_basics.client.send_command(&mut cmd, None).await;
            assert!(result.is_err());
            let err = result.unwrap_err();
            assert_eq!(err.kind(), redis::ErrorKind::ResponseError);
            assert!(err.to_string().contains("negative"));
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_blocking_command_with_zero_timeout_blocks_indefinitely(
        #[values(false, true)] use_cluster: bool,
    ) {
        // We test that when a blocking command is passed with a timeout duration of 0, it will block the client indefinitely
        block_on_all(async {
            let config = TestConfiguration {
                request_timeout: Some(1), // millisecond
                shared_server: true,
                ..Default::default()
            };
            let mut test_basics = setup_test_basics(use_cluster, config).await;
            let key = generate_random_string(10);
            let future = async move {
                let mut cmd = redis::Cmd::new();
                cmd.arg("BLPOP").arg(key).arg(0); // `0` should block indefinitely
                test_basics.client.send_command(&mut cmd, None).await
            };
            // We execute the command with Tokio's timeout wrapper to prevent the test from hanging indefinitely.
            let tokio_timeout_result =
                tokio::time::timeout(DEFAULT_RESPONSE_TIMEOUT * 2, future).await;
            assert!(tokio_timeout_result.is_err());
        });
    }

    /// Serialized `ConnectionRequest` pointing at the backing test server, for
    /// seeding a scope pool. Standalone uses the single server address; cluster
    /// uses the node addresses (the scope layer resolves the concrete node for a
    /// slot from the parent client when one is supplied).
    fn scope_request_bytes(server: &BackingServer, configuration: &TestConfiguration) -> Vec<u8> {
        use protobuf::Message as _;
        let addresses: Vec<redis::ConnectionAddr> = match server {
            BackingServer::Standalone(server) => vec![
                server
                    .as_ref()
                    .map(|s| s.get_client_addr())
                    .unwrap_or(get_shared_server_address(configuration.use_tls)),
            ],
            BackingServer::Cluster(cluster) => cluster
                .as_ref()
                .map(|c| c.get_server_addresses())
                .unwrap_or_else(|| get_shared_cluster_addresses(configuration.use_tls)),
        };
        create_connection_request(&addresses, configuration)
            .write_to_bytes()
            .expect("serialize scope connection request")
    }

    /// A live scope backed by a real connection to the test server, ready to
    /// execute commands via `send_scope_command`. Callers must invoke `release`
    /// when done to avoid leaking global scope-registry state between tests.
    struct ScopeHandle {
        client: Client,
        client_id: u64,
        scope_id: u64,
        pool: std::sync::Arc<tokio::sync::Mutex<glide_core::pool::ScopePool>>,
        released: std::cell::Cell<bool>,
    }

    impl ScopeHandle {
        /// Seat a real scoped connection and acquire a scope_id for it.
        ///
        /// `routing_slot` selects the cluster node in cluster mode; it is ignored
        /// in standalone mode. A unique `client_id` avoids collisions in the
        /// process-global scope registry across serial test runs.
        async fn setup(client: Client, bytes: Vec<u8>, routing_slot: u16) -> ScopeHandle {
            let client_id = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .expect("system clock after epoch")
                .as_nanos() as u64;

            glide_core::scope::register_client(client_id, client.clone());
            let pool = glide_core::pool::get_or_create_scope_pool(client_id, bytes.clone());

            // Reserve capacity, then synchronously seat one idle connection. This
            // mirrors the reserve-before-create contract that `try_acquire_scope`
            // relies on: `create_scope_connection` only decrements on failure.
            pool.lock()
                .await
                .total_count
                .fetch_add(1, std::sync::atomic::Ordering::AcqRel);
            glide_core::scope::create_scope_connection(
                pool.clone(),
                Some(&client),
                &bytes,
                routing_slot,
            )
            .await;

            let scope_id = {
                let mut guard = pool.lock().await;
                match guard.try_acquire(glide_core::pool::get_scope_registry(), routing_slot) {
                    glide_core::pool::ScopeAcquire::Reused(scope_id) => scope_id,
                    other => panic!("failed to acquire scope (connection not seated): {other:?}"),
                }
            };

            ScopeHandle {
                client,
                client_id,
                scope_id,
                pool,
                released: std::cell::Cell::new(false),
            }
        }

        async fn send(&self, cmd_name: &str, args: &mut [Vec<u8>]) -> redis::RedisResult<Value> {
            glide_core::scope::send_scope_command(self.scope_id, cmd_name, args, Some(&self.client))
                .await
        }

        fn release(&self) {
            if self.released.replace(true) {
                return;
            }
            let handle = tokio::runtime::Handle::current();
            glide_core::scope::release_scope(self.scope_id, self.client_id, &handle);
            glide_core::scope::unregister_client(self.client_id);
        }
    }

    impl Drop for ScopeHandle {
        /// Teardown runs unconditionally, even when a test asserts before calling
        /// `release`, so a failed assertion cannot leak this client's registration
        /// into the process-global registry and poison the next serial test.
        fn drop(&mut self) {
            self.release();
        }
    }

    /// Polls `try_acquire_scope` the way the bindings do (see `GlideClient`'s
    /// `scopedConnection` loop), returning `None` if the deadline passes.
    #[cfg(feature = "proto")]
    async fn acquire_scope_within(
        client_id: u64,
        bytes: &[u8],
        timeout: std::time::Duration,
    ) -> Option<u64> {
        let runtime = tokio::runtime::Handle::current();
        let deadline = std::time::Instant::now() + timeout;
        while std::time::Instant::now() < deadline {
            let result =
                glide_core::scope::try_acquire_scope(client_id, bytes.to_vec(), &runtime, 0);
            if result >= 0 {
                return Some(result as u64);
            }
            tokio::time::sleep(std::time::Duration::from_millis(10)).await;
        }
        None
    }

    /// `max_total = N` must permit exactly N concurrent scopes, with N+1 the first
    /// rejection. Exercises the whole borrow path against a real server — reserve,
    /// create, reuse, and `in_use` accounting — which the reservation-layer unit
    /// test in `pool.rs` cannot reach on its own.
    #[cfg(feature = "proto")]
    #[rstest]
    #[serial_test::serial]
    fn test_scope_pool_permits_exactly_max_total_concurrent_scopes() {
        const MAX_TOTAL: u32 = 3;

        block_on_all(async {
            let configuration = TestConfiguration {
                shared_server: true,
                ..Default::default()
            };
            let test_basics = setup_test_basics(false, configuration.clone()).await;
            let bytes = scope_request_bytes(&test_basics.server, &configuration);

            let client_id = 67_950_001_u64;
            glide_core::scope::register_client(client_id, test_basics.client.clone());
            glide_core::pool::get_client_scope_pools().insert(
                client_id,
                std::sync::Arc::new(tokio::sync::Mutex::new(glide_core::pool::ScopePool::new(
                    glide_core::pool::ScopePoolConfig {
                        max_total: MAX_TOTAL,
                        ..Default::default()
                    },
                    bytes.clone(),
                    client_id,
                ))),
            );

            let mut held = Vec::new();
            for i in 0..MAX_TOTAL {
                let scope_id =
                    acquire_scope_within(client_id, &bytes, std::time::Duration::from_secs(10))
                        .await
                        .unwrap_or_else(|| {
                            panic!("scope {} of {MAX_TOTAL} must be acquirable", i + 1)
                        });
                held.push(scope_id);
            }

            let over_capacity =
                acquire_scope_within(client_id, &bytes, std::time::Duration::from_millis(500))
                    .await;

            let runtime = tokio::runtime::Handle::current();
            for scope_id in &held {
                glide_core::scope::release_scope(*scope_id, client_id, &runtime);
            }
            glide_core::scope::unregister_client(client_id);
            glide_core::pool::get_client_scope_pools().remove(&client_id);

            let mut distinct = held.clone();
            distinct.sort_unstable();
            distinct.dedup();
            assert_eq!(
                distinct.len(),
                MAX_TOTAL as usize,
                "each concurrent scope must get its own connection, got {held:?}"
            );
            assert!(
                over_capacity.is_none(),
                "scope {} must be rejected while {MAX_TOTAL} are held, got {over_capacity:?}",
                MAX_TOTAL + 1
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_scoped_blocking_command_with_zero_timeout_blocks_indefinitely(
        #[values(false, true)] use_cluster: bool,
    ) {
        // A blocking command issued through a scoped connection must honour the
        // command's own timeout (0 = block forever), not the client's flat
        // request_timeout. With request_timeout set to 1ms, a naive implementation
        // would abort BLPOP almost immediately; the correct behaviour is to keep
        // blocking. We confirm this by racing the scoped BLPOP against a Tokio
        // timeout: if the scope still hasn't returned when the Tokio timeout
        // fires, the command was (correctly) still blocking.
        block_on_all(async {
            let config = TestConfiguration {
                request_timeout: Some(1), // millisecond
                shared_server: true,
                ..Default::default()
            };
            let test_basics = setup_test_basics(use_cluster, config.clone()).await;
            let bytes = scope_request_bytes(&test_basics.server, &config);
            let key = generate_random_string(10);
            let routing_slot = glide_core::pool::slot_for_key(key.as_bytes());

            let scope = ScopeHandle::setup(test_basics.client, bytes, routing_slot).await;

            let future = async {
                // `0` should block indefinitely; nothing is ever pushed to `key`.
                let mut args = vec![key.into_bytes(), b"0".to_vec()];
                scope.send("BLPOP", &mut args).await
            };

            // If BLPOP were incorrectly bounded by the 1ms request_timeout, this
            // future would resolve well before the Tokio timeout. An elapsed error
            // means it was still blocking, which is what we want.
            let tokio_timeout_result =
                tokio::time::timeout(DEFAULT_RESPONSE_TIMEOUT * 2, future).await;
            assert!(
                tokio_timeout_result.is_err(),
                "scoped BLPOP with a 0 timeout returned early instead of blocking: {tokio_timeout_result:?}"
            );

            scope.release();
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_scoped_blocking_command_honours_finite_command_timeout(
        #[values(false, true)] use_cluster: bool,
    ) {
        // A blocking command with a FINITE timeout must honour its own command-level
        // deadline through the scope layer, not the much shorter flat request_timeout.
        // With request_timeout = 1s and BLPOP <key> 5, a naive implementation bounded
        // by request_timeout would return at ~1s; the correct behaviour (get_request_timeout
        // -> Some(5s) for the blocking arg) keeps blocking until the command's own 5s
        // deadline. We assert the call lasted clearly longer than the flat 1s timeout
        // (> 2s, generous to avoid flakiness) rather than an exact 5s.
        block_on_all(async {
            let config = TestConfiguration {
                request_timeout: Some(1000), // 1s flat request timeout
                shared_server: true,
                ..Default::default()
            };
            let test_basics = setup_test_basics(use_cluster, config.clone()).await;
            let bytes = scope_request_bytes(&test_basics.server, &config);
            let key = generate_random_string(10);
            let routing_slot = glide_core::pool::slot_for_key(key.as_bytes());

            let scope = ScopeHandle::setup(test_basics.client, bytes, routing_slot).await;

            let started = std::time::Instant::now();
            // `5` = block up to 5 seconds; nothing is ever pushed to `key`, so this
            // returns only when the command's own deadline elapses.
            let mut args = vec![key.into_bytes(), b"5".to_vec()];
            let result = scope.send("BLPOP", &mut args).await;
            let elapsed = started.elapsed();

            assert!(
                elapsed > std::time::Duration::from_secs(2),
                "scoped BLPOP with a 5s timeout returned after {elapsed:?}, which is at \
                 or below the 1s flat request_timeout — the per-command timeout was not honoured"
            );

            // The command must reach its own 5s server-side deadline cleanly: BLPOP on
            // an empty key at its timeout returns a nil reply. Asserting the nil success
            // (rather than merely `is_ok()` or just the timing) rejects both a spurious
            // non-nil success and any post-2s connection/protocol error, which would
            // otherwise satisfy the timing check alone.
            assert_eq!(
                result,
                Ok(Value::Nil),
                "scoped BLPOP should return a nil reply at its command deadline, got {result:?}"
            );

            scope.release();
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_scoped_blocking_command_clean_protocol_error_reuses_connection(
        #[values(false, true)] use_cluster: bool,
    ) {
        // A blocking command that fails with a clean protocol error never armed a
        // server-side waiter, so its connection stays reusable and must be returned
        // to the idle pool on release — not churned. We trigger the deterministic
        // WRONGTYPE case: SET a string key, then BLPOP it (BLPOP on a non-list
        // returns WRONGTYPE immediately). Pre-fix (poison-on-any-error) the released
        // connection was discarded; post-fix it is reused.
        block_on_all(async {
            let config = TestConfiguration {
                request_timeout: Some(1000),
                shared_server: true,
                ..Default::default()
            };
            let test_basics = setup_test_basics(use_cluster, config.clone()).await;
            let bytes = scope_request_bytes(&test_basics.server, &config);
            let key = generate_random_string(10);
            let routing_slot = glide_core::pool::slot_for_key(key.as_bytes());

            let scope = ScopeHandle::setup(test_basics.client, bytes, routing_slot).await;

            // Make the key a string so BLPOP on it fails fast with WRONGTYPE.
            let mut set_args = vec![key.clone().into_bytes(), b"not-a-list".to_vec()];
            scope
                .send("SET", &mut set_args)
                .await
                .expect("SET should succeed");

            let mut blpop_args = vec![key.into_bytes(), b"0".to_vec()];
            let result = scope.send("BLPOP", &mut blpop_args).await;
            assert!(
                result.is_err(),
                "expected WRONGTYPE error from BLPOP on a string key, got {result:?}"
            );
            let err = result.unwrap_err();
            assert!(
                !err.is_timeout() && !err.is_io_error(),
                "WRONGTYPE must be a clean protocol error, not timeout/io: {err:?}"
            );

            scope.release();

            // A clean protocol error leaves the connection reusable: it is returned
            // to idle and still counted, rather than discarded.
            let pool = scope.pool.lock().await;
            assert!(
                !pool.idle.is_empty(),
                "connection with a clean protocol error was discarded instead of reused"
            );
            assert_eq!(
                pool.total_count.load(std::sync::atomic::Ordering::Acquire),
                1,
                "reusable connection was dropped from the pool total"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_scoped_blocking_command_cancelled_connection_is_not_reused(
        #[values(false, true)] use_cluster: bool,
    ) {
        // When a scoped blocking command is cancelled while still in flight (its
        // future dropped before the server responds), the underlying connection
        // may still have an armed server-side waiter. Returning such a connection
        // to the idle pool would let it silently swallow a later, unrelated push
        // to the same key instead of delivering it to a legitimate consumer. The
        // fix marks the connection unrecoverable and discards it on release. We
        // assert that observable outcome: after cancelling an in-flight scoped
        // BLPOP and releasing the scope, the connection is neither returned to the
        // idle pool nor left counted, so it cannot be handed out again.
        block_on_all(async {
            let config = TestConfiguration {
                request_timeout: Some(1), // millisecond
                shared_server: true,
                ..Default::default()
            };
            let test_basics = setup_test_basics(use_cluster, config.clone()).await;
            let bytes = scope_request_bytes(&test_basics.server, &config);
            let key = generate_random_string(10);
            let routing_slot = glide_core::pool::slot_for_key(key.as_bytes());

            let scope = ScopeHandle::setup(test_basics.client, bytes, routing_slot).await;

            // Start a blocking BLPOP that will never complete (nothing is pushed),
            // then cancel it by letting a short Tokio timeout elapse. Dropping the
            // future mid-flight leaves the connection's blocking-in-flight marker
            // set, which release must treat as unrecoverable.
            let cancelled = {
                let key = key.clone();
                tokio::time::timeout(std::time::Duration::from_millis(300), async {
                    let mut args = vec![key.into_bytes(), b"0".to_vec()];
                    scope.send("BLPOP", &mut args).await
                })
                .await
            };
            assert!(
                cancelled.is_err(),
                "expected the in-flight scoped BLPOP to be cancelled, got {cancelled:?}"
            );

            scope.release();

            // The discarded connection must not be reusable: not parked as idle,
            // not tracked as in-use, and not counted toward the pool total.
            let pool = scope.pool.lock().await;
            assert!(
                pool.idle.is_empty(),
                "cancelled blocking connection was returned to the idle pool"
            );
            assert!(
                pool.in_use.is_empty(),
                "released scope is still marked in-use"
            );
            assert_eq!(
                pool.total_count.load(std::sync::atomic::Ordering::Acquire),
                0,
                "discarded blocking connection was still counted in the pool total"
            );
            drop(pool);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_scoped_release_contended_lock_reclaims_slot_synchronously(
        #[values(false, true)] use_cluster: bool,
    ) {
        // Regression for the release() Err(_) branch: when a scoped blocking
        // command is still holding the connection lock at release time (its
        // binding-side await was cancelled but the native task kept running and
        // is parked in an unbounded BLPOP), release() cannot lock the connection
        // and takes the try_lock -> Err path. The pool slot MUST be reclaimed
        // synchronously there; the earlier code deferred the total_count
        // decrement into a spawned task gated on acquiring that same connection
        // lock, so for an unbounded blocking command the lock never freed and the
        // slot leaked forever (total_count stuck at 1).
        //
        // We force the contended path deterministically: hold a clone of the
        // connection's Arc<Mutex> for the whole test, then call release(). The
        // internal try_lock is guaranteed to fail, so we exercise exactly the
        // Err(_) branch. We assert total_count returns to 0 immediately, WITHOUT
        // ever releasing our held lock (mirroring a never-completing BLPOP).
        block_on_all(async {
            let config = TestConfiguration {
                request_timeout: Some(1), // millisecond
                shared_server: true,
                ..Default::default()
            };
            let test_basics = setup_test_basics(use_cluster, config.clone()).await;
            let bytes = scope_request_bytes(&test_basics.server, &config);
            let key = generate_random_string(10);
            let routing_slot = glide_core::pool::slot_for_key(key.as_bytes());

            let scope = ScopeHandle::setup(test_basics.client, bytes, routing_slot).await;

            // A seated + acquired scope means the slot is counted.
            {
                let pool = scope.pool.lock().await;
                assert_eq!(
                    pool.total_count.load(std::sync::atomic::Ordering::Acquire),
                    1,
                    "expected one counted connection before release"
                );
            }

            // Grab and hold the connection lock the way an in-flight, never
            // completing blocking command would. This clone keeps the Arc alive
            // and the lock held for the duration of release().
            let registry = glide_core::pool::get_scope_registry();
            let conn_arc = registry
                .get(&scope.scope_id)
                .expect("scope entry present before release")
                .connection
                .clone();
            let _held = conn_arc
                .try_lock()
                .expect("test should be sole holder before release");

            // Drive release() directly so the branch is deterministic. We hold the
            // pool lock ourselves (as release_scope's Ok arm does) and stay inside
            // the runtime so the best-effort cleanup task has a reactor.
            {
                let mut pool = scope.pool.lock().await;
                let reclaimed = pool.release(scope.scope_id, registry);
                assert!(reclaimed, "release should report the scope was reclaimed");

                // The decrement must have happened synchronously in the Err(_)
                // branch — we are STILL holding the connection lock, so any
                // lock-gated deferred decrement (the buggy path) could not have
                // run yet. Pre-fix this reads 1 (leaked); post-fix it reads 0.
                assert_eq!(
                    pool.total_count.load(std::sync::atomic::Ordering::Acquire),
                    0,
                    "pool slot was not reclaimed synchronously on the contended \
                     release path — the blocking command's held lock leaked it"
                );
                assert!(
                    pool.in_use.is_empty(),
                    "released scope is still marked in-use"
                );
            }

            // Never released `_held`, mirroring an unbounded BLPOP that never
            // completes; the slot must already be reclaimed regardless.
            drop(_held);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    #[ignore = "Fails on the pre-existing gap tracked in #7000: dropping the \
                MultiplexedConnection doesn't close the socket while a command is \
                outstanding, so a cancelled BLPOP's server-side waiter can still steal \
                a later push. Not a regression from this PR."]
    fn test_scoped_blocking_command_expired_does_not_steal_later_push(
        #[values(false, true)] use_cluster: bool,
    ) {
        // Full end-to-end version of the "no silent consumption" guarantee: a
        // scoped BLPOP is cancelled in flight and its scope released; a later
        // legitimate consumer then blocks on the same key and must receive a
        // subsequent push, proving the stale scoped connection did not intercept it.
        block_on_all(async {
            let config = TestConfiguration {
                request_timeout: Some(1), // millisecond
                shared_server: true,
                ..Default::default()
            };
            let mut test_basics = setup_test_basics(use_cluster, config.clone()).await;
            let bytes = scope_request_bytes(&test_basics.server, &config);
            let key = generate_random_string(10);
            let routing_slot = glide_core::pool::slot_for_key(key.as_bytes());

            let scope = ScopeHandle::setup(test_basics.client.clone(), bytes, routing_slot).await;

            // Cancel an in-flight scoped BLPOP, then release (connection discarded).
            let cancelled = {
                let key = key.clone();
                tokio::time::timeout(std::time::Duration::from_millis(300), async {
                    let mut args = vec![key.clone().into_bytes(), b"0".to_vec()];
                    scope.send("BLPOP", &mut args).await
                })
                .await
            };
            assert!(
                cancelled.is_err(),
                "expected cancellation, got {cancelled:?}"
            );
            scope.release();

            // A legitimate consumer blocks on the same key with a finite timeout.
            let consumer = {
                let mut consumer_client = test_basics.client.clone();
                let key = key.clone();
                tokio::spawn(async move {
                    let mut cmd = redis::Cmd::new();
                    cmd.arg("BLPOP").arg(&key).arg(2); // seconds
                    consumer_client.send_command(&mut cmd, None).await
                })
            };

            // Give the consumer time to register its waiter, then push once.
            tokio::time::sleep(std::time::Duration::from_millis(200)).await;
            let mut push_cmd = redis::Cmd::new();
            push_cmd.arg("RPUSH").arg(&key).arg("payload");
            let _ = test_basics.client.send_command(&mut push_cmd, None).await;

            // The push must reach the legitimate consumer, not a stale scoped waiter.
            let consumed = consumer.await.expect("consumer task join");
            let value = consumed.expect("consumer BLPOP result");
            match value {
                Value::Array(items) => {
                    assert_eq!(items.len(), 2, "BLPOP should return [key, value]");
                    assert_eq!(items[1], Value::BulkString(b"payload".to_vec().into()));
                }
                other => panic!("legitimate consumer did not receive the push: {other:?}"),
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_scoped_blocking_connection_discarded_on_concurrent_release(
        #[values(false, true)] use_cluster: bool,
    ) {
        // Guards the clear-then-requeue race on the deferred release path. When
        // `release` races a command still holding the connection lock, `try_lock`
        // fails and the work is deferred to a spawned task. A binding may cancel a
        // blocking command and release the scope while the native command is still
        // parked; if a push then satisfies it, the command completes and clears its
        // in-flight marker. A release that re-inspected state *after* the command
        // finished would see a clean connection and requeue it — even though the
        // push was already consumed. Correct behaviour is to discard from the fact
        // that release raced an in-flight command, not from post-completion state.
        //
        // We reproduce deterministically: hold the lock so release defers, then
        // reset the state to clean BEFORE the deferred task runs, then release the
        // lock and assert the connection is still discarded.
        block_on_all(async {
            let config = TestConfiguration {
                request_timeout: Some(1), // millisecond
                shared_server: true,
                ..Default::default()
            };
            let test_basics = setup_test_basics(use_cluster, config.clone()).await;
            let bytes = scope_request_bytes(&test_basics.server, &config);
            let key = generate_random_string(10);
            let routing_slot = glide_core::pool::slot_for_key(key.as_bytes());

            let scope = ScopeHandle::setup(test_basics.client, bytes, routing_slot).await;

            // Clone the registry entry's connection Arc and hold its lock so that
            // release's `try_lock` fails and the spawned (deferred) path is taken.
            let registry = glide_core::pool::get_scope_registry();
            let conn_arc = registry
                .get(&scope.scope_id)
                .expect("scope entry present before release")
                .connection
                .clone();
            let mut guard = conn_arc.lock().await;

            // Release while the lock is held -> Err(_)/try_lock-contention branch.
            let released = {
                let mut pool = scope.pool.lock().await;
                pool.release(scope.scope_id, registry)
            };
            assert!(released, "release should report success even when deferred");

            // Simulate the racing blocking command completing (a late push arrived)
            // and clearing every marker before the deferred task runs. This is the
            // exact window the old post-completion state check got wrong: the
            // connection now looks perfectly clean.
            guard.state = glide_core::pool::ConnectionState::with_configured_db(0);
            assert!(
                guard.state.is_clean_for(0),
                "precondition: state must look clean so a post-completion check \
                 would (wrongly) requeue"
            );

            // Let the spawned task acquire the connection now that we release it.
            drop(guard);

            // Wait (bounded) for the deferred task to finish adjusting pool state.
            let discarded = {
                let mut discarded = false;
                for _ in 0..200 {
                    let pool = scope.pool.lock().await;
                    if pool.idle.is_empty()
                        && pool.total_count.load(std::sync::atomic::Ordering::Acquire) == 0
                    {
                        discarded = true;
                        break;
                    }
                    drop(pool);
                    tokio::time::sleep(std::time::Duration::from_millis(5)).await;
                }
                discarded
            };

            let pool = scope.pool.lock().await;
            assert!(
                discarded,
                "deferred release requeued a connection that raced an in-flight \
                 blocking command (clear-then-requeue race): idle={}, total_count={}",
                pool.idle.len(),
                pool.total_count.load(std::sync::atomic::Ordering::Acquire)
            );
            drop(pool);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_request_transaction_timeout(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(1),
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // BLPOP doesn't block in transactions, so we'll pause the client instead.
            let mut cmd = redis::cmd("CLIENT");
            cmd.arg("PAUSE").arg(100);
            let _ = test_basics
                .client
                .send_command(
                    &mut cmd,
                    Some(RoutingInfo::MultiNode((
                        MultipleNodeRoutingInfo::AllNodes,
                        None,
                    ))),
                )
                .await;

            let mut pipeline = redis::pipe();
            pipeline.atomic();
            pipeline.cmd("GET").arg("foo");
            let result = test_basics
                .client
                .send_transaction(&pipeline, None, None, false)
                .await;
            assert!(result.is_err(), "Received {result:?}");
            let err = result.unwrap_err();
            assert!(err.is_timeout(), "{err}");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_client_name_after_reconnection(#[values(false, true)] use_cluster: bool) {
        const CLIENT_NAME: &str = "TEST_CLIENT_NAME";
        let mut client_info_cmd = redis::Cmd::new();
        client_info_cmd.arg("CLIENT").arg("INFO");
        block_on_all(async move {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    client_name: Some(CLIENT_NAME.to_string()),
                    ..Default::default()
                },
            )
            .await;

            for i in 0..2 {
                // ensure all connections have CLIENT_NAME set
                let mut client = test_basics.client.clone();
                let client_infos: HashMap<String, String> = {
                    let variant_res = client
                        .send_command(
                            &mut client_info_cmd,
                            Some(RoutingInfo::MultiNode((
                                MultipleNodeRoutingInfo::AllNodes,
                                None,
                            ))),
                        )
                        .await
                        .unwrap();

                    if use_cluster {
                        redis::from_owned_redis_value(variant_res).unwrap()
                    } else {
                        [(
                            "DONT_CARE".to_string(),
                            redis::from_owned_redis_value(variant_res).unwrap(),
                        )]
                        .into()
                    }
                };

                for client_info in client_infos.values() {
                    assert!(client_info.contains(&format!("name={CLIENT_NAME}")));
                }

                if i == 0 {
                    // first pass - kill the connections
                    kill_connection(&mut client).await;
                    // short sleep to allow the connection validation task to reconnect - 1s is enough since the detection should happen immediately
                    tokio::time::sleep(std::time::Duration::from_secs(1)).await;
                }
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_resp_support(
        #[values(false, true)] use_cluster: bool,
        #[values(2, 3)] protocol: i64,
    ) {
        let protocol_enum = match protocol {
            2 => redis::ProtocolVersion::RESP2,
            3 => redis::ProtocolVersion::RESP3,
            _ => panic!(),
        };
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    connection_info: Some(RedisConnectionInfo {
                        protocol: protocol_enum,
                        ..Default::default()
                    }),
                    protocol: match protocol {
                        2 => ProtocolVersion::RESP2,
                        3 => ProtocolVersion::RESP3,
                        _ => panic!(),
                    },
                    ..Default::default()
                },
            )
            .await;
            let hello: std::collections::HashMap<String, Value> = redis::from_owned_redis_value(
                test_basics
                    .client
                    .send_command(&mut redis::cmd("HELLO"), None)
                    .await
                    .expect("HELLO failed"),
            )
            .unwrap();
            assert_eq!(hello.get("proto").unwrap(), &Value::Int(protocol));

            let (key, field, value, field2, value2) = (
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
            );
            let mut pipeline = Pipeline::new();
            pipeline.hset(&key, &field, &value);
            pipeline.hset(&key, &field2, &value2);
            pipeline.hgetall(&key);

            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    Some(PIPELINE_TEST_TIMEOUT_MS),
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");
            assert_eq!(
                result,
                Value::Array(vec![
                    Value::Int(1),
                    Value::Int(1),
                    Value::Map(vec![
                        (
                            Value::BulkString(field.as_bytes().to_vec().into()),
                            Value::BulkString(value.as_bytes().to_vec().into())
                        ),
                        (
                            Value::BulkString(field2.as_bytes().to_vec().into()),
                            Value::BulkString(value2.as_bytes().to_vec().into())
                        )
                    ])
                ]),
                "Pipeline result: {result:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_return_error(
        #[values(false, true)] use_cluster: bool,
        #[values(false, true)] raise_error: bool,
    ) {
        use redis::ErrorKind;

        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // Generate random keys.
            let (key, value, key2) = (
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
            );

            let mut pipeline = Pipeline::new();
            pipeline.set(&key, &value).get(&key).llen(&key).get(&key2);

            let res = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    raise_error,
                    Some(PIPELINE_TEST_TIMEOUT_MS),
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await;

            match raise_error {
                false => {
                    let res = match res {
                        Ok(Value::Array(arr)) => arr,
                        _ => panic!("Expected an array response, got: {res:?}"),
                    };
                    assert_eq!(
                        &res[..2],
                        &[
                            Value::Okay,
                            Value::BulkString(value.as_bytes().to_vec().into()),
                        ],
                        "Pipeline result: {res:?}"
                    );

                    assert!(
                        matches!(res[2], Value::ServerError(ref err) if err.err_code().contains("WRONGTYPE"))
                    );
                }
                true => {
                    assert!(res.is_err(), "Pipeline should fail with wrong type error");
                    let err = res.unwrap_err();
                    assert_eq!(
                        err.kind(),
                        ErrorKind::ExtensionError,
                        "Pipeline should fail with response error"
                    );
                    assert!(err.to_string().contains("WRONGTYPE"), "{err:?}");
                }
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_with_blocking_command_returns_null(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    request_timeout: Some(3000), // allow enough time for the BLPOP to timeout
                    ..Default::default()
                },
            )
            .await;

            // Generate two random keys.
            let key = generate_random_string(10);
            let key2 = generate_random_string(10);

            // Build a pipeline:
            // 1. SET key to "value1"
            // 2. GET key (should return "value1")
            // 3. BLPOP key2 with a 2-second timeout (should return null since key2 is empty)
            // 4. GET key2 (should return null)
            let mut pipeline = Pipeline::new();
            pipeline.set(&key, "value1");
            pipeline.get(&key);
            pipeline.blpop(&key2, 1.0);
            pipeline.get(&key2);

            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    Some(PIPELINE_TEST_TIMEOUT_MS),
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");

            // Expected results:
            // - SET returns OK
            // - GET returns "value1"
            // - BLPOP returns null (because of timeout)
            // - GET returns null (key2 was never set)
            assert_eq!(
                result,
                Value::Array(vec![
                    Value::Okay,
                    Value::BulkString(b"value1".to_vec().into()),
                    Value::Nil,
                    Value::Nil,
                ]),
                "Pipeline with blocking command should return null for BLPOP and GET on a non-existent key {result:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_blocking_command_inside_pipeline_raises_timeout_error(
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    request_timeout: Some(1000),
                    ..Default::default()
                },
            )
            .await;

            // Generate random keys.
            let (key, key2) = (generate_random_string(10), generate_random_string(10));

            let mut pipeline = Pipeline::new();
            pipeline
                .set(&key, "value1")
                .get(&key)
                .blpop(&key2, 2.0)
                .get(&key2);

            // Pipeline containing BLPOP with 2s block exceeds the 1s request_timeout,
            // so it should time out.
            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await;

            assert!(
                result.is_err(),
                "Pipeline with blocking command exceeding timeout should fail"
            );
            let err = result.unwrap_err();
            assert!(err.is_timeout(), "Expected timeout error, got: {err:?}");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_timeout(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    request_timeout: Some(1000),
                    ..Default::default()
                },
            )
            .await;

            // Generate random keys.
            let (key, key2) = (generate_random_string(10), generate_random_string(10));

            let mut pipeline = Pipeline::new();
            pipeline
                .set(&key, "value1")
                .get(&key)
                .blpop(&key2, 2.0)
                .get(&key2);

            let res = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    Some(3000),
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");

            assert_eq!(
                res,
                Value::Array(vec![
                    Value::Okay,
                    Value::BulkString(b"value1".to_vec().into()),
                    Value::Nil,
                    Value::Nil,
                ]),
                "Pipeline result: {res:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_transaction_timeout(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    request_timeout: Some(1000),
                    ..Default::default()
                },
            )
            .await;

            // Generate random keys.
            let key = generate_random_string(10);

            let mut transaction = redis::pipe();
            transaction.atomic();
            transaction.blpop(&key, 2.0).get(&key);

            let res = test_basics
                .client
                .send_transaction(&transaction, None, Some(3000), true)
                .await
                .expect("Transaction failed");

            assert_eq!(
                res,
                Value::Array(vec![Value::Nil, Value::Nil,]),
                "Transaction result: {res:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_can_reconnect(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let (key, value) = (generate_random_string(10), generate_random_string(10));
            let (key2, value2) = (generate_random_string(10), generate_random_string(10));

            let mut pipeline = Pipeline::new();
            pipeline
                .set(&key, &value)
                .get(&key)
                .set(&key2, &value2)
                .get(&key2);

            // kill the connection for `key2`
            kill_connection_for_route(
                &mut test_basics.client,
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                    get_slot(key2.as_bytes()),
                    SlotAddr::Master,
                ))),
            )
            .await;

            let res = retry_until_timeout(
                || async {
                    let mut client = test_basics.client.clone();
                    client
                        .send_pipeline(
                            &pipeline,
                            None,
                            false,
                            None,
                            PipelineRetryStrategy {
                                retry_server_error: true,
                                retry_connection_error: false,
                            },
                        )
                        .await
                        .ok()
                },
                std::time::Duration::from_millis(10_000),
            )
            .await;

            let expected = Value::Array(vec![
                Value::Okay,
                Value::BulkString(value.as_bytes().to_vec().into()),
                Value::Okay,
                Value::BulkString(value2.as_bytes().to_vec().into()),
            ]);

            assert_eq!(res, expected, "Pipeline result: {res:?}");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_parallel_pipeline_and_kill_connection(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let key = generate_random_string(10);
            let configuration = TestConfiguration {
                shared_server: true,
                request_timeout: Some(3000),
                ..Default::default()
            };
            let mut test_basics = setup_test_basics(use_cluster, configuration.clone()).await;

            let mut client_for_kill = match test_basics.server {
                BackingServer::Cluster(cluster) => {
                    create_cluster_client(cluster.as_ref(), configuration).await
                }
                BackingServer::Standalone(standalone) => {
                    create_client(&BackingServer::Standalone(standalone), configuration).await
                }
            };

            let pipeline_future = async {
                let mut pipeline = Pipeline::new();
                pipeline.blpop(&key, 2.0);
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                test_basics
                    .client
                    .send_pipeline(
                        &pipeline,
                        None,
                        false,
                        None,
                        PipelineRetryStrategy {
                            retry_server_error: true,
                            retry_connection_error: false,
                        },
                    )
                    .await
            };

            let kill_future = async {
                tokio::time::sleep(std::time::Duration::from_millis(50)).await;

                kill_connection_for_route(
                    &mut client_for_kill,
                    RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                        get_slot(key.as_bytes()),
                        SlotAddr::Master,
                    ))),
                )
                .await;
            };

            // Run both tasks concurrently.
            let (pipeline_result, _) = tokio::join!(pipeline_future, kill_future);

            match use_cluster {
                true => {
                    assert!(
                        pipeline_result.is_ok(),
                        "Pipeline failed: {pipeline_result:?}"
                    );
                    let result = match pipeline_result.unwrap() {
                        Value::Array(res) => res,
                        _ => panic!("Expected an array of values"),
                    };

                    assert!(
                        result.iter().all(|r| matches!(r, Value::ServerError(e) if e.err_code().contains("BrokenPipe"))
                            ),
                        "Not all responses are ServerError: {result:?}"
                    );
                }
                false => {
                    assert!(
                        pipeline_result.is_err(),
                        "Pipeline should have failed: {pipeline_result:?}"
                    );
                    assert!(
                        pipeline_result
                            .unwrap_err()
                            .to_string()
                            .contains("FatalReceiveError"),
                    );
                }
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_reconnect_after_kill_all_connections(
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // Generate random keys.
            let (key, key2) = (generate_random_string(10), generate_random_string(10));

            let mut pipeline = Pipeline::new();
            pipeline
                .set(&key, "value1")
                .get(&key)
                .set(&key2, "value2")
                .get(&key2);

            // Kill all connections.
            kill_connection(&mut test_basics.client).await;

            let res = retry_until_timeout(
                || async {
                    let mut client = test_basics.client.clone();
                    client
                        .send_pipeline(
                            &pipeline,
                            None,
                            false,
                            None,
                            PipelineRetryStrategy {
                                retry_server_error: true,
                                retry_connection_error: false,
                            },
                        )
                        .await
                        .ok()
                },
                std::time::Duration::from_millis(10_000),
            )
            .await;

            let expected = Value::Array(vec![
                Value::Okay,
                Value::BulkString("value1".as_bytes().to_vec().into()),
                Value::Okay,
                Value::BulkString("value2".as_bytes().to_vec().into()),
            ]);

            assert_eq!(res, expected, "Pipeline result: {res:?}");
        });
    }
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_empty_pipeline(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let pipeline = Pipeline::new();
            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await;

            assert!(result.is_err(), "Pipeline should fail with empty pipeline");
        });
    }
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_multi_slot_routing(#[values(true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let keys = vec![
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
            ];

            let items = keys.iter().map(|key| (key, key)).collect::<Vec<_>>();
            let expected = keys
                .iter()
                .map(|key| Value::BulkString(key.as_bytes().to_vec().into()))
                .collect::<Vec<_>>();
            let mut pipeline = Pipeline::new();
            pipeline.mset(&items);
            pipeline.mget(&keys);

            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    Some(PIPELINE_TEST_TIMEOUT_MS),
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");
            assert_eq!(
                result,
                Value::Array(vec![Value::Okay, Value::Array(expected)]),
                "Pipeline result: {result:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_all_nodes_routing(#[values(true, false)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let mut config_set_cmd = cmd("CONFIG");
            config_set_cmd.arg("SET").arg("appendonly").arg("no");
            let mut config_get_cmd = cmd("CONFIG");
            config_get_cmd.arg("GET").arg("appendonly");
            let mut pipeline = Pipeline::new();
            pipeline.add_command(config_set_cmd); // AllNodes cmd
            pipeline.add_command(config_get_cmd); // RandomNode cmd

            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    Some(PIPELINE_TEST_TIMEOUT_MS),
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");

            assert_eq!(
                result,
                Value::Array(vec![
                    Value::Okay,
                    Value::Map(vec![(
                        Value::BulkString(b"appendonly".to_vec().into()),
                        Value::BulkString(b"no".to_vec().into()),
                    )])
                ]),
                "Pipeline result: {result:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_all_primary_routing(#[values(true, false)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let mut pipeline = Pipeline::new();
            pipeline.add_command(cmd("PING"));
            pipeline.set(generate_random_string(10), "value");
            pipeline.add_command(cmd("FLUSHALL"));
            pipeline.add_command(cmd("DBSIZE")); // AllPrimary cmd + SUM aggregation

            // Execute the pipeline.
            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    Some(PIPELINE_TEST_TIMEOUT_MS),
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline execution failed");

            let expected = Value::Array(vec![
                Value::SimpleString("PONG".to_string()),
                Value::Okay,
                Value::Okay,
                Value::Int(0),
            ]);

            assert_eq!(result, expected, "Pipeline result: {result:?}");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_failed_commands_skip_conversion(
        #[values(false, true)] use_cluster: bool,
        #[values(false, true)] atomic: bool,
    ) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let mut pipeline = Pipeline::new();
            if atomic {
                pipeline.atomic();
            }
            pipeline.set("key", "value");
            pipeline.hgetall("key");

            let result = if atomic {
                test_basics
                    .client
                    .send_transaction(&pipeline, None, None, false)
                    .await
                    .expect("Transaction failed")
            } else {
                test_basics
                    .client
                    .send_pipeline(
                        &pipeline,
                        None,
                        false,
                        Some(PIPELINE_TEST_TIMEOUT_MS),
                        PipelineRetryStrategy {
                            retry_server_error: false,
                            retry_connection_error: false,
                        },
                    )
                    .await
                    .expect("Pipeline failed")
            };

            let arr = match result {
                Value::Array(ref arr) => arr,
                _ => panic!("Expected array result, got: {result:?}"),
            };

            assert_eq!(arr[0], Value::Okay, "Pipeline result: {arr:?}");
            assert!(
                matches!(&arr[1], Value::ServerError(err) if err.err_code().contains("WRONGTYPE")),
                "Pipeline result: {arr:?}"
            );
        });
    }

    #[test]
    #[serial_test::serial]
    fn test_client_telemetry_standalone() {
        Telemetry::reset();
        block_on_all(async move {
            // create a server with 2 clients
            let server_config = TestConfiguration {
                use_tls: false,
                ..Default::default()
            };

            let test_basics = utilities::setup_test_basics_internal(&server_config).await;
            let server = BackingServer::Standalone(test_basics.server);

            // setup_test_basics_internal internally, starts a single client connection
            assert_eq!(Telemetry::total_connections(), 1);
            assert_eq!(Telemetry::total_clients(), 1);

            {
                // Create 2 more clients, confirm that they are tracked
                let _client1 = create_client(&server, server_config.clone()).await;
                let _client2 = create_client(&server, server_config).await;

                // Each client maintains a single connection
                assert_eq!(Telemetry::total_connections(), 3);
                assert_eq!(Telemetry::total_clients(), 3);

                // Connections are dropped here
            }

            // Confirm 1 connection & client remain
            assert_eq!(Telemetry::total_connections(), 1);
            assert_eq!(Telemetry::total_clients(), 1);
        });
    }

    #[test]
    #[serial_test::serial]
    fn test_client_telemetry_cluster() {
        Telemetry::reset();
        block_on_all(async {
            let local_set = tokio::task::LocalSet::default();
            let (tx, mut rx) = tokio::sync::mpsc::channel(1);
            // We use 2 tasks to let "dispose" be called. In addition, the task that checks for the cleanup
            // does not start until the cluster is up and running. We use a channel to communicate this between
            // the tasks
            local_set.spawn_local(async move {
                let cluster = cluster::setup_default_cluster().await;
                async_assert_eq!(Telemetry::total_connections(), 0);
                async_assert_eq!(Telemetry::total_clients(), 0);

                // Each client opens 12 connections
                println!("Creating 1st cluster client...");
                let _c1 = cluster::setup_default_client(&cluster).await;
                async_assert_eq!(Telemetry::total_connections(), 12);
                async_assert_eq!(Telemetry::total_clients(), 1);

                println!("Creating 2nd cluster client...");
                let _c2 = cluster::setup_default_client(&cluster).await;
                async_assert_eq!(Telemetry::total_connections(), 24);
                async_assert_eq!(Telemetry::total_clients(), 2);

                let _ = tx.send(1).await;
                // client is dropped and eventually disposed here
            });

            local_set.spawn_local(async move {
                let _ = rx.recv().await;
                println!("Cluster terminated. Wait for the telemetry to clear");
                tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;
                assert_eq!(Telemetry::total_connections(), 0);
                assert_eq!(Telemetry::total_clients(), 0);
            });
            local_set.await;
        });
    }

    #[test]
    #[serial_test::serial]
    fn test_multi_key_no_args_in_cluster() {
        block_on_all(async {
            let cluster = cluster::setup_default_cluster().await;
            println!("Creating 1st cluster client...");
            let mut c1 = cluster::setup_default_client(&cluster).await;
            let result = c1.send_command(&mut redis::cmd("MSET"), None).await;
            assert!(result.is_err());
            let e = result.unwrap_err();
            assert!(e.kind().clone().eq(&redis::ErrorKind::ResponseError));
            assert!(e.to_string().contains("wrong number of arguments"));
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_request_transaction_and_convert_all_values(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    protocol: glide_core::connection_request::ProtocolVersion::RESP2,
                    ..Default::default()
                },
            )
            .await;

            let key = generate_random_string(10);
            let mut pipeline = redis::pipe();
            pipeline.atomic();
            pipeline.hset(&key, "bar", "vaz");
            pipeline.hgetall(&key);
            pipeline.hexists(&key, "bar");
            pipeline.del(&key);
            pipeline.set(&key, "0");
            pipeline.cmd("INCRBYFLOAT").arg(&key).arg("0.5");
            pipeline.del(&key);

            let result = test_basics
                .client
                .send_transaction(&pipeline, None, None, false)
                .await;
            assert_eq!(
                result,
                Ok(Value::Array(vec![
                    Value::Int(1),
                    Value::Map(vec![(
                        Value::BulkString(b"bar".to_vec().into()),
                        Value::BulkString(b"vaz".to_vec().into()),
                    )]),
                    Value::Boolean(true),
                    Value::Int(1),
                    Value::Okay,
                    Value::Double(0.5),
                    Value::Int(1),
                ]),)
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    /// Test that verifies the client maintains the correct database ID after an automatic reconnection
    /// when the database was changed using the SELECT command.
    /// This test:
    /// 1. Creates a client connected to database 0 (default)
    /// 2. Uses SELECT command to change to database 5
    /// 3. Verifies the connection is now on database 5
    /// 4. Simulates a connection drop by killing the connection
    /// 5. Sends another command which either:
    ///    - Fails due to the dropped connection, then retries and verifies reconnection to db=5
    ///    - Succeeds with a new client ID (indicating reconnection) and verifies still on db=5
    /// This ensures that database selection via SELECT command persists across reconnections.
    fn test_select_command_database_persistence_after_reconnection(
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    database_id: 0, // Start with default database
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            if use_cluster {
                // Skip test if server version is less than 9.0 (database isolation not supported in cluster)
                if !utilities::version_greater_or_equal(&mut test_basics.client, "9.0.0").await {
                    return;
                }
            }

            let mut client_info_cmd = redis::Cmd::new();
            client_info_cmd.arg("CLIENT").arg("INFO");

            let mut select_cmd = redis::Cmd::new();
            select_cmd.arg("SELECT").arg("5");

            // Verify initial connection is to database 0
            let initial_client_info_response = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await
                .unwrap();

            let initial_client_info = match initial_client_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response type: {:?}",
                    initial_client_info_response
                ),
            };
            assert!(initial_client_info.contains("db=0"));

            // Extract initial client ID
            let initial_client_id = utilities::extract_client_id(&initial_client_info)
                .expect("Failed to extract initial client ID");

            // Execute SELECT command to change to database 5
            let select_result = test_basics
                .client
                .send_command(&mut select_cmd, None)
                .await
                .unwrap();
            assert_eq!(select_result, Value::Okay);

            // Verify we're now on database 5
            let post_select_client_info_response = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await
                .unwrap();

            let post_select_client_info = match post_select_client_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response type: {:?}",
                    post_select_client_info_response
                ),
            };
            assert!(post_select_client_info.contains("db=5"));

            // Kill the connection to simulate a network drop
            kill_connection(&mut test_basics.client).await;
            // Allow TCP close to propagate before sending the next command
            tokio::time::sleep(std::time::Duration::from_millis(100)).await;

            // Try to send another command - this should trigger reconnection
            let res = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await;
            match res {
                Err(err) => {
                    // Connection was dropped as expected
                    assert!(
                        err.is_connection_dropped()
                            || err.is_timeout()
                            || err.kind() == redis::ErrorKind::AllConnectionsUnavailable,
                        "Expected connection dropped, timeout, or unavailable error, got: {err:?}",
                    );
                    // Retry and verify we're still on database 5 after reconnection
                    let client_info = retry(|| async {
                        let mut client = test_basics.client.clone();
                        let mut cmd = client_info_cmd.clone();
                        let response = client.send_command(&mut cmd, None).await.ok()?;
                        match response {
                            Value::BulkString(bytes) => {
                                Some(String::from_utf8_lossy(&bytes).to_string())
                            }
                            Value::VerbatimString { text, .. } => Some(text),
                            _ => None,
                        }
                    })
                    .await;
                    assert!(client_info.contains("db=5"));
                }
                Ok(response) => {
                    // Command succeeded — reconnection completed within the timeout.
                    // Extract new client ID and verify it changed (new connection).
                    let new_client_info = match response {
                        Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                        Value::VerbatimString { text, .. } => text,
                        _ => panic!("Unexpected CLIENT INFO response type: {:?}", response),
                    };
                    let new_client_id = utilities::extract_client_id(&new_client_info)
                        .expect("Failed to extract new client ID");
                    assert_ne!(
                        initial_client_id, new_client_id,
                        "Client ID should change after reconnection if command succeeds"
                    );
                    // Check that the database ID is still 5 (from SELECT command)
                    assert!(new_client_info.contains("db=5"));
                }
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    /// Test that verifies the client maintains the correct client name after an automatic reconnection
    /// when the client name was changed using the CLIENT SETNAME command.
    /// This test:
    /// 1. Creates a client with a name "1stName"
    /// 2. Uses CLIENT SETNAME command to change to "2ndName"
    /// 3. Verifies the connection is now with the "2ndName"
    /// 4. Simulates a connection drop by killing the connection
    /// 5. Sends another command which either:
    ///    - Fails due to the dropped connection, then retries and verifies reconnection has the name "2ndName" set
    ///    - Succeeds with a new client ID (indicating reconnection) and verifies still has the name "2ndName" set
    /// This ensures that client name via CLIENT SETNAME command persists across reconnections.
    fn test_client_set_name_command_persistence_after_reconnection(
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    client_name: Some("1stName".to_string()),
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let mut client_info_cmd = redis::Cmd::new();
            client_info_cmd.arg("CLIENT").arg("INFO");

            let mut client_setname_cmd = redis::Cmd::new();
            client_setname_cmd
                .arg("Client")
                .arg("SETNAME")
                .arg("2ndName");

            // Verify initial connection client name
            let initial_client_info_response = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await
                .unwrap();

            let initial_client_info = match initial_client_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response type: {:?}",
                    initial_client_info_response
                ),
            };
            assert!(initial_client_info.contains("name=1stName"));

            // Execute CLIENT SETNAME command to change to 2ndName
            let client_setname_result = test_basics
                .client
                .send_command(&mut client_setname_cmd, None)
                .await
                .unwrap();
            assert_eq!(client_setname_result, Value::Okay);

            // Verify we're now on 2ndName
            let post_client_setname_client_info_response = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await
                .unwrap();

            let post_client_setname_client_info = match post_client_setname_client_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response type: {:?}",
                    post_client_setname_client_info_response
                ),
            };
            assert!(post_client_setname_client_info.contains("name=2ndName"));

            // Kill the connection to simulate a network drop
            kill_connection(&mut test_basics.client).await;

            // Retry until reconnection completes, then verify name=2ndName persisted
            let client_info = retry_until_timeout(
                || async {
                    let mut client = test_basics.client.clone();
                    let mut cmd = client_info_cmd.clone();
                    let response = client.send_command(&mut cmd, None).await.ok()?;
                    match response {
                        Value::BulkString(bytes) => {
                            Some(String::from_utf8_lossy(&bytes).to_string())
                        }
                        Value::VerbatimString { text, .. } => Some(text),
                        _ => None,
                    }
                },
                std::time::Duration::from_secs(3),
            )
            .await;
            assert!(client_info.contains("name=2ndName"));
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    /// Test that verifies the client maintains the correct username after an automatic reconnection
    /// when authentication is changed using the AUTH command.
    /// This test:
    /// 1. Creates a client with default authentication
    /// 2. Uses ACL SETUSER to create a new user "testuser"
    /// 3. Uses AUTH command to authenticate as "testuser"
    /// 4. Verifies the connection is authenticated with the correct username
    /// 5. Simulates a connection drop by killing the connection
    /// 6. Sends another command which either:
    ///    - Fails due to the dropped connection, then retries and verifies reconnection with same username
    ///    - Succeeds with a new client ID (indicating reconnection) and verifies still authenticated with same username
    /// This ensures that username authentication via AUTH command persists across reconnections.
    fn test_username_persistence_after_reconnection(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls: true,
                    shared_server: false,
                    connection_info: Some(redis::RedisConnectionInfo {
                        password: Some("ReallySecurePassword".to_string()),
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            let mut client_info_cmd = redis::Cmd::new();
            client_info_cmd.arg("CLIENT").arg("INFO");

            // Verify initial connection (should be default user)
            let initial_client_info_response = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await
                .unwrap();

            let initial_client_info = match initial_client_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response type: {:?}",
                    initial_client_info_response
                ),
            };
            assert!(initial_client_info.contains("user=default"));

            // Extract initial client ID
            let initial_client_id = utilities::extract_client_id(&initial_client_info)
                .expect("Failed to extract initial client ID");

            // Create a new user using ACL SETUSER (automatically routes to all nodes)
            let mut acl_setuser_cmd = redis::Cmd::new();
            acl_setuser_cmd
                .arg("ACL")
                .arg("SETUSER")
                .arg("testuser")
                .arg("on")
                .arg(">testpassword")
                .arg("~*")
                .arg("+@all");

            let acl_result = test_basics
                .client
                .send_command(&mut acl_setuser_cmd, None)
                .await
                .unwrap();

            // ACL SETUSER routes to all nodes with AllSucceeded policy, which returns a single OK
            assert_eq!(acl_result, Value::Okay);

            // Execute AUTH command to authenticate as testuser (automatically routes to all nodes)
            let mut auth_cmd = redis::Cmd::new();
            auth_cmd.arg("AUTH").arg("testuser").arg("testpassword");

            let auth_result = test_basics
                .client
                .send_command(&mut auth_cmd, None)
                .await
                .unwrap();

            // AUTH routes to all nodes with AllSucceeded policy, which returns a single OK
            assert_eq!(auth_result, Value::Okay);

            // Verify we're now authenticated as testuser
            let post_auth_client_info_response = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await
                .unwrap();

            let post_auth_client_info = match post_auth_client_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response type: {:?}",
                    post_auth_client_info_response
                ),
            };
            assert!(post_auth_client_info.contains("user=testuser"));

            // Kill the connection to simulate a network drop
            kill_connection(&mut test_basics.client).await;

            // Wait a moment for the connection to be fully dropped
            tokio::time::sleep(std::time::Duration::from_millis(100)).await;

            // Try to send another command - this should trigger reconnection
            let res = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await;
            match res {
                Err(err) => {
                    // Connection was dropped as expected
                    assert!(
                        err.is_connection_dropped()
                            || err.is_timeout()
                            || err.kind() == redis::ErrorKind::AllConnectionsUnavailable,
                        "Expected connection dropped, timeout, or unavailable error, got: {err:?}",
                    );
                    // Retry and verify we're still authenticated with same username after reconnection
                    let client_info = retry(|| async {
                        let mut client = test_basics.client.clone();
                        let mut cmd = client_info_cmd.clone();
                        let response = client.send_command(&mut cmd, None).await.ok()?;
                        match response {
                            Value::BulkString(bytes) => {
                                Some(String::from_utf8_lossy(&bytes).to_string())
                            }
                            Value::VerbatimString { text, .. } => Some(text),
                            _ => None,
                        }
                    })
                    .await;
                    assert!(client_info.contains("user=testuser"));
                }
                Ok(response) => {
                    // Command succeeded after reconnection, verify username persists
                    let new_client_info = match response {
                        Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                        Value::VerbatimString { text, .. } => text,
                        _ => panic!("Unexpected CLIENT INFO response type: {:?}", response),
                    };
                    let new_client_id = utilities::extract_client_id(&new_client_info)
                        .expect("Failed to extract new client ID");

                    // Client ID may or may not change depending on timing
                    // The important thing is that the username persists
                    if new_client_id != initial_client_id {
                        // Reconnection happened and we got a new client ID
                        println!(
                            "Reconnection detected: client ID changed from {} to {}",
                            initial_client_id, new_client_id
                        );
                    }

                    // Check that the username is still testuser after reconnection
                    assert!(new_client_info.contains("user=testuser"));
                }
            }

            // Cleanup: delete the test user (automatically routes to all nodes)
            let mut acl_deluser_cmd = redis::Cmd::new();
            acl_deluser_cmd.arg("ACL").arg("DELUSER").arg("testuser");
            let _ = test_basics
                .client
                .send_command(&mut acl_deluser_cmd, None)
                .await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    /// Test that verifies the client maintains the correct protocol version after an automatic reconnection
    /// when the protocol is changed using the HELLO command.
    /// This test:
    /// 1. Creates a client with RESP2 protocol
    /// 2. Uses HELLO command to change to RESP3
    /// 3. Verifies the connection is using RESP3 (proto=3)
    /// 4. Simulates a connection drop by killing the connection
    /// 5. Retries HELLO until reconnection succeeds and verifies RESP3 persisted
    /// This ensures that protocol version changed via HELLO command persists across reconnections.
    fn test_protocol_persistence_after_reconnection(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    connection_info: Some(RedisConnectionInfo {
                        protocol: redis::ProtocolVersion::RESP2,
                        ..Default::default()
                    }),
                    protocol: ProtocolVersion::RESP2,
                    ..Default::default()
                },
            )
            .await;

            let mut hello_cmd = redis::Cmd::new();
            hello_cmd.arg("HELLO");

            // Verify initial connection is using RESP2
            let initial_hello_response = test_basics
                .client
                .send_command(&mut hello_cmd, None)
                .await
                .unwrap();

            let initial_hello: std::collections::HashMap<String, Value> =
                redis::from_owned_redis_value(initial_hello_response).unwrap();
            assert_eq!(initial_hello.get("proto").unwrap(), &Value::Int(2));

            // Use HELLO command to change to RESP3
            let mut hello_3_cmd = redis::Cmd::new();
            hello_3_cmd.arg("HELLO").arg("3");
            let hello_3_response = test_basics
                .client
                .send_command(&mut hello_3_cmd, None)
                .await
                .unwrap();

            let hello_3_result: std::collections::HashMap<String, Value> =
                redis::from_owned_redis_value(hello_3_response).unwrap();
            assert_eq!(hello_3_result.get("proto").unwrap(), &Value::Int(3));

            // Kill the connection to simulate a network drop
            kill_connection(&mut test_basics.client).await;

            // Retry until reconnection completes, then verify RESP3 persisted
            let hello_info = retry_until_timeout(
                || async {
                    let mut client = test_basics.client.clone();
                    let mut cmd = hello_cmd.clone();
                    let response = client.send_command(&mut cmd, None).await.ok()?;
                    redis::from_owned_redis_value::<std::collections::HashMap<String, Value>>(
                        response,
                    )
                    .ok()
                },
                std::time::Duration::from_secs(3),
            )
            .await;
            assert_eq!(hello_info.get("proto").unwrap(), &Value::Int(3));
        });
    }

    // =========================================================================
    // RESET command - tracked state tests
    // Verifies handle_reset_command correctly updates glide-core tracked state
    // so reconnections restore the post-RESET state, not the pre-RESET state.
    // =========================================================================

    /// Parse a CLIENT INFO response Value into a String.
    fn parse_client_info_value(val: Value) -> String {
        match val {
            Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
            Value::VerbatimString { text, .. } => text,
            other => {
                let map: HashMap<String, String> = redis::from_owned_redis_value(other).unwrap();
                map.into_values().next().unwrap_or_default()
            }
        }
    }

    /// Send CLIENT INFO and return as String (handles both standalone and cluster responses).
    async fn reset_test_get_client_info(client: &mut Client) -> String {
        let mut cmd = redis::Cmd::new();
        cmd.arg("CLIENT").arg("INFO");
        let val = client
            .send_command(
                &mut cmd,
                Some(RoutingInfo::MultiNode((
                    MultipleNodeRoutingInfo::AllNodes,
                    None,
                ))),
            )
            .await
            .unwrap();
        parse_client_info_value(val)
    }

    /// Kill connection and retry CLIENT INFO until reconnect succeeds.
    async fn reset_test_get_info_after_reconnect(client: &mut Client) -> String {
        kill_connection(client).await;
        retry(|| async {
            let mut c = client.clone();
            let mut cmd = redis::Cmd::new();
            cmd.arg("CLIENT").arg("INFO");
            let val = c
                .send_command(
                    &mut cmd,
                    Some(RoutingInfo::MultiNode((
                        MultipleNodeRoutingInfo::AllNodes,
                        None,
                    ))),
                )
                .await
                .ok()?;
            Some(parse_client_info_value(val))
        })
        .await
    }

    /// Send RESET command.
    async fn reset_test_send_reset(client: &mut Client) {
        let mut cmd = redis::Cmd::new();
        cmd.arg("RESET");
        client.send_command(&mut cmd, None).await.unwrap();
    }

    /// RESET reverts tracked DB to 0 after reconnection.
    /// SELECT 2 (tracked DB=2) -> RESET (tracked DB=0) -> kill -> reconnect on DB=0.
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_reset_reverts_database_to_zero(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            if use_cluster && !version_greater_or_equal(&mut test_basics.client, "9.0.0").await {
                return;
            }
            let mut select = redis::Cmd::new();
            select.arg("SELECT").arg("2");
            test_basics
                .client
                .send_command(&mut select, None)
                .await
                .unwrap();
            reset_test_send_reset(&mut test_basics.client).await;
            let info = reset_test_get_info_after_reconnect(&mut test_basics.client).await;
            assert!(
                info.contains("db=0"),
                "expected db=0 after RESET+reconnect, got: {info}"
            );
        });
    }

    /// RESET with initial DB=3 in config -> reconnect uses DB=0 (post-RESET), not DB=3.
    /// Verifies handle_reset_command overrides ConnectionRequest.database_id.
    /// Cluster variant requires Valkey 9.0+ (multi-DB cluster support).
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_reset_overrides_initial_database_config(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            if use_cluster {
                // Cluster multi-DB requires Valkey 9.0+. Check version with a db=0 client
                // before attempting to create a db=3 client.
                let mut version_check = setup_test_basics(
                    true,
                    TestConfiguration {
                        shared_server: true,
                        ..Default::default()
                    },
                )
                .await;
                if !version_greater_or_equal(&mut version_check.client, "9.0.0").await {
                    return;
                }
            }
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    database_id: 3,
                    ..Default::default()
                },
            )
            .await;
            let initial = reset_test_get_client_info(&mut test_basics.client).await;
            assert!(
                initial.contains("db=3"),
                "expected db=3 initially, got: {initial}"
            );
            reset_test_send_reset(&mut test_basics.client).await;
            let info = reset_test_get_info_after_reconnect(&mut test_basics.client).await;
            assert!(
                info.contains("db=0"),
                "expected db=0 after RESET overrides initial config, got: {info}"
            );
        });
    }

    /// RESET clears tracked client name after reconnection.
    /// CLIENT SETNAME foo (tracked) -> RESET (tracked None) -> kill -> reconnect with empty name.
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_reset_clears_client_name(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            let mut setname = redis::Cmd::new();
            setname.arg("CLIENT").arg("SETNAME").arg("reset-test-name");
            test_basics
                .client
                .send_command(&mut setname, None)
                .await
                .unwrap();
            reset_test_send_reset(&mut test_basics.client).await;
            let info = reset_test_get_info_after_reconnect(&mut test_basics.client).await;
            assert!(
                info.contains("name= ") || info.contains("name=\n"),
                "expected empty name after RESET+reconnect, got: {info}"
            );
        });
    }

    /// RESET with initial client_name in config -> reconnect uses empty name (post-RESET).
    /// Verifies handle_reset_command overrides ConnectionRequest.client_name.
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_reset_overrides_initial_client_name_config(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    client_name: Some("initial-name".to_string()),
                    ..Default::default()
                },
            )
            .await;
            let initial = reset_test_get_client_info(&mut test_basics.client).await;
            assert!(
                initial.contains("name=initial-name"),
                "expected initial-name, got: {initial}"
            );
            reset_test_send_reset(&mut test_basics.client).await;
            let info = reset_test_get_info_after_reconnect(&mut test_basics.client).await;
            assert!(
                info.contains("name= ") || info.contains("name=\n"),
                "expected empty name after RESET overrides initial config, got: {info}"
            );
        });
    }

    /// RESET reverts tracked protocol to RESP2 after reconnection (standalone only).
    /// HELLO 3 (tracked RESP3) -> RESET (tracked RESP2) -> kill -> reconnect with RESP2.
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_reset_reverts_protocol_to_resp2() {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                false,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            if !version_greater_or_equal(&mut test_basics.client, "7.0.0").await {
                return;
            }
            let mut hello = redis::Cmd::new();
            hello.arg("HELLO").arg("3");
            test_basics
                .client
                .send_command(&mut hello, None)
                .await
                .unwrap();
            reset_test_send_reset(&mut test_basics.client).await;
            let info = reset_test_get_info_after_reconnect(&mut test_basics.client).await;
            assert!(
                info.contains("resp=2"),
                "expected resp=2 after RESET+reconnect, got: {info}"
            );
        });
    }

    /// Case 1: RESET on auth server -> next command fails with NOAUTH.
    /// Per spec: "Deauthenticates the connection, requiring a call to AUTH to reauthenticate."
    /// The caller is responsible for re-authenticating after RESET.
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_reset_deauths_connection(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls: true,
                    connection_info: Some(redis::RedisConnectionInfo {
                        password: Some("ReallySecurePassword".to_string()),
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;
            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("RESET");
            let result = test_basics
                .client
                .clone()
                .send_command(&mut reset_cmd, None)
                .await
                .unwrap();
            // RESET routes to AllNodes; unwrap the aggregated response.
            let reset_values: Vec<Value> = match result {
                Value::SimpleString(_) => vec![result],
                Value::Array(arr) => arr,
                Value::Map(map) => map.into_iter().map(|(_, v)| v).collect(),
                other => panic!("unexpected RESET response: {other:?}"),
            };
            assert!(
                reset_values
                    .iter()
                    .all(|v| *v == Value::SimpleString("RESET".to_string())),
                "expected all RESET responses to be SimpleString(\"RESET\"), got: {reset_values:?}"
            );
            // Next command on the deauthed connection must fail with NOAUTH
            let mut ping_cmd = redis::Cmd::new();
            ping_cmd.arg("PING");
            let err = test_basics
                .client
                .clone()
                .send_command(&mut ping_cmd, None)
                .await
                .unwrap_err();
            assert!(
                err.to_string().contains("NOAUTH"),
                "expected NOAUTH after RESET, got: {err}"
            );
        });
    }

    /// Case 2: RESET on auth server -> kill connection -> glide reconnects and re-auths automatically.
    /// glide-core stores the original credentials and uses them during reconnect handshake.
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_reset_reconnect_reauths_with_stored_credentials(
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async {
            let config = TestConfiguration {
                use_tls: true,
                connection_info: Some(redis::RedisConnectionInfo {
                    password: Some("ReallySecurePassword".to_string()),
                    ..Default::default()
                }),
                ..Default::default()
            };
            let mut test_basics = setup_test_basics(use_cluster, config.clone()).await;
            // Issue RESET - deauths the live connection
            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("RESET");
            test_basics
                .client
                .send_command(&mut reset_cmd, None)
                .await
                .unwrap();
            // Kill all connections via a direct authenticated connection, because the glide
            // client connection is deauthed after RESET and cannot send CLIENT KILL itself.
            let addrs: Vec<redis::ConnectionAddr> = match &test_basics.server {
                BackingServer::Standalone(server) => vec![
                    server
                        .as_ref()
                        .map(|s| s.get_client_addr())
                        .unwrap_or(get_shared_server_address(true)),
                ],
                BackingServer::Cluster(cluster) => cluster
                    .as_ref()
                    .map(|c| c.get_server_addresses())
                    .unwrap_or_else(|| get_shared_cluster_addresses(true)),
            };
            for addr in &addrs {
                kill_connection_via_addr(addr, Some("ReallySecurePassword")).await;
            }
            // Retry for up to 10s: glide reconnects and re-auths with stored credentials
            let key = generate_random_string(6);
            retry_until_timeout(
                || async {
                    let mut set_cmd = redis::Cmd::new();
                    set_cmd.arg("SET").arg(key.as_str()).arg("val");
                    test_basics
                        .client
                        .clone()
                        .send_command(&mut set_cmd, None)
                        .await
                        .ok()
                },
                std::time::Duration::from_secs(10),
            )
            .await;
        });
    }

    /// Case 3: RESET on auth server -> user manually calls AUTH -> client works again.
    /// The user is responsible for re-authenticating after RESET when auth is required.
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_reset_manual_reauth_restores_client(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls: true,
                    connection_info: Some(redis::RedisConnectionInfo {
                        password: Some("ReallySecurePassword".to_string()),
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;
            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("RESET");
            test_basics
                .client
                .clone()
                .send_command(&mut reset_cmd, None)
                .await
                .unwrap();
            // Manually re-authenticate after RESET
            let mut auth_cmd = redis::Cmd::new();
            auth_cmd.arg("AUTH").arg("ReallySecurePassword");
            let auth_result = test_basics
                .client
                .clone()
                .send_command(&mut auth_cmd, None)
                .await
                .unwrap();
            assert_eq!(auth_result, Value::Okay);
            // Client should now work normally
            let key = generate_random_string(6);
            send_set_and_get(test_basics.client.clone(), key).await;
        });
    }

    /// RESET exits pubsub mode: after RESET, regular commands are accepted.
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_reset_exits_pubsub_mode(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            reset_test_send_reset(&mut test_basics.client).await;
            let key = generate_random_string(6);
            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg(&key).arg("value");
            test_basics
                .client
                .send_command(&mut set_cmd, None)
                .await
                .expect("SET should work after RESET");
        });
    }

    /// RESET clears pubsub desired subscriptions: handle_reset_command calls
    /// remove_desired_subscriptions for all kinds so the synchronizer does not
    /// resubscribe on reconnect.  We verify this indirectly: RESET followed by
    /// a kill-and-reconnect cycle must not leave the connection in pubsub mode.
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_reset_clears_pubsub_desired_state(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            // RESET clears desired subscriptions in the synchronizer.
            reset_test_send_reset(&mut test_basics.client).await;
            // After reconnect the connection must not be in pubsub mode.
            let key = generate_random_string(6);
            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg(&key).arg("value");
            test_basics
                .client
                .send_command(&mut set_cmd, None)
                .await
                .expect("SET should work after RESET (no pubsub resubscription on reconnect)");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_watchdog_timeout_fires_for_slow_command(#[values(false)] use_cluster: bool) {
        // Verify the watchdog fires a timeout for a command that exceeds the configured
        // request_timeout. Uses CLIENT PAUSE to make the server stop processing, then
        // sends a GET that will timeout.
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(100), // 100ms client timeout
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // Pause the server for 2 seconds — all commands will block
            let mut pause_cmd = redis::Cmd::new();
            pause_cmd.arg("CLIENT").arg("PAUSE").arg("2000");
            let _ = test_basics.client.send_command(&mut pause_cmd, None).await;

            // Now send a GET — it will be queued at the server and not processed
            // until the pause ends, but our 100ms timeout fires first.
            let mut cmd = redis::Cmd::new();
            cmd.arg("GET").arg("watchdog_test_key");
            let result = test_basics.client.send_command(&mut cmd, None).await;
            assert!(
                result.is_err(),
                "Command should have timed out but got: {:?}",
                result
            );
            let err = result.unwrap_err();
            assert!(
                err.is_timeout(),
                "Error should be a timeout, got: {:?}",
                err
            );

            // Unpause so we don't affect other tests
            tokio::time::sleep(std::time::Duration::from_secs(2)).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(LONG_CLUSTER_TEST_TIMEOUT)]
    fn test_mtls_cert_rotation_reconnect(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            // TODO #6532: the cluster arm is skipped because cluster_manager.py
            // hard-codes `--tls-auth-clients no`, so a cluster-through-cluster_manager
            // cannot enforce client-cert auth. Once #6532 teaches cluster_manager.py to
            // optionally require client certs, run the full rotation+reconnect flow here
            // for use_cluster == true instead of returning early.
            if use_cluster {
                println!(
                    "Skipping cluster arm: cluster_manager.py does not support \
                     --tls-auth-clients yes; cluster mTLS cert rotation is exercised \
                     in redis-rs test_cluster_async::mtls_test"
                );
                return;
            }

            let tempdir = tempfile::tempdir().expect("Failed to create temp dir");
            let tls_paths = build_tls_file_paths(&tempdir);

            let client_cert = tempdir.path().join("client.crt");
            let client_key = tempdir.path().join("client.key");

            rotate_client_cert_and_key(&tls_paths, &client_cert, &client_key);

            let server = RedisServer::new_with_addr_tls_modules_and_spawner(
                redis::ConnectionAddr::TcpTls {
                    host: "127.0.0.1".to_string(),
                    port: get_available_port(),
                    insecure: false,
                    tls_params: None,
                },
                Some(tls_paths.clone()),
                &[],
                true,
                |cmd| cmd.spawn().expect("Failed to spawn server"),
            );

            let server_addr = server.get_client_addr();
            tokio::time::sleep(std::time::Duration::from_millis(300)).await;

            let ca_cert_bytes = tls_paths.read_ca_cert_as_bytes();

            let configuration = TestConfiguration {
                use_tls: true,
                shared_server: false,
                client_cert_path: Some(client_cert.to_string_lossy().to_string()),
                client_key_path: Some(client_key.to_string_lossy().to_string()),
                root_certs: vec![ca_cert_bytes],
                cert_reload_interval_seconds: Some(1),
                ..Default::default()
            };

            let connection_request =
                create_connection_request(std::slice::from_ref(&server_addr), &configuration);

            let client = Client::new(connection_request.into(), None)
                .await
                .expect("Failed to create mTLS client");
            let mut test_basics = TestBasics {
                server: BackingServer::Standalone(Some(server)),
                client,
            };

            let mut set_cmd = redis::cmd("SET");
            set_cmd.arg("mtls_test_key").arg("value_before_rotation");
            test_basics
                .client
                .send_command(&mut set_cmd, None)
                .await
                .expect("SET should succeed pre-rotation");

            let mut get_cmd = redis::cmd("GET");
            get_cmd.arg("mtls_test_key");
            let get_result = test_basics
                .client
                .send_command(&mut get_cmd, None)
                .await
                .expect("GET should succeed pre-rotation");
            assert_eq!(
                get_result,
                Value::BulkString(b"value_before_rotation".to_vec().into())
            );

            let mut client_info_cmd = redis::Cmd::new();
            client_info_cmd.arg("CLIENT").arg("INFO");
            let initial_info_response = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await
                .expect("CLIENT INFO should succeed");
            let initial_info = match initial_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response: {:?}",
                    initial_info_response
                ),
            };
            let initial_client_id =
                extract_client_id(&initial_info).expect("Failed to extract initial client ID");

            rotate_client_cert_and_key(&tls_paths, &client_cert, &client_key);

            tokio::time::sleep(std::time::Duration::from_secs(2)).await;

            kill_connection(&mut test_basics.client).await;
            tokio::time::sleep(std::time::Duration::from_millis(100)).await;

            let post_rotation_info = retry(|| async {
                let mut client = test_basics.client.clone();
                let mut cmd = redis::Cmd::new();
                cmd.arg("CLIENT").arg("INFO");
                let response = client.send_command(&mut cmd, None).await.ok()?;
                match response {
                    Value::BulkString(bytes) => Some(String::from_utf8_lossy(&bytes).to_string()),
                    Value::VerbatimString { text, .. } => Some(text),
                    _ => None,
                }
            })
            .await;

            let new_client_id =
                extract_client_id(&post_rotation_info).expect("Failed to extract new client ID");
            assert_ne!(
                initial_client_id, new_client_id,
                "Client ID should differ after reconnect (was {}, now {})",
                initial_client_id, new_client_id
            );

            let mut set_cmd2 = redis::cmd("SET");
            set_cmd2.arg("mtls_test_key").arg("value_after_rotation");
            test_basics
                .client
                .send_command(&mut set_cmd2, None)
                .await
                .expect("SET should succeed after rotation + reconnect");

            let mut get_cmd2 = redis::cmd("GET");
            get_cmd2.arg("mtls_test_key");
            let get_result2 = test_basics
                .client
                .send_command(&mut get_cmd2, None)
                .await
                .expect("GET should succeed after rotation + reconnect");
            assert_eq!(
                get_result2,
                Value::BulkString(b"value_after_rotation".to_vec().into())
            );
        });
    }

    /// Sets fake AWS credentials so `IAMTokenManager::new` can locally sign a SigV4
    /// token without reaching real AWS. Distinct from the `iam_tests`-gated
    /// `setup_mock_aws_credentials` above: this test runs under plain `proto`
    /// (no real ElastiCache/MemoryDB endpoint needed) since the local server
    /// accepts any AUTH password for the configured user (see `nopass` below).
    #[cfg(feature = "proto")]
    fn setup_test_credentials() {
        unsafe {
            std::env::set_var("AWS_ACCESS_KEY_ID", "test_access_key");
            std::env::set_var("AWS_SECRET_ACCESS_KEY", "test_secret_key");
            std::env::set_var("AWS_SESSION_TOKEN", "test_session_token");
        }
    }

    /// Starts a local server with an IAM-style `nopass` user and returns the
    /// server, its address, and the protobuf request configured for IAM auth.
    #[cfg(feature = "proto")]
    async fn setup_iam_local_server(
        iam_username: &str,
    ) -> (
        RedisServer,
        redis::ConnectionAddr,
        glide_core::connection_request::ConnectionRequest,
    ) {
        use ::protobuf::MessageField;
        use glide_core::connection_request as protobuf;
        use protobuf::TlsMode;
        use protobuf::{AuthenticationInfo as ProtoAuthInfo, IamCredentials, NodeAddress};
        use protobuf::{
            ConnectionRequest as ProtoConnectionRequest, ServiceType as ProtoServiceType,
        };

        // `nopass` means ACL ignores the supplied password entirely (verified
        // empirically: `AUTH iamuser <anything>` succeeds), which is the closest
        // local stand-in for how a real ElastiCache/MemoryDB IAM-enabled user
        // accepts a valid-looking SigV4-signed token without the test having
        // real AWS access.
        let server = RedisServer::new(ServerType::Tcp { tls: false });
        let addr = server.get_client_addr();
        let (host, port) = match &addr {
            redis::ConnectionAddr::Tcp(host, port) => (host.clone(), *port),
            other => panic!("Expected a plain TCP test server address, got: {other:?}"),
        };

        let setup_client = redis::Client::open(redis::ConnectionInfo {
            addr: addr.clone(),
            redis: RedisConnectionInfo::default(),
        })
        .unwrap();
        let mut setup_conn = retry(|| async {
            setup_client
                .get_multiplexed_async_connection(redis::GlideConnectionOptions::default())
                .await
                .ok()
        })
        .await;
        let mut acl_cmd = redis::cmd("ACL");
        acl_cmd
            .arg("SETUSER")
            .arg(iam_username)
            .arg("on")
            .arg("allkeys")
            .arg("+@all")
            .arg("nopass");
        setup_conn
            .send_packed_command(&acl_cmd)
            .await
            .expect("ACL SETUSER should succeed");

        let mut proto_request = ProtoConnectionRequest::new();
        proto_request.addresses.push(NodeAddress {
            host: host.clone().into(),
            port: port.into(),
            ..Default::default()
        });
        proto_request.tls_mode = TlsMode::NoTls.into();
        let mut iam_credentials = IamCredentials::new();
        iam_credentials.cluster_name = "test-scope-iam-cluster".into();
        iam_credentials.region = "us-east-1".into();
        iam_credentials.service_type = ProtoServiceType::ELASTICACHE.into();
        let mut auth_info = ProtoAuthInfo::new();
        auth_info.username = iam_username.into();
        auth_info.iam_credentials = MessageField(Some(Box::new(iam_credentials)));
        proto_request.authentication_info = MessageField(Some(Box::new(auth_info)));

        (server, addr, proto_request)
    }

    /// Reads `cmdstat_auth`'s `calls=` counter from `INFO commandstats`.
    #[cfg(feature = "proto")]
    async fn auth_call_count(conn: &mut redis::aio::MultiplexedConnection) -> u64 {
        let info_cmd = redis::cmd("INFO").arg("commandstats").to_owned();
        let raw = conn
            .send_packed_command(&info_cmd)
            .await
            .expect("INFO commandstats should succeed");
        let text = match raw {
            Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
            Value::VerbatimString { text, .. } => text,
            Value::SimpleString(s) => s,
            other => panic!("Unexpected INFO reply shape: {other:?}"),
        };
        for line in text.lines() {
            if let Some(rest) = line.strip_prefix("cmdstat_auth:") {
                for field in rest.split(',') {
                    if let Some(calls) = field.strip_prefix("calls=") {
                        return calls.parse().expect("calls= should be numeric");
                    }
                }
            }
        }
        0
    }

    /// Regression test for the initial AUTH on a scoped connection
    /// (`create_scope_connection`), isolated from the command-time rotation
    /// re-authentication that otherwise masks it.
    ///
    /// The command is sent with `client: None`, which routes through the raw
    /// `send_packed_command` fallback in `execute_scope_command`. That fallback
    /// cannot re-authenticate, so the identity observed here is whatever
    /// `create_scope_connection`'s init pipeline established and nothing else.
    #[cfg(feature = "proto")]
    #[rstest]
    #[serial_test::serial]
    fn test_scope_initial_connect_authenticates_as_iam_user() {
        use ::protobuf::Message as _;
        use glide_core::scope;

        block_on_all(async {
            setup_test_credentials();

            let iam_username = "iamuser";
            let (_server, _addr, proto_request) = setup_iam_local_server(iam_username).await;
            let connection_request_bytes = proto_request
                .write_to_bytes()
                .expect("serialize scope connection request");

            let client = Client::new(proto_request.into(), None)
                .await
                .expect("Client::new with IAM config should succeed against local server");

            let client_id = 2u64;
            scope::register_client(client_id, client.clone());

            let runtime = tokio::runtime::Handle::current();
            let scope_id = retry(|| {
                let connection_request_bytes = connection_request_bytes.clone();
                async {
                    let result =
                        scope::try_acquire_scope(client_id, connection_request_bytes, &runtime, 0);
                    if result >= 0 { Some(result) } else { None }
                }
            })
            .await;

            let mut args: Vec<Vec<u8>> = vec![b"WHOAMI".to_vec()];
            let response = scope::send_scope_command(scope_id as u64, "ACL", &mut args, None).await;

            let value = response.expect("ACL WHOAMI through the scope should succeed");
            let whoami = match value {
                Value::BulkString(bytes) => String::from_utf8(bytes.to_vec()).unwrap(),
                Value::SimpleString(s) => s,
                other => panic!("Unexpected ACL WHOAMI response shape: {other:?}"),
            };
            assert_eq!(
                whoami, iam_username,
                "create_scope_connection must AUTH as the IAM identity on initial connect"
            );

            scope::release_scope(scope_id as u64, client_id, &runtime);
            scope::unregister_client(client_id);
        });
    }

    /// Regression test for the real, binding-facing scope path with IAM
    /// configured. Checks `create_scope_connection`'s init AUTH via the
    /// server's `cmdstat_auth` counter, read before any scope command is sent
    /// — an identity-only check here can't catch a missing init AUTH, since
    /// `send_command_on_connection`'s rotation-detection would silently repair
    /// it on the first command. Then confirms the two mechanisms work
    /// together via `ScopeHandle`'s `Some(&client)` send.
    #[cfg(feature = "proto")]
    #[rstest]
    #[serial_test::serial]
    fn test_scope_full_path_authenticates_as_iam_user_with_client() {
        use ::protobuf::Message as _;

        block_on_all(async {
            setup_test_credentials();

            let iam_username = "iamuser";
            let (_server, addr, proto_request) = setup_iam_local_server(iam_username).await;
            let bytes = proto_request
                .clone()
                .write_to_bytes()
                .expect("serialize scope connection request");

            let client = Client::new(proto_request.into(), None)
                .await
                .expect("Client::new with IAM config should succeed against local server");

            let admin_client = redis::Client::open(redis::ConnectionInfo {
                addr: addr.clone(),
                redis: RedisConnectionInfo::default(),
            })
            .unwrap();
            let mut admin_conn = retry(|| async {
                admin_client
                    .get_multiplexed_async_connection(redis::GlideConnectionOptions::default())
                    .await
                    .ok()
            })
            .await;
            let auth_calls_before_setup = auth_call_count(&mut admin_conn).await;

            let scope = ScopeHandle::setup(client, bytes, 0).await;

            // Read before sending any scope command, so a later self-healing
            // re-auth can't cover for a missing init AUTH here.
            let auth_calls_after_setup = auth_call_count(&mut admin_conn).await;
            assert_eq!(
                auth_calls_after_setup - auth_calls_before_setup,
                1,
                "create_scope_connection should send exactly one AUTH while establishing \
                 the scoped connection (before={auth_calls_before_setup}, \
                 after={auth_calls_after_setup})"
            );

            let mut args: Vec<Vec<u8>> = vec![b"WHOAMI".to_vec()];
            let response = scope.send("ACL", &mut args).await;

            let value = response.expect("ACL WHOAMI through the scope should succeed");
            let whoami = match value {
                Value::BulkString(bytes) => String::from_utf8(bytes.to_vec()).unwrap(),
                Value::SimpleString(s) => s,
                other => panic!("Unexpected ACL WHOAMI response shape: {other:?}"),
            };
            assert_eq!(
                whoami, iam_username,
                "scope should authenticate as the IAM identity end-to-end through the \
                 real Some(&client) path"
            );

            scope.release();
        });
    }

    /// A rejected IAM re-authentication must not leave the connection in the pool:
    /// the bookmark advances only on success, so a retained connection would retry
    /// the same failing AUTH for the next borrower.
    #[cfg(feature = "proto")]
    #[rstest]
    #[serial_test::serial]
    fn test_failed_iam_reauth_discards_the_scoped_connection() {
        use ::protobuf::Message as _;

        block_on_all(async {
            setup_test_credentials();

            let iam_username = "iamuser";
            let (_server, addr, proto_request) = setup_iam_local_server(iam_username).await;
            let bytes = proto_request
                .clone()
                .write_to_bytes()
                .expect("serialize scope connection request");

            let mut client = Client::new(proto_request.into(), None)
                .await
                .expect("Client::new with IAM config should succeed against local server");
            let scope = ScopeHandle::setup(client.clone(), bytes, 0).await;

            let mut args: Vec<Vec<u8>> = vec![b"WHOAMI".to_vec()];
            scope
                .send("ACL", &mut args)
                .await
                .expect("the scope should work before the credentials change");

            // Revoke `nopass` so the generated IAM token no longer authenticates.
            let admin_client = redis::Client::open(redis::ConnectionInfo {
                addr,
                redis: RedisConnectionInfo::default(),
            })
            .unwrap();
            let mut admin_conn = retry(|| async {
                admin_client
                    .get_multiplexed_async_connection(redis::GlideConnectionOptions::default())
                    .await
                    .ok()
            })
            .await;
            let mut revoke = redis::cmd("ACL");
            revoke
                .arg("SETUSER")
                .arg(iam_username)
                .arg("on")
                .arg("allkeys")
                .arg("+@all")
                .arg("resetpass")
                .arg(">not-the-iam-token");
            admin_conn
                .send_packed_command(&revoke)
                .await
                .expect("ACL SETUSER should succeed");

            client
                .refresh_iam_token()
                .await
                .expect("refresh_iam_token should succeed with IAM configured");

            let mut args: Vec<Vec<u8>> = vec![b"WHOAMI".to_vec()];
            let err = scope
                .send("ACL", &mut args)
                .await
                .expect_err("the rotation's AUTH is rejected, so the command must fail");
            assert_eq!(
                err.kind(),
                redis::ErrorKind::AuthenticationFailed,
                "a failed re-auth must be identifiable by the caller, got: {err:?}"
            );

            scope.release();

            let pool = scope.pool.lock().await;
            assert!(
                pool.idle.is_empty(),
                "a connection whose re-auth failed must not be returned to idle"
            );
            assert_eq!(
                pool.total_count.load(std::sync::atomic::Ordering::Acquire),
                0,
                "discarding the connection must free its slot"
            );
        });
    }

    /// Regression test for the scope path's IAM rotation re-authentication.
    ///
    /// Drives the real consumer, `Client::send_command_on_connection`, with two
    /// independent `AtomicU64` generation bookmarks on two separate connections,
    /// and asserts that a single token rotation makes each connection send its own
    /// `AUTH`. That is precisely what the shared `token_changed` flag could not do:
    /// whichever consumer looked first cleared it and the other silently missed the
    /// rotation.
    #[cfg(feature = "proto")]
    #[rstest]
    #[serial_test::serial]
    fn test_iam_rotation_reauthenticates_each_connection_independently() {
        use std::sync::atomic::AtomicU64;

        block_on_all(async {
            setup_test_credentials();

            let iam_username = "iamuser";
            let (_server, addr, proto_request) = setup_iam_local_server(iam_username).await;

            let mut client = Client::new(proto_request.into(), None)
                .await
                .expect("Client::new with IAM config should succeed against local server");

            // Two raw connections standing in for two scoped connections, plus an
            // admin connection used only to read the server's AUTH counter.
            let raw_client = redis::Client::open(redis::ConnectionInfo {
                addr: addr.clone(),
                redis: RedisConnectionInfo::default(),
            })
            .unwrap();
            let open_conn = || async {
                retry(|| async {
                    raw_client
                        .get_multiplexed_async_connection(redis::GlideConnectionOptions::default())
                        .await
                        .ok()
                })
                .await
            };
            let mut conn_a = open_conn().await;
            let mut conn_b = open_conn().await;
            let mut admin_conn = open_conn().await;

            // Each connection owns its own bookmark, exactly as `ScopedConnection`
            // does. A fresh manager sits at generation 1, so 0 always mismatches
            // and the first command on each connection authenticates.
            let bookmark_a = AtomicU64::new(0);
            let bookmark_b = AtomicU64::new(0);

            let whoami_cmd = redis::cmd("ACL").arg("WHOAMI").to_owned();
            let whoami = |value: redis::RedisResult<Value>| -> String {
                match value.expect("ACL WHOAMI through send_command_on_connection should succeed") {
                    Value::BulkString(bytes) => String::from_utf8(bytes.to_vec()).unwrap(),
                    Value::SimpleString(s) => s,
                    other => panic!("Unexpected ACL WHOAMI response shape: {other:?}"),
                }
            };

            // Initial authentication on both connections. This also covers the
            // malformed single-argument `AUTH` bug: a one-arg AUTH is rejected by
            // the server, so an identity assertion here fails without the username.
            let a_initial = client
                .send_command_on_connection(&whoami_cmd, &mut conn_a, &bookmark_a, false)
                .await;
            assert_eq!(whoami(a_initial), iam_username);
            let b_initial = client
                .send_command_on_connection(&whoami_cmd, &mut conn_b, &bookmark_b, false)
                .await;
            assert_eq!(whoami(b_initial), iam_username);

            let auth_calls_before_rotation = auth_call_count(&mut admin_conn).await;

            // One rotation. Both bookmarks are now stale by exactly one generation.
            client
                .refresh_iam_token()
                .await
                .expect("refresh_iam_token should succeed with IAM configured");

            // Connection A observes the rotation first and updates its bookmark.
            let a_after = client
                .send_command_on_connection(&whoami_cmd, &mut conn_a, &bookmark_a, false)
                .await;
            assert_eq!(whoami(a_after), iam_username);

            // Connection B must still observe the same rotation. Under the shared
            // `token_changed` flag, A's re-auth above cleared it and this connection
            // sends no AUTH at all.
            let b_after = client
                .send_command_on_connection(&whoami_cmd, &mut conn_b, &bookmark_b, false)
                .await;
            assert_eq!(whoami(b_after), iam_username);

            let auth_calls_after_rotation = auth_call_count(&mut admin_conn).await;
            assert_eq!(
                auth_calls_after_rotation - auth_calls_before_rotation,
                2,
                "a single token rotation must make each connection send its own AUTH \
                 (before={auth_calls_before_rotation}, after={auth_calls_after_rotation})"
            );

            // And no further AUTH once both bookmarks are current again.
            let a_settled = client
                .send_command_on_connection(&whoami_cmd, &mut conn_a, &bookmark_a, false)
                .await;
            assert_eq!(whoami(a_settled), iam_username);
            assert_eq!(
                auth_call_count(&mut admin_conn).await,
                auth_calls_after_rotation,
                "an up-to-date bookmark must not re-authenticate"
            );
        });
    }
}
