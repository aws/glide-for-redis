// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::{
    client::Client,
    connection_request::{ConnectionRequest, NodeAddress, TlsMode},
};
use gungraun::{library_benchmark, library_benchmark_group, main};
use redis::{Value, cmd};
use std::hint::black_box;
use tokio::runtime::Builder;

fn create_connection_request() -> ConnectionRequest {
    let host = "localhost";
    let mut request = ConnectionRequest::new();
    request.tls_mode = TlsMode::NoTls.into();
    let mut address_info = NodeAddress::new();
    address_info.host = host.into();
    address_info.port = 6379;
    request.addresses.push(address_info);
    request
}

fn runner<Fut>(f: impl FnOnce(Client) -> Fut)
where
    Fut: futures::Future<Output = ()>,
{
    let runtime = Builder::new_current_thread().enable_all().build().unwrap();
    runtime.block_on(async {
        let client = Client::new(create_connection_request().into(), None)
            .await
            .unwrap();
        f(client).await;
    });
}

#[library_benchmark]
fn just_setup() {
    runner(|_| async {});
}

#[library_benchmark]
fn send_message() {
    runner(|mut client| async move {
        let mut command = black_box(cmd("PING"));
        client.send_command(&mut command, None).await.unwrap();
    });
}

#[library_benchmark]
fn send_message_owned() {
    runner(|mut client| async move {
        let command = black_box(cmd("PING"));
        client.send_command_owned(command, None).await.unwrap();
    });
}

#[library_benchmark]
fn send_and_receive_messages() {
    runner(|mut client| async move {
        let mut command = cmd("SET");
        command.arg("foo").arg("bar");
        let mut command = black_box(command);
        client.send_command(&mut command, None).await.unwrap();

        let mut command = cmd("SET");
        command.arg("baz").arg("foo");
        let mut command = black_box(command);
        client.send_command(&mut command, None).await.unwrap();

        let mut command = cmd("MGET");
        command.arg("baz").arg("foo");
        let mut command = black_box(command);
        let result = client.send_command(&mut command, None).await.unwrap();
        assert!(
            result
                == Value::Array(vec![
                    Value::BulkString(b"foo".to_vec().into()),
                    Value::BulkString(b"bar".to_vec().into())
                ])
        )
    });
}

#[library_benchmark]
fn lots_of_messages() {
    runner(|mut client| async move {
        for _ in 0..1000 {
            let mut command = cmd("SET");
            command.arg("foo").arg("bar");
            let mut command = black_box(command);
            client.send_command(&mut command, None).await.unwrap();

            let mut command = cmd("SET");
            command.arg("baz").arg("foo");
            let mut command = black_box(command);
            client.send_command(&mut command, None).await.unwrap();

            let mut command = cmd("MGET");
            command.arg("baz").arg("foo");
            let mut command = black_box(command);
            let result = client.send_command(&mut command, None).await.unwrap();
            assert!(
                result
                    == Value::Array(vec![
                        Value::BulkString(b"foo".to_vec().into()),
                        Value::BulkString(b"bar".to_vec().into())
                    ])
            )
        }
    });
}

library_benchmark_group!(
    name = cluster;
    benchmarks = just_setup, send_message, send_message_owned, send_and_receive_messages, lots_of_messages
);

main!(library_benchmark_groups = cluster);
