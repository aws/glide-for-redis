/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */
mod utilities;

#[cfg(test)]
pub(crate) mod shared_client_tests {
    use super::*;
    use glide_core::client::Client;
    use redis::{
        cluster_routing::{MultipleNodeRoutingInfo, RoutingInfo},
        FromRedisValue, InfoDict, RedisConnectionInfo, Value,
    };
    use rstest::rstest;
    use utilities::cluster::*;
    use utilities::BackingServer;
    use utilities::*;

    struct TestBasics {
        server: BackingServer,
        client: Client,
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

            let connection_addr = test_basics
                .server
                .as_ref()
                .map(|server| server.get_client_addr())
                .unwrap_or(get_shared_server_address(configuration.use_tls));

            // TODO - this is a patch, handling the situation where the new server
            // still isn't available to connection. This should be fixed in [RedisServer].
            let client = repeat_try_create(|| async {
                Client::new(
                    create_connection_request(&[connection_addr.clone()], &configuration).into(),
                )
                .await
                .ok()
            })
            .await;

            TestBasics {
                server: BackingServer::Standalone(test_basics.server),
                client,
            }
        }
    }

    #[rstest]
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
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_is_not_routed() {
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
                    &cmd,
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
                    .send_transaction(&pipe, None)
                    .await
                    .unwrap();
            }

            // Gather info from each server
            let mut cmd = redis::cmd("INFO");
            cmd.arg("commandstats");
            let values = test_basics
                .client
                .send_command(
                    &cmd,
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
                    ..Default::default()
                },
            )
            .await;
            let hello: std::collections::HashMap<String, Value> = redis::from_owned_redis_value(
                test_basics
                    .client
                    .send_command(&redis::cmd("HELLO"), None)
                    .await
                    .unwrap(),
            )
            .unwrap();
            assert_eq!(hello.get("proto").unwrap(), &Value::Int(protocol));

            let mut cmd = redis::cmd("HSET");
            cmd.arg("hash").arg("foo").arg("baz");
            test_basics.client.send_command(&cmd, None).await.unwrap();
            let mut cmd = redis::cmd("HSET");
            cmd.arg("hash").arg("bar").arg("foobar");
            test_basics.client.send_command(&cmd, None).await.unwrap();

            let mut cmd = redis::cmd("HGETALL");
            cmd.arg("hash");
            let result = test_basics.client.send_command(&cmd, None).await.unwrap();

            assert_eq!(
                result,
                Value::Map(vec![
                    (
                        Value::BulkString("foo".as_bytes().to_vec()),
                        Value::BulkString("baz".as_bytes().to_vec())
                    ),
                    (
                        Value::BulkString("bar".as_bytes().to_vec()),
                        Value::BulkString("foobar".as_bytes().to_vec())
                    )
                ])
            );
        });
    }

    #[rstest]
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
                    || get_result.kind() == redis::ErrorKind::ClusterConnectionNotFound
            );
        });
    }

    #[rstest]
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

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_request_timeout(#[values(false, true)] use_cluster: bool) {
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

            let mut cmd = redis::Cmd::new();
            cmd.arg("BLPOP").arg("foo").arg(0); // 0 timeout blocks indefinitely
            let result = test_basics.client.send_command(&cmd, None).await;
            assert!(result.is_err());
            let err = result.unwrap_err();
            assert!(err.is_timeout(), "{err}");
        });
    }

    #[rstest]
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
                    &cmd,
                    Some(RoutingInfo::MultiNode((
                        MultipleNodeRoutingInfo::AllNodes,
                        None,
                    ))),
                )
                .await;

            let mut pipeline = redis::pipe();
            pipeline.atomic();
            pipeline.cmd("GET").arg("foo");
            let result = test_basics.client.send_transaction(&pipeline, None).await;
            assert!(result.is_err(), "Received {:?}", result);
            let err = result.unwrap_err();
            assert!(err.is_timeout(), "{err}");
        });
    }

    #[rstest]
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
            let mut client = test_basics.client;
            let client_info: String = redis::from_owned_redis_value(
                client.send_command(&client_info_cmd, None).await.unwrap(),
            )
            .unwrap();
            assert!(client_info.contains(&format!("name={CLIENT_NAME}")));

            kill_connection(&mut client).await;

            let error = client.send_command(&client_info_cmd, None).await;
            // In Standalone mode the error is passed back to the client,
            // while in Cluster mode the request is retried with reconnect
            if !use_cluster {
                assert!(error.is_err(), "{error:?}",);
                let error = error.unwrap_err();
                assert!(
                    error.is_connection_dropped() || error.is_timeout(),
                    "{error:?}",
                );
            }
            let client_info: String = repeat_try_create(|| async {
                let mut client = client.clone();
                redis::from_owned_redis_value(
                    client.send_command(&client_info_cmd, None).await.unwrap(),
                )
                .ok()
            })
            .await;

            assert!(client_info.contains(&format!("name={CLIENT_NAME}")));
        });
    }

    #[rstest]
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

            let result = test_basics.client.send_transaction(&pipeline, None).await;
            assert_eq!(
                result,
                Ok(Value::Array(vec![
                    Value::Int(1),
                    Value::Map(vec![(
                        Value::BulkString(b"bar".to_vec()),
                        Value::BulkString(b"vaz".to_vec()),
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
}
