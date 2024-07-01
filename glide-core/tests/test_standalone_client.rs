/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
mod utilities;

#[cfg(test)]
mod standalone_client_tests {
    use std::collections::HashMap;

    use crate::utilities::mocks::{Mock, ServerMock};

    use super::*;
    use glide_core::{
        client::{ConnectionError, StandaloneClient},
        connection_request::ReadFrom,
    };
    use redis::{FromRedisValue, Value};
    use rstest::rstest;
    use utilities::*;

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_report_disconnect_and_reconnect_after_temporary_disconnect(
        #[values(false, true)] use_tls: bool,
    ) {
        block_on_all(async move {
            let test_basics = setup_test_basics_internal(&TestConfiguration {
                use_tls,
                shared_server: true,
                ..Default::default()
            })
            .await;
            let mut client = test_basics.client;

            kill_connection(&mut client).await;

            let mut get_command = redis::Cmd::new();
            get_command
                .arg("GET")
                .arg("test_report_disconnect_and_reconnect_after_temporary_disconnect");
            let error = client.send_command(&get_command).await;
            assert!(error.is_err(), "{error:?}",);
            let error = error.unwrap_err();
            assert!(
                error.is_connection_dropped() || error.is_timeout(),
                "{error:?}",
            );

            let get_result = repeat_try_create(|| async {
                let mut client = client.clone();
                client.send_command(&get_command).await.ok()
            })
            .await;
            assert_eq!(get_result, Value::Nil);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(LONG_STANDALONE_TEST_TIMEOUT)]
    #[cfg(standalone_heartbeat)]
    fn test_detect_disconnect_and_reconnect_using_heartbeat(#[values(false, true)] use_tls: bool) {
        let (sender, receiver) = tokio::sync::oneshot::channel();
        block_on_all(async move {
            let mut test_basics = setup_test_basics(use_tls).await;
            let server = test_basics.server;
            let address = server.get_client_addr();
            drop(server);

            // we use another thread, so that the creation of the server won't block the client work.
            std::thread::spawn(move || {
                block_on_all(async move {
                    let new_server = RedisServer::new_with_addr_and_modules(address.clone(), &[]);
                    wait_for_server_to_become_ready(&address).await;
                    let _ = sender.send(new_server);
                })
            });

            let _new_server = receiver.await;
            tokio::time::sleep(
                glide_core::client::HEARTBEAT_SLEEP_DURATION + Duration::from_secs(1),
            )
            .await;

            let mut get_command = redis::Cmd::new();
            get_command
                .arg("GET")
                .arg("test_detect_disconnect_and_reconnect_using_heartbeat");
            let get_result = test_basics.client.send_command(&get_command).await.unwrap();
            assert_eq!(get_result, Value::Nil);
        });
    }

    fn get_mock_addresses(mocks: &[ServerMock]) -> Vec<redis::ConnectionAddr> {
        mocks.iter().flat_map(|mock| mock.get_addresses()).collect()
    }

    fn create_primary_responses() -> HashMap<String, Value> {
        let mut primary_responses = std::collections::HashMap::new();
        primary_responses.insert(
            "*1\r\n$4\r\nPING\r\n".to_string(),
            Value::BulkString(b"PONG".to_vec()),
        );
        primary_responses.insert(
            "*2\r\n$4\r\nINFO\r\n$11\r\nREPLICATION\r\n".to_string(),
            Value::BulkString(b"role:master\r\nconnected_slaves:3\r\n".to_vec()),
        );
        primary_responses
    }

    fn create_replica_response() -> HashMap<String, Value> {
        let mut replica_responses = std::collections::HashMap::new();
        replica_responses.insert(
            "*1\r\n$4\r\nPING\r\n".to_string(),
            Value::BulkString(b"PONG".to_vec()),
        );
        replica_responses.insert(
            "*2\r\n$4\r\nINFO\r\n$11\r\nREPLICATION\r\n".to_string(),
            Value::BulkString(b"role:slave\r\n".to_vec()),
        );
        replica_responses
    }
    fn create_primary_conflict_mock_two_primaries_one_replica() -> Vec<ServerMock> {
        let mut listeners: Vec<std::net::TcpListener> =
            (0..3).map(|_| get_listener_on_available_port()).collect();
        let primary_1 =
            ServerMock::new_with_listener(create_primary_responses(), listeners.pop().unwrap());
        let primary_2 =
            ServerMock::new_with_listener(create_primary_responses(), listeners.pop().unwrap());
        let replica =
            ServerMock::new_with_listener(create_replica_response(), listeners.pop().unwrap());
        vec![primary_1, primary_2, replica]
    }

    fn create_primary_mock_with_replicas(replica_count: usize) -> Vec<ServerMock> {
        let mut listeners: Vec<std::net::TcpListener> = (0..replica_count + 1)
            .map(|_| get_listener_on_available_port())
            .collect();
        let primary =
            ServerMock::new_with_listener(create_primary_responses(), listeners.pop().unwrap());
        let mut mocks = vec![primary];

        mocks.extend(
            listeners
                .into_iter()
                .map(|listener| ServerMock::new_with_listener(create_replica_response(), listener)),
        );
        mocks
    }

    struct ReadFromReplicaTestConfig {
        read_from: ReadFrom,
        expected_primary_reads: u16,
        expected_replica_reads: Vec<u16>,
        number_of_initial_replicas: usize,
        number_of_missing_replicas: usize,
        number_of_replicas_dropped_after_connection: usize,
        number_of_requests_sent: usize,
    }

    impl Default for ReadFromReplicaTestConfig {
        fn default() -> Self {
            Self {
                read_from: ReadFrom::Primary,
                expected_primary_reads: 3,
                expected_replica_reads: vec![0, 0, 0],
                number_of_initial_replicas: 3,
                number_of_missing_replicas: 0,
                number_of_replicas_dropped_after_connection: 0,
                number_of_requests_sent: 3,
            }
        }
    }

    fn test_read_from_replica(config: ReadFromReplicaTestConfig) {
        let mut mocks = create_primary_mock_with_replicas(
            config.number_of_initial_replicas - config.number_of_missing_replicas,
        );
        let mut cmd = redis::cmd("GET");
        cmd.arg("foo");

        for mock in mocks.iter() {
            for _ in 0..3 {
                mock.add_response(&cmd, "$-1\r\n".to_string());
            }
        }

        let mut addresses = get_mock_addresses(&mocks);

        for i in 4 - config.number_of_missing_replicas..4 {
            addresses.push(redis::ConnectionAddr::Tcp(
                "foo".to_string(),
                6379 + i as u16,
            ));
        }
        let mut connection_request =
            create_connection_request(addresses.as_slice(), &Default::default());
        connection_request.read_from = config.read_from.into();

        block_on_all(async {
            let mut client = StandaloneClient::create_client(connection_request.into(), None)
                .await
                .unwrap();
            for mock in mocks.drain(1..config.number_of_replicas_dropped_after_connection + 1) {
                mock.close().await;
            }
            for _ in 0..config.number_of_requests_sent {
                let _ = client.send_command(&cmd).await;
            }
        });

        assert_eq!(
            mocks[0].get_number_of_received_commands(),
            config.expected_primary_reads
        );
        let mut replica_reads: Vec<_> = mocks
            .iter()
            .skip(1)
            .map(|mock| mock.get_number_of_received_commands())
            .collect();
        replica_reads.sort();
        assert_eq!(config.expected_replica_reads, replica_reads);
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_always_read_from_primary() {
        test_read_from_replica(ReadFromReplicaTestConfig::default());
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::PreferReplica,
            expected_primary_reads: 0,
            expected_replica_reads: vec![1, 1, 1],
            ..Default::default()
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_skip_disconnected_replicas() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::PreferReplica,
            expected_primary_reads: 0,
            expected_replica_reads: vec![1, 2],
            number_of_missing_replicas: 1,
            ..Default::default()
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_read_from_primary_if_no_replica_is_connected() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::PreferReplica,
            expected_primary_reads: 3,
            expected_replica_reads: vec![],
            number_of_missing_replicas: 3,
            ..Default::default()
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_do_not_read_from_disconnected_replica() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::PreferReplica,
            expected_primary_reads: 0,
            expected_replica_reads: vec![2, 3],
            number_of_replicas_dropped_after_connection: 1,
            number_of_requests_sent: 6,
            ..Default::default()
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_with_single_replica() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::PreferReplica,
            expected_primary_reads: 0,
            expected_replica_reads: vec![3],
            number_of_initial_replicas: 1,
            number_of_requests_sent: 3,
            ..Default::default()
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_primary_conflict_raises_error() {
        let mocks = create_primary_conflict_mock_two_primaries_one_replica();
        let addresses = get_mock_addresses(&mocks);
        let connection_request =
            create_connection_request(addresses.as_slice(), &Default::default());
        block_on_all(async {
            let client_res = StandaloneClient::create_client(connection_request.into(), None)
                .await
                .map_err(ConnectionError::Standalone);
            assert!(client_res.is_err());
            let error = client_res.unwrap_err();
            assert!(matches!(error, ConnectionError::Standalone(_),));
            let primary_1_addr = addresses.first().unwrap().to_string();
            let primary_2_addr = addresses.get(1).unwrap().to_string();
            let replica_addr = addresses.get(2).unwrap().to_string();
            let err_msg = error.to_string().to_ascii_lowercase();
            assert!(
                err_msg.contains("conflict")
                    && err_msg.contains(&primary_1_addr)
                    && err_msg.contains(&primary_2_addr)
                    && !err_msg.contains(&replica_addr)
            );
        });
    }

    #[rstest]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_send_acl_request_to_all_nodes() {
        let mocks = create_primary_mock_with_replicas(2);
        let mut cmd = redis::cmd("ACL");
        cmd.arg("SETUSER").arg("foo");

        for mock in mocks.iter() {
            for _ in 0..3 {
                mock.add_response(&cmd, "+OK\r\n".to_string());
            }
        }

        let addresses: Vec<redis::ConnectionAddr> =
            mocks.iter().flat_map(|mock| mock.get_addresses()).collect();

        let connection_request =
            create_connection_request(addresses.as_slice(), &Default::default());

        block_on_all(async {
            let mut client = StandaloneClient::create_client(connection_request.into(), None)
                .await
                .unwrap();

            let result = client.send_command(&cmd).await;
            assert_eq!(result, Ok(Value::Okay));
        });

        for mock in mocks {
            assert_eq!(mock.get_number_of_received_commands(), 1);
        }
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_set_database_id_after_reconnection() {
        let mut client_info_cmd = redis::Cmd::new();
        client_info_cmd.arg("CLIENT").arg("INFO");
        block_on_all(async move {
            let test_basics = setup_test_basics_internal(&TestConfiguration {
                database_id: 4,
                shared_server: true,
                ..Default::default()
            })
            .await;
            let mut client = test_basics.client;

            let client_info: String = String::from_owned_redis_value(
                client.send_command(&client_info_cmd).await.unwrap(),
            )
            .unwrap();
            assert!(client_info.contains("db=4"));

            kill_connection(&mut client).await;

            let error = client.send_command(&client_info_cmd).await;
            assert!(error.is_err(), "{error:?}",);
            let error = error.unwrap_err();
            assert!(
                error.is_connection_dropped() || error.is_timeout(),
                "{error:?}",
            );

            let client_info = repeat_try_create(|| async {
                let mut client = client.clone();
                String::from_owned_redis_value(client.send_command(&client_info_cmd).await.unwrap())
                    .ok()
            })
            .await;
            assert!(client_info.contains("db=4"));
        });
    }
}
