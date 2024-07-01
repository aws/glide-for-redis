/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use criterion::{criterion_group, criterion_main, Criterion};
use futures::future::join_all;
use redis::{
    aio::{ConnectionLike, ConnectionManager, MultiplexedConnection},
    cluster::ClusterClientBuilder,
    cluster_async::ClusterConnection,
    AsyncCommands, ConnectionAddr, ConnectionInfo, RedisConnectionInfo, RedisResult, Value,
};
use std::env;
use tokio::runtime::{Builder, Runtime};

trait GlideClient: ConnectionLike + Send + Clone {}

impl GlideClient for MultiplexedConnection {}
impl GlideClient for ConnectionManager {}
impl GlideClient for ClusterConnection {}

async fn run_get(mut connection: impl GlideClient) -> RedisResult<Value> {
    connection.get("foo").await
}

fn benchmark_single_get(
    c: &mut Criterion,
    connection_id: &str,
    test_group: &str,
    connection: impl GlideClient,
    runtime: &Runtime,
) {
    let mut group = c.benchmark_group(test_group);
    group.significance_level(0.1).sample_size(500);
    group.bench_function(format!("{connection_id}-single get"), move |b| {
        b.to_async(runtime).iter(|| run_get(connection.clone()));
    });
}

fn benchmark_concurrent_gets(
    c: &mut Criterion,
    connection_id: &str,
    test_group: &str,
    connection: impl GlideClient,
    runtime: &Runtime,
) {
    const ITERATIONS: usize = 100;
    let mut group = c.benchmark_group(test_group);
    group.significance_level(0.1).sample_size(150);
    group.bench_function(format!("{connection_id}-concurrent gets"), move |b| {
        b.to_async(runtime).iter(|| {
            let mut actions = Vec::with_capacity(ITERATIONS);
            for _ in 0..ITERATIONS {
                actions.push(run_get(connection.clone()));
            }
            join_all(actions)
        });
    });
}

fn benchmark<Fun, Con>(
    c: &mut Criterion,
    address: ConnectionAddr,
    connection_id: &str,
    group: &str,
    connection_creation: Fun,
) where
    Con: GlideClient,
    Fun: FnOnce(ConnectionAddr, &Runtime) -> Con,
{
    let runtime = Builder::new_current_thread().enable_all().build().unwrap();
    let connection = connection_creation(address, &runtime);
    benchmark_single_get(c, connection_id, group, connection.clone(), &runtime);
    benchmark_concurrent_gets(c, connection_id, group, connection, &runtime);
}

fn get_connection_info(address: ConnectionAddr) -> redis::ConnectionInfo {
    ConnectionInfo {
        addr: address,
        redis: RedisConnectionInfo::default(),
    }
}

fn multiplexer_benchmark(c: &mut Criterion, address: ConnectionAddr, group: &str) {
    benchmark(c, address, "multiplexer", group, |address, runtime| {
        let client = redis::Client::open(get_connection_info(address)).unwrap();
        runtime.block_on(async { client.get_multiplexed_tokio_connection(None).await.unwrap() })
    });
}

fn connection_manager_benchmark(c: &mut Criterion, address: ConnectionAddr, group: &str) {
    benchmark(
        c,
        address,
        "connection-manager",
        group,
        |address, runtime| {
            let client = redis::Client::open(get_connection_info(address)).unwrap();
            runtime.block_on(async { client.get_connection_manager().await.unwrap() })
        },
    );
}

fn cluster_connection_benchmark(
    c: &mut Criterion,
    address: ConnectionAddr,
    group: &str,
    read_from_replica: bool,
) {
    let connection_id = if read_from_replica {
        "read-from-replica"
    } else {
        "read-from-master"
    };
    benchmark(c, address, connection_id, group, |address, runtime| {
        runtime
            .block_on(async {
                let mut builder = ClusterClientBuilder::new(vec![get_connection_info(address)])
                    .tls(redis::cluster::TlsMode::Secure);
                if read_from_replica {
                    builder = builder.read_from_replicas();
                }
                let client = builder.build().unwrap();
                client.get_async_connection(None).await
            })
            .unwrap()
    });
}

fn local_benchmark<F: FnOnce(&mut Criterion, ConnectionAddr, &str)>(c: &mut Criterion, f: F) {
    f(
        c,
        ConnectionAddr::Tcp("localhost".to_string(), 6379),
        "local",
    );
}

fn get_tls_address() -> Result<ConnectionAddr, impl std::error::Error> {
    env::var("HOST").map(|host| ConnectionAddr::TcpTls {
        host,
        port: 6379,
        insecure: false,
        tls_params: None,
    })
}

fn remote_benchmark<F: FnOnce(&mut Criterion, ConnectionAddr, &str)>(c: &mut Criterion, f: F) {
    let Ok(address) = get_tls_address() else {
        eprintln!("*** HOST must be set as an env parameter for remote server benchmark ***");
        return;
    };
    f(c, address, "remote");
}

fn multiplexer_benchmarks(c: &mut Criterion) {
    remote_benchmark(c, multiplexer_benchmark);
    local_benchmark(c, multiplexer_benchmark)
}

fn connection_manager_benchmarks(c: &mut Criterion) {
    remote_benchmark(c, connection_manager_benchmark);
    local_benchmark(c, connection_manager_benchmark)
}

fn cluster_connection_benchmarks(c: &mut Criterion) {
    let Ok(address) = env::var("CLUSTER_HOST").map(|host| ConnectionAddr::TcpTls {
        host,
        port: 6379,
        insecure: false,
        tls_params: None,
    }) else {
        return;
    };
    cluster_connection_benchmark(c, address.clone(), "ClusterConnection", false);
    cluster_connection_benchmark(c, address, "ClusterConnection", true);
}

criterion_group!(
    benches,
    connection_manager_benchmarks,
    multiplexer_benchmarks,
    cluster_connection_benchmarks
);

criterion_main!(benches);
