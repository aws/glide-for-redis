/*
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

#![cfg(feature = "socket-layer")]
use glide_core::*;
use rsevents::{Awaitable, EventState, ManualResetEvent};
use std::io::prelude::*;
use std::sync::{Arc, Mutex};
use std::{os::unix::net::UnixStream, thread};
mod utilities;
use integer_encoding::VarInt;
use utilities::cluster::*;
use utilities::*;

/// Response header length approximation, including the length of the message and the callback index
const APPROX_RESP_HEADER_LEN: usize = 3;
const KEY_LENGTH: usize = 6;

#[cfg(test)]
mod socket_listener {
    use crate::utilities::mocks::{Mock, ServerMock};

    use super::*;
    use glide_core::redis_request::command::{Args, ArgsArray};
    use glide_core::redis_request::{Command, Transaction};
    use glide_core::response::{response, ConstantResponse, Response};
    use glide_core::scripts_container::add_script;
    use protobuf::{EnumOrUnknown, Message};
    use redis::{Cmd, ConnectionAddr, FromRedisValue, Value};
    use redis_request::{RedisRequest, RequestType};
    use rstest::rstest;
    use std::mem::size_of;
    use tokio::{net::UnixListener, runtime::Builder};

    /// An enum representing the values of the request type field for testing purposes
    #[derive(PartialEq, Eq, Debug)]
    pub enum ResponseType {
        /// Type of a response that returns a null.
        Null = 0,
        /// Type of a response that returns a redis value, and not an error.
        Value = 1,
        /// Type of response containing an error that impacts a single request.
        RequestError = 2,
        /// Type of response containing an error causes the connection to close.
        ClosingError = 3,
    }

    enum RedisType {
        Cluster,
        Standalone,
    }

    #[derive(Copy, Clone)]
    enum Tls {
        NoTls,
        UseTls,
    }

    impl Tls {
        fn to_bool(self) -> bool {
            matches!(self, Tls::UseTls)
        }
    }

    enum TestServer {
        Shared,
        Unique,
    }

    struct ServerTestBasics {
        server: Option<RedisServer>,
        socket: UnixStream,
    }

    struct ServerTestBasicsWithMock {
        server_mock: ServerMock,
        socket: UnixStream,
    }

    struct ClusterTestBasics {
        _cluster: Option<RedisCluster>,
        socket: UnixStream,
    }

    struct TestBasics {
        server: BackingServer,
        socket: UnixStream,
    }

    struct CommandComponents {
        args: Vec<bytes::Bytes>,
        request_type: EnumOrUnknown<RequestType>,
        args_pointer: bool,
    }

    fn pointer_to_value(pointer: u64) -> Box<Value> {
        let pointer = pointer as *mut Value;
        unsafe { Box::from_raw(pointer) }
    }

    fn assert_value(pointer: u64, expected_value: Option<Value>) {
        let received_value = pointer_to_value(pointer);
        assert!(expected_value.is_some());
        assert_eq!(*received_value, expected_value.unwrap());
    }

    fn decode_response(buffer: &[u8], cursor: usize, message_length: usize) -> Response {
        let header_end = cursor;
        match Response::parse_from_bytes(&buffer[header_end..header_end + message_length]) {
            Ok(res) => res,
            Err(err) => {
                panic!(
                    "Error decoding protocol message\r\n|── Protobuf error was: {:?}",
                    err.to_string()
                );
            }
        }
    }

    fn get_response(buffer: &mut Vec<u8>, socket: Option<&mut UnixStream>) -> Response {
        if let Some(socket) = socket {
            let _size = read_from_socket(buffer, socket);
        }
        let (message_length, header_bytes) = parse_header(buffer);
        decode_response(buffer, header_bytes, message_length as usize)
    }

    fn assert_null_response(buffer: &mut Vec<u8>, socket: &mut UnixStream, expected_callback: u32) {
        assert_response(
            buffer,
            Some(socket),
            expected_callback,
            None,
            ResponseType::Null,
        );
    }

    fn assert_ok_response(buffer: &mut Vec<u8>, socket: &mut UnixStream, expected_callback: u32) {
        assert_response(
            buffer,
            Some(socket),
            expected_callback,
            Some(Value::Okay),
            ResponseType::Value,
        );
    }

    fn assert_error_response(
        buffer: &mut Vec<u8>,
        socket: &mut UnixStream,
        expected_callback: u32,
        error_type: ResponseType,
    ) -> Response {
        assert_response(buffer, Some(socket), expected_callback, None, error_type)
    }

    fn assert_value_response(
        buffer: &mut Vec<u8>,
        socket: Option<&mut UnixStream>,
        expected_callback: u32,
        value: Value,
    ) -> Response {
        assert_response(
            buffer,
            socket,
            expected_callback,
            Some(value),
            ResponseType::Value,
        )
    }

    fn read_from_socket(buffer: &mut Vec<u8>, socket: &mut UnixStream) -> usize {
        buffer.resize(100, 0_u8);
        socket.read(buffer).unwrap()
    }

    fn assert_response(
        buffer: &mut Vec<u8>,
        socket: Option<&mut UnixStream>,
        expected_callback: u32,
        expected_value: Option<Value>,
        expected_response_type: ResponseType,
    ) -> Response {
        let response = get_response(buffer, socket);
        assert_eq!(response.callback_idx, expected_callback);
        match response.value {
            Some(response::Value::RespPointer(pointer)) => {
                assert_value(pointer, expected_value);
            }
            Some(response::Value::ClosingError(ref _err)) => {
                assert_eq!(
                    expected_response_type,
                    ResponseType::ClosingError,
                    "Received {response:?}",
                );
            }
            Some(response::Value::RequestError(ref _err)) => {
                assert_eq!(
                    expected_response_type,
                    ResponseType::RequestError,
                    "Received {response:?}",
                );
            }
            Some(response::Value::ConstantResponse(enum_value)) => {
                let enum_value = enum_value.unwrap();
                if enum_value == ConstantResponse::OK {
                    assert_eq!(
                        expected_value.unwrap(),
                        Value::Okay,
                        "Received {response:?}"
                    );
                } else {
                    unreachable!()
                }
            }
            Some(_) => unreachable!(),
            None => {
                assert!(expected_value.is_none(), "Expected {expected_value:?}",);
            }
        };
        response
    }

    fn write_header(buffer: &mut Vec<u8>, length: u32) {
        let required_space = u32::required_space(length);
        let new_len = buffer.len() + required_space;
        buffer.resize(new_len, 0_u8);
        u32::encode_var(length, &mut buffer[new_len - required_space..]);
    }

    fn write_message(buffer: &mut Vec<u8>, request: impl Message) {
        let message_length = request.compute_size() as u32;

        write_header(buffer, message_length);
        let _res = buffer.write_all(&request.write_to_bytes().unwrap());
    }

    fn get_command(components: CommandComponents) -> Command {
        let mut command = Command::new();
        command.request_type = components.request_type;
        if components.args_pointer {
            command.args = Some(Args::ArgsVecPointer(Box::leak(Box::new(components.args))
                as *mut Vec<bytes::Bytes>
                as u64));
        } else {
            let mut args_array = ArgsArray::new();
            args_array.args.clone_from(&components.args);
            command.args = Some(Args::ArgsArray(args_array));
        }
        command
    }

    fn get_command_request(
        callback_index: u32,
        args: Vec<bytes::Bytes>,
        request_type: EnumOrUnknown<RequestType>,
        args_pointer: bool,
    ) -> RedisRequest {
        let mut request = RedisRequest::new();
        request.callback_idx = callback_index;

        request.command = Some(redis_request::redis_request::Command::SingleCommand(
            get_command(CommandComponents {
                args,
                request_type,
                args_pointer,
            }),
        ));
        request
    }

    fn write_request(buffer: &mut Vec<u8>, socket: &mut UnixStream, request: RedisRequest) {
        write_message(buffer, request);
        socket.write_all(buffer).unwrap();
    }

    fn write_command_request(
        buffer: &mut Vec<u8>,
        socket: &mut UnixStream,
        callback_index: u32,
        args: Vec<bytes::Bytes>,
        request_type: EnumOrUnknown<RequestType>,
        args_pointer: bool,
    ) {
        let request = get_command_request(callback_index, args, request_type, args_pointer);

        write_request(buffer, socket, request);
    }

    fn write_transaction_request(
        buffer: &mut Vec<u8>,
        socket: &mut UnixStream,
        callback_index: u32,
        commands_components: Vec<CommandComponents>,
    ) {
        let mut request = RedisRequest::new();
        request.callback_idx = callback_index;
        let mut transaction = Transaction::new();
        transaction.commands.reserve(commands_components.len());

        for components in commands_components {
            transaction.commands.push(get_command(components));
        }

        request.command = Some(redis_request::redis_request::Command::Transaction(
            transaction,
        ));

        write_request(buffer, socket, request);
    }

    fn write_get(
        buffer: &mut Vec<u8>,
        socket: &mut UnixStream,
        callback_index: u32,
        key: &str,
        args_pointer: bool,
    ) {
        write_command_request(
            buffer,
            socket,
            callback_index,
            vec![key.to_string().into()],
            RequestType::Get.into(),
            args_pointer,
        )
    }

    fn write_set(
        buffer: &mut Vec<u8>,
        socket: &mut UnixStream,
        callback_index: u32,
        key: &str,
        value: String,
        args_pointer: bool,
    ) {
        write_command_request(
            buffer,
            socket,
            callback_index,
            vec![key.to_string().into(), value.into()],
            RequestType::Set.into(),
            args_pointer,
        )
    }

    fn parse_header(buffer: &[u8]) -> (u32, usize) {
        u32::decode_var(buffer).unwrap()
    }

    fn connect_to_redis(
        addresses: &[ConnectionAddr],
        socket: &UnixStream,
        use_tls: Tls,
        cluster_mode: ClusterMode,
    ) {
        // Send the server address
        const CALLBACK_INDEX: u32 = 0;
        let connection_request = create_connection_request(
            addresses,
            &TestConfiguration {
                use_tls: use_tls.to_bool(),
                cluster_mode,
                request_timeout: Some(10000),
                ..Default::default()
            },
        );
        let approx_message_length =
            APPROX_RESP_HEADER_LEN + connection_request.compute_size() as usize;
        let mut buffer = Vec::with_capacity(approx_message_length);
        write_message(&mut buffer, connection_request);
        let mut socket = socket.try_clone().unwrap();
        socket.write_all(&buffer).unwrap();
        assert_ok_response(&mut buffer, &mut socket, CALLBACK_INDEX);
    }

    fn setup_socket(
        use_tls: Tls,
        socket_path: Option<String>,
        addresses: &[ConnectionAddr],
        cluster_mode: ClusterMode,
    ) -> UnixStream {
        let socket_listener_state: Arc<ManualResetEvent> =
            Arc::new(ManualResetEvent::new(EventState::Unset));
        let cloned_state = socket_listener_state.clone();
        let path_arc = Arc::new(std::sync::Mutex::new(None));
        let path_arc_clone = Arc::clone(&path_arc);
        socket_listener::start_socket_listener_internal(
            move |res| {
                let path: String = res.expect("Failed to initialize the socket listener");
                let mut path_arc_clone = path_arc_clone.lock().unwrap();
                *path_arc_clone = Some(path);
                cloned_state.set();
            },
            socket_path,
        );
        socket_listener_state.wait();
        let path = path_arc.lock().unwrap();
        let path = path.as_ref().expect("Didn't get any socket path");
        let socket = std::os::unix::net::UnixStream::connect(path).unwrap();
        connect_to_redis(addresses, &socket, use_tls, cluster_mode);
        socket
    }

    fn setup_mocked_test_basics(socket_path: Option<String>) -> ServerTestBasicsWithMock {
        let mut responses = std::collections::HashMap::new();
        responses.insert(
            "*2\r\n$4\r\nINFO\r\n$11\r\nREPLICATION\r\n".to_string(),
            Value::BulkString(b"role:master\r\nconnected_slaves:0\r\n".to_vec()),
        );
        let server_mock = ServerMock::new(responses);
        let addresses = server_mock.get_addresses();
        let socket = setup_socket(
            Tls::NoTls,
            socket_path,
            addresses.as_slice(),
            ClusterMode::Disabled,
        );
        ServerTestBasicsWithMock {
            server_mock,
            socket,
        }
    }

    fn setup_server_test_basics_with_server_and_socket_path(
        use_tls: Tls,
        socket_path: Option<String>,
        server: Option<RedisServer>,
    ) -> ServerTestBasics {
        let address = server
            .as_ref()
            .map(|server| server.get_client_addr())
            .unwrap_or(get_shared_server_address(use_tls.to_bool()));
        let socket = setup_socket(use_tls, socket_path, &[address], ClusterMode::Disabled);
        ServerTestBasics { server, socket }
    }

    fn setup_test_basics_with_socket_path(
        use_tls: Tls,
        socket_path: Option<String>,
        shared_server: TestServer,
    ) -> ServerTestBasics {
        let server = match shared_server {
            TestServer::Shared => None,
            TestServer::Unique => Some(RedisServer::new(ServerType::Tcp {
                tls: use_tls.to_bool(),
            })),
        };
        setup_server_test_basics_with_server_and_socket_path(use_tls, socket_path, server)
    }

    fn setup_server_test_basics(use_tls: Tls, shared_server: TestServer) -> ServerTestBasics {
        setup_test_basics_with_socket_path(use_tls, None, shared_server)
    }

    fn setup_test_basics(
        use_tls: Tls,
        shared_server: TestServer,
        redis_type: RedisType,
    ) -> TestBasics {
        match redis_type {
            RedisType::Cluster => {
                let cluster = setup_cluster_test_basics(use_tls, shared_server);
                TestBasics {
                    server: BackingServer::Cluster(cluster._cluster),
                    socket: cluster.socket,
                }
            }
            RedisType::Standalone => {
                let server = setup_server_test_basics(use_tls, shared_server);
                TestBasics {
                    server: BackingServer::Standalone(server.server),
                    socket: server.socket,
                }
            }
        }
    }

    fn setup_cluster_test_basics(use_tls: Tls, shared_cluster: TestServer) -> ClusterTestBasics {
        let cluster = match shared_cluster {
            TestServer::Shared => None,
            TestServer::Unique => Some(RedisCluster::new(use_tls.to_bool(), &None, None, None)),
        };
        let socket = setup_socket(
            use_tls,
            None,
            &cluster
                .as_ref()
                .map(|cluster| cluster.get_server_addresses())
                .unwrap_or(get_shared_cluster_addresses(use_tls.to_bool())),
            ClusterMode::Enabled,
        );
        ClusterTestBasics {
            _cluster: cluster,
            socket,
        }
    }

    #[rstest]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_working_after_socket_listener_was_dropped() {
        let socket_path =
            get_socket_path_from_name("test_working_after_socket_listener_was_dropped".to_string());
        close_socket(&socket_path);
        // create a socket listener and drop it, to simulate a panic in a previous iteration.
        Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap()
            .block_on(async {
                let _ = UnixListener::bind(socket_path.clone()).unwrap();
            });

        const CALLBACK_INDEX: u32 = 99;
        let mut test_basics = setup_test_basics_with_socket_path(
            Tls::NoTls,
            Some(socket_path.clone()),
            TestServer::Shared,
        );
        let key = generate_random_string(KEY_LENGTH);
        let approx_message_length = key.len() + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        write_get(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK_INDEX,
            key.as_str(),
            false,
        );

        assert_null_response(&mut buffer, &mut test_basics.socket, CALLBACK_INDEX);
        close_socket(&socket_path);
    }

    #[rstest]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_multiple_listeners_competing_for_the_socket() {
        let socket_path = get_socket_path_from_name(
            "test_multiple_listeners_competing_for_the_socket".to_string(),
        );
        close_socket(&socket_path);
        let server = Arc::new(RedisServer::new(ServerType::Tcp { tls: false }));

        thread::scope(|scope| {
            for i in 0..20 {
                thread::Builder::new()
                    .name(format!("test-{i}"))
                    .spawn_scoped(scope, || {
                        const CALLBACK_INDEX: u32 = 99;
                        let address = server.get_client_addr();
                        let mut socket = setup_socket(
                            Tls::NoTls,
                            Some(socket_path.clone()),
                            &[address],
                            ClusterMode::Disabled,
                        );
                        let key = generate_random_string(KEY_LENGTH);
                        let approx_message_length = key.len() + APPROX_RESP_HEADER_LEN;
                        let mut buffer = Vec::with_capacity(approx_message_length);
                        write_get(
                            &mut buffer,
                            &mut socket,
                            CALLBACK_INDEX,
                            key.as_str(),
                            false,
                        );

                        assert_null_response(&mut buffer, &mut socket, CALLBACK_INDEX);
                    })
                    .unwrap();
            }
        });
        close_socket(&socket_path);
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_socket_set_and_get(
        #[values((false, Tls::NoTls), (true, Tls::NoTls), (false, Tls::UseTls))]
        use_arg_pointer_and_tls: (bool, Tls),
        #[values(RedisType::Cluster, RedisType::Standalone)] use_cluster: RedisType,
    ) {
        let args_pointer = use_arg_pointer_and_tls.0;
        let use_tls = use_arg_pointer_and_tls.1;
        let mut test_basics = setup_test_basics(use_tls, TestServer::Shared, use_cluster);

        const CALLBACK1_INDEX: u32 = 100;
        const CALLBACK2_INDEX: u32 = 101;
        const VALUE_LENGTH: usize = 10;
        let key = generate_random_string(KEY_LENGTH);
        let value = generate_random_string(VALUE_LENGTH);
        // Send a set request
        let approx_message_length = VALUE_LENGTH + key.len() + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        write_set(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK1_INDEX,
            key.as_str(),
            value.clone(),
            args_pointer,
        );

        assert_ok_response(&mut buffer, &mut test_basics.socket, CALLBACK1_INDEX);

        buffer.clear();
        write_get(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK2_INDEX,
            key.as_str(),
            args_pointer,
        );
        assert_value_response(
            &mut buffer,
            Some(&mut test_basics.socket),
            CALLBACK2_INDEX,
            Value::BulkString(value.into_bytes()),
        );
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_socket_pass_custom_command(
        #[values(false, true)] args_pointer: bool,
        #[values(RedisType::Cluster, RedisType::Standalone)] use_cluster: RedisType,
    ) {
        let mut test_basics = setup_test_basics(Tls::NoTls, TestServer::Shared, use_cluster);

        const CALLBACK1_INDEX: u32 = 100;
        const CALLBACK2_INDEX: u32 = 101;
        const VALUE_LENGTH: usize = 10;
        let key = generate_random_string(KEY_LENGTH);
        let value = generate_random_string(VALUE_LENGTH);
        // Send a set request
        let approx_message_length = VALUE_LENGTH + key.len() + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        write_command_request(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK1_INDEX,
            vec![
                "SET".to_string().into(),
                key.to_string().into(),
                value.clone().into(),
            ],
            RequestType::CustomCommand.into(),
            args_pointer,
        );

        assert_ok_response(&mut buffer, &mut test_basics.socket, CALLBACK1_INDEX);

        buffer.clear();
        write_command_request(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK2_INDEX,
            vec!["GET".to_string().into(), key.into()],
            RequestType::CustomCommand.into(),
            args_pointer,
        );

        assert_value_response(
            &mut buffer,
            Some(&mut test_basics.socket),
            CALLBACK2_INDEX,
            Value::BulkString(value.into_bytes()),
        );
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_socket_pass_manual_route_to_all_primaries() {
        let mut test_basics = setup_cluster_test_basics(Tls::NoTls, TestServer::Shared);

        const CALLBACK1_INDEX: u32 = 100;
        let approx_message_length = 4 + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        let mut request = get_command_request(
            CALLBACK1_INDEX,
            vec!["ECHO".to_string().into(), "foo".to_string().into()],
            RequestType::CustomCommand.into(),
            false,
        );
        let mut routes = redis_request::Routes::default();
        routes.set_simple_routes(redis_request::SimpleRoutes::AllPrimaries);
        request.route = Some(routes).into();
        write_request(&mut buffer, &mut test_basics.socket, request);

        let response = get_response(&mut buffer, Some(&mut test_basics.socket));

        assert_eq!(response.callback_idx, CALLBACK1_INDEX);
        let Some(response::Value::RespPointer(pointer)) = response.value else {
            panic!("Unexpected response {:?}", response.value);
        };
        let received_value = pointer_to_value(pointer);
        let Value::Map(values) = *received_value else {
            panic!("Unexpected value {:?}", received_value);
        };
        assert_eq!(values.len(), 3);
        for i in 0..3 {
            assert_eq!(values.get(i).unwrap().1, Value::BulkString(b"foo".to_vec()));
        }
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_socket_cluster_route_by_address_reaches_correct_node() {
        // returns the line that contains the word "myself", up to that point. This is done because the values after it might change with time.
        let clean_result = |value: String| {
            value
                .split('\n')
                .find(|line| line.contains("myself"))
                .and_then(|line| line.split_once("myself"))
                .unwrap()
                .0
                .to_string()
        };

        // We send a request to a random node, get that node's hostname & port, and then
        // route the same request to the hostname & port, and verify that we've received the same value.
        let mut test_basics = setup_cluster_test_basics(Tls::NoTls, TestServer::Shared);

        const CALLBACK_INDEX: u32 = 100;
        let approx_message_length = 4 + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        let mut request = get_command_request(
            CALLBACK_INDEX,
            vec!["CLUSTER".to_string().into(), "NODES".to_string().into()],
            RequestType::CustomCommand.into(),
            false,
        );
        let mut routes = redis_request::Routes::default();
        routes.set_simple_routes(redis_request::SimpleRoutes::Random);
        request.route = Some(routes).into();
        write_request(&mut buffer, &mut test_basics.socket, request.clone());

        let response = get_response(&mut buffer, Some(&mut test_basics.socket));

        assert_eq!(response.callback_idx, CALLBACK_INDEX);
        let Some(response::Value::RespPointer(pointer)) = response.value else {
            panic!("Unexpected response {:?}", response.value);
        };
        let received_value = pointer_to_value(pointer);
        let first_value = clean_result(String::from_redis_value(&received_value).unwrap());

        let (host, port) = first_value
            .split_once(' ')
            .and_then(|(_, second)| second.split_once('@'))
            .and_then(|(first, _)| first.split_once(':'))
            .and_then(|(host, port)| port.parse::<i32>().map(|port| (host, port)).ok())
            .unwrap();

        buffer.clear();
        let mut routes = redis_request::Routes::default();
        let by_address_route = glide_core::redis_request::ByAddressRoute {
            host: host.into(),
            port,
            ..Default::default()
        };
        routes.set_by_address_route(by_address_route);
        request.route = Some(routes).into();
        write_request(&mut buffer, &mut test_basics.socket, request);

        let response = get_response(&mut buffer, Some(&mut test_basics.socket));

        assert_eq!(response.callback_idx, CALLBACK_INDEX);
        let Some(response::Value::RespPointer(pointer)) = response.value else {
            panic!("Unexpected response {:?}", response.value);
        };
        let received_value = pointer_to_value(pointer);
        let second_value = clean_result(String::from_redis_value(&received_value).unwrap());

        assert_eq!(first_value, second_value);
    }

    #[rstest]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_socket_get_returns_null(#[values(false, true)] use_arg_pointer: bool) {
        const CALLBACK_INDEX: u32 = 99;
        let mut expected_command = Cmd::new();
        let key = generate_random_string(KEY_LENGTH);
        expected_command.arg("GET").arg(key.clone());
        let mut test_basics = setup_mocked_test_basics(None);
        test_basics
            .server_mock
            .add_response(&expected_command, "*-1\r\n".to_string());
        let mut buffer = Vec::with_capacity(key.len() * 2);
        write_get(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK_INDEX,
            key.as_str(),
            use_arg_pointer,
        );

        assert_null_response(&mut buffer, &mut test_basics.socket, CALLBACK_INDEX);
    }

    #[rstest]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_socket_report_error() {
        const CALLBACK_INDEX: u32 = 99;
        let mut test_basics = setup_mocked_test_basics(None);

        let key = generate_random_string(1);
        let request_type = i32::MAX; // here we send an erroneous enum
                                     // Send a set request
        let approx_message_length = key.len() + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        write_command_request(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK_INDEX,
            vec![key.into()],
            EnumOrUnknown::from_i32(request_type),
            false,
        );

        let response = assert_error_response(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK_INDEX,
            ResponseType::ClosingError,
        );
        assert_eq!(
            response.closing_error(),
            format!("Received invalid request type: {request_type}")
        );
        assert_eq!(test_basics.server_mock.get_number_of_received_commands(), 0);
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_socket_send_and_receive_long_values(
        #[values((false, Tls::NoTls), (true, Tls::NoTls), (false, Tls::UseTls))]
        use_arg_pointer_and_tls: (bool, Tls),
        #[values(RedisType::Cluster, RedisType::Standalone)] use_cluster: RedisType,
    ) {
        let args_pointer = use_arg_pointer_and_tls.0;
        let use_tls = use_arg_pointer_and_tls.1;
        let mut test_basics = setup_test_basics(use_tls, TestServer::Shared, use_cluster);

        const CALLBACK1_INDEX: u32 = 100;
        const CALLBACK2_INDEX: u32 = 101;
        const VALUE_LENGTH: usize = 1000000;
        let key = generate_random_string(KEY_LENGTH);
        let value = generate_random_string(VALUE_LENGTH);
        // Send a set request
        let approx_message_length = VALUE_LENGTH
            + key.len()
            + u32::required_space(VALUE_LENGTH as u32)
            + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        write_set(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK1_INDEX,
            key.as_str(),
            value.clone(),
            args_pointer,
        );

        assert_ok_response(&mut buffer, &mut test_basics.socket, CALLBACK1_INDEX);

        buffer.clear();
        write_get(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK2_INDEX,
            key.as_str(),
            args_pointer,
        );

        let response_header_length = u32::required_space(size_of::<usize>() as u32);
        let expected_length = size_of::<usize>() + response_header_length + 2; // 2 bytes for callbackIdx and value type

        // we set the length to a longer value, just in case we'll get more data - which is a failure for the test.
        buffer.resize(approx_message_length, 0);
        let mut size = 0;
        while size < expected_length {
            let next_read = test_basics.socket.read(&mut buffer[size..]).unwrap();
            assert_ne!(0, next_read);
            size += next_read;
        }
        assert_value_response(
            &mut buffer,
            None,
            CALLBACK2_INDEX,
            Value::BulkString(value.into_bytes()),
        );
    }

    // This test starts multiple threads writing large inputs to a socket, and another thread that reads from the output socket and
    // verifies that the outputs match the inputs.
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_socket_send_and_receive_multiple_long_inputs(
        #[values((false, Tls::NoTls), (true, Tls::NoTls), (false, Tls::UseTls))]
        use_arg_pointer_and_tls: (bool, Tls),
        #[values(RedisType::Cluster, RedisType::Standalone)] use_cluster: RedisType,
    ) {
        #[derive(Clone, PartialEq, Eq, Debug)]
        enum State {
            Initial,
            ReceivedNull,
            ReceivedValue,
        }
        let args_pointer = use_arg_pointer_and_tls.0;
        let use_tls = use_arg_pointer_and_tls.1;
        let test_basics = setup_test_basics(use_tls, TestServer::Shared, use_cluster);
        const VALUE_LENGTH: usize = 1000000;
        const NUMBER_OF_THREADS: usize = 10;
        let values = Arc::new(Mutex::new(vec![Vec::<u8>::new(); NUMBER_OF_THREADS]));
        let results = Arc::new(Mutex::new(vec![State::Initial; NUMBER_OF_THREADS]));
        let lock = Arc::new(Mutex::new(()));
        thread::scope(|scope| {
            let values_for_read = values.clone();
            let results_for_read = results.clone();
            // read thread
            let mut read_socket = test_basics.socket.try_clone().unwrap();
            scope.spawn(move || {
                let mut received_callbacks = 0;
                let mut buffer = vec![0_u8; 2 * (VALUE_LENGTH + 2 * APPROX_RESP_HEADER_LEN)];
                let mut next_start = 0;
                while received_callbacks < NUMBER_OF_THREADS * 2 {
                    let size = read_socket.read(&mut buffer[next_start..]).unwrap();
                    let mut cursor = 0;
                    while cursor < size {
                        let (request_len, header_bytes) =
                            parse_header(&buffer[cursor..cursor + APPROX_RESP_HEADER_LEN]);
                        let length = request_len as usize;

                        if cursor + header_bytes + length > size + next_start {
                            break;
                        }

                        {
                            let response = decode_response(&buffer, cursor + header_bytes, length);
                            let callback_index = response.callback_idx as usize;
                            let mut results = results_for_read.lock().unwrap();
                            match response.value {
                                Some(response::Value::ConstantResponse(constant)) => {
                                    assert_eq!(constant, ConstantResponse::OK.into());
                                    assert_eq!(results[callback_index], State::Initial);
                                    results[callback_index] = State::ReceivedNull;
                                }
                                Some(response::Value::RespPointer(pointer)) => {
                                    assert_eq!(results[callback_index], State::ReceivedNull);

                                    let values = values_for_read.lock().unwrap();
                                    assert_value(
                                        pointer,
                                        Some(Value::BulkString(values[callback_index].clone())),
                                    );
                                    results[callback_index] = State::ReceivedValue;
                                }
                                _ => unreachable!(),
                            };
                        }

                        cursor += length + header_bytes;
                        received_callbacks += 1;
                    }

                    let save_size = next_start + size - cursor;
                    next_start = save_size;
                    if next_start > 0 {
                        let mut new_buffer =
                            vec![0_u8; 2 * VALUE_LENGTH + 4 * APPROX_RESP_HEADER_LEN];
                        let slice = &buffer[cursor..cursor + save_size];
                        let iter = slice.iter().copied();
                        new_buffer.splice(..save_size, iter);
                        buffer = new_buffer;
                    }
                }
            });

            for i in 0..NUMBER_OF_THREADS {
                let mut write_socket = test_basics.socket.try_clone().unwrap();
                let values = values.clone();
                let index = i;
                let cloned_lock = lock.clone();
                scope.spawn(move || {
                    let key = format!("hello{index}");
                    let value = generate_random_string(VALUE_LENGTH);

                    {
                        let mut values = values.lock().unwrap();
                        values[index] = value.clone().into();
                    }

                    // Send a set request
                    let approx_message_length = VALUE_LENGTH + key.len() + APPROX_RESP_HEADER_LEN;
                    let mut buffer = Vec::with_capacity(approx_message_length);
                    {
                        let _guard = cloned_lock.lock().unwrap();
                        write_set(
                            &mut buffer,
                            &mut write_socket,
                            index as u32,
                            &key,
                            value,
                            args_pointer,
                        );
                    }
                    buffer.clear();

                    // Send a get request
                    {
                        let _guard = cloned_lock.lock().unwrap();
                        write_get(
                            &mut buffer,
                            &mut write_socket,
                            index as u32,
                            &key,
                            args_pointer,
                        );
                    }
                });
            }
        });

        let results = results.lock().unwrap();
        for i in 0..NUMBER_OF_THREADS {
            assert_eq!(State::ReceivedValue, results[i]);
        }
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_does_not_close_when_server_closes() {
        let mut test_basics =
            setup_test_basics(Tls::NoTls, TestServer::Unique, RedisType::Standalone);
        let server = test_basics.server;

        drop(server);

        const CALLBACK_INDEX: u32 = 0;
        let key = generate_random_string(KEY_LENGTH);
        let mut buffer = Vec::with_capacity(100);
        write_get(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK_INDEX,
            key.as_str(),
            false,
        );

        assert_error_response(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK_INDEX,
            ResponseType::RequestError,
        );
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_reconnect_after_temporary_disconnect() {
        let test_basics = setup_server_test_basics(Tls::NoTls, TestServer::Unique);
        let mut socket = test_basics.socket.try_clone().unwrap();
        let address = test_basics.server.as_ref().unwrap().get_client_addr();
        drop(test_basics);

        let new_server = RedisServer::new_with_addr_and_modules(address, &[]);
        block_on_all(wait_for_server_to_become_ready(
            &new_server.get_client_addr(),
        ));

        const CALLBACK_INDEX: u32 = 0;
        let key = generate_random_string(KEY_LENGTH);
        // TODO - this part should be replaced with a sleep once we implement heartbeat
        let mut buffer = Vec::with_capacity(100);
        write_get(
            &mut buffer,
            &mut socket,
            CALLBACK_INDEX,
            key.as_str(),
            false,
        );

        assert_error_response(
            &mut buffer,
            &mut socket,
            CALLBACK_INDEX,
            ResponseType::RequestError,
        );

        let mut buffer = Vec::with_capacity(100);
        write_get(
            &mut buffer,
            &mut socket,
            CALLBACK_INDEX,
            key.as_str(),
            false,
        );

        assert_null_response(&mut buffer, &mut socket, CALLBACK_INDEX);
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_handle_request_after_reporting_disconnet() {
        let test_basics = setup_server_test_basics(Tls::NoTls, TestServer::Unique);
        let mut socket = test_basics.socket.try_clone().unwrap();
        let address = test_basics.server.as_ref().unwrap().get_client_addr();
        drop(test_basics);

        const CALLBACK_INDEX: u32 = 0;
        let key = generate_random_string(KEY_LENGTH);
        let mut buffer = Vec::with_capacity(100);
        write_get(
            &mut buffer,
            &mut socket,
            CALLBACK_INDEX,
            key.as_str(),
            false,
        );

        assert_error_response(
            &mut buffer,
            &mut socket,
            CALLBACK_INDEX,
            ResponseType::RequestError,
        );

        let new_server = RedisServer::new_with_addr_and_modules(address, &[]);
        block_on_all(wait_for_server_to_become_ready(
            &new_server.get_client_addr(),
        ));

        let mut buffer = Vec::with_capacity(100);
        write_get(
            &mut buffer,
            &mut socket,
            CALLBACK_INDEX,
            key.as_str(),
            false,
        );

        assert_null_response(&mut buffer, &mut socket, CALLBACK_INDEX);
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_transaction_and_get_array_of_results(
        #[values(RedisType::Cluster, RedisType::Standalone)] use_cluster: RedisType,
    ) {
        let test_basics = setup_test_basics(Tls::NoTls, TestServer::Shared, use_cluster);
        let mut socket = test_basics.socket;

        const CALLBACK_INDEX: u32 = 0;
        let key = generate_random_string(KEY_LENGTH);
        let commands = vec![
            CommandComponents {
                args: vec![key.clone().into(), "bar".to_string().into()],
                args_pointer: true,
                request_type: RequestType::Set.into(),
            },
            CommandComponents {
                args: vec!["GET".to_string().into(), key.clone().into()],
                args_pointer: false,
                request_type: RequestType::CustomCommand.into(),
            },
            CommandComponents {
                args: vec!["FLUSHALL".to_string().into()],
                args_pointer: false,
                request_type: RequestType::CustomCommand.into(),
            },
            CommandComponents {
                args: vec![key.into()],
                args_pointer: false,
                request_type: RequestType::Get.into(),
            },
        ];
        let mut buffer = Vec::with_capacity(200);
        write_transaction_request(&mut buffer, &mut socket, CALLBACK_INDEX, commands);

        assert_value_response(
            &mut buffer,
            Some(&mut socket),
            CALLBACK_INDEX,
            Value::Array(vec![
                Value::Okay,
                Value::BulkString(vec![b'b', b'a', b'r']),
                Value::Okay,
                Value::Nil,
            ]),
        );
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_script(
        #[values(RedisType::Cluster, RedisType::Standalone)] use_cluster: RedisType,
    ) {
        let mut test_basics = setup_test_basics(Tls::NoTls, TestServer::Shared, use_cluster);
        const CALLBACK_INDEX: u32 = 100;
        const VALUE_LENGTH: usize = 10;
        let key = generate_random_string(KEY_LENGTH);
        let value = generate_random_string(VALUE_LENGTH);
        let script = r#"redis.call("SET", KEYS[1], ARGV[1]); return redis.call("GET", KEYS[1])"#;
        let hash = add_script(script);

        let approx_message_length = hash.len() + value.len() + key.len() + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);

        let mut request = RedisRequest::new();
        request.callback_idx = CALLBACK_INDEX;
        request.command = Some(redis_request::redis_request::Command::ScriptInvocation(
            redis_request::ScriptInvocation {
                hash: hash.into(),
                keys: vec![key.into()],
                args: vec![value.clone().into()],
                ..Default::default()
            },
        ));

        write_request(&mut buffer, &mut test_basics.socket, request);

        assert_value_response(
            &mut buffer,
            Some(&mut test_basics.socket),
            CALLBACK_INDEX,
            Value::BulkString(value.into_bytes()),
        );
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_empty_custom_command_is_an_error(
        #[values(RedisType::Cluster, RedisType::Standalone)] use_cluster: RedisType,
    ) {
        let mut test_basics = setup_test_basics(Tls::NoTls, TestServer::Shared, use_cluster);
        const CALLBACK_INDEX: u32 = 42;

        let mut buffer = Vec::with_capacity(APPROX_RESP_HEADER_LEN);
        write_command_request(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK_INDEX,
            vec![],
            RequestType::CustomCommand.into(),
            false,
        );

        assert_error_response(
            &mut buffer,
            &mut test_basics.socket,
            CALLBACK_INDEX,
            ResponseType::RequestError,
        );
    }
}
