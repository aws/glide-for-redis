/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

#[cfg(not(target_env = "msvc"))]
use tikv_jemallocator::Jemalloc;

#[cfg(not(target_env = "msvc"))]
#[global_allocator]
static GLOBAL: Jemalloc = Jemalloc;

use clap::Parser;
use futures::{self, future::join_all, stream, StreamExt};
use glide_core::client::{Client, ConnectionRequest, NodeAddress, TlsMode};
use rand::{thread_rng, Rng};
use serde_json::Value;
use std::{
    cmp::max,
    collections::HashMap,
    path::Path,
    sync::{atomic::AtomicUsize, Arc},
    time::{Duration, Instant},
};

#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
struct Args {
    #[arg(
        name = "resultsFile",
        long,
        default_value = "../results/rust-results.json"
    )]
    results_file: String,

    #[arg(long, default_value = "localhost")]
    host: String,

    #[arg(name = "dataSize", long, default_value_t = 100)]
    data_size: usize,

    #[arg(name = "concurrentTasks", long, default_values_t = [1,10,100,1000])]
    concurrent_tasks: Vec<usize>,

    #[arg(name = "clientCount", long, default_value_t = 1)]
    client_count: usize,

    #[arg(long, default_value_t = false)]
    tls: bool,

    #[arg(name = "clusterModeEnabled", long, default_value_t = false)]
    cluster_mode_enabled: bool,

    #[arg(name = "port", long, default_value_t = PORT)]
    port: u32,

    #[arg(long, default_value_t = false)]
    minimal: bool,
}

// Connection constants - these should be adjusted to fit your connection.
const PORT: u32 = 6379;

// Benchmark constants - adjusting these will change the meaning of the benchmark.
const PROB_GET: f64 = 0.8;
const PROB_GET_EXISTING_KEY: f64 = 0.8;
const SIZE_GET_KEYSPACE: u32 = 3_750_000;
const SIZE_SET_KEYSPACE: u32 = 3_000_000;

#[derive(Eq, PartialEq, Hash)]
enum ChosenAction {
    GetNonExisting,
    GetExisting,
    Set,
}

fn main() {
    let args = Args::parse();
    logger_core::init(
        Some(logger_core::Level::Warn),
        Path::new(&args.results_file)
            .file_stem()
            .and_then(|os_str| os_str.to_str()),
    );

    // We can test using single or multi threading, by changing the runtime.
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .thread_name("rust benchmark")
        .enable_all()
        .build()
        .unwrap();
    runtime.block_on(perform_benchmark(args));
}

async fn perform_benchmark(args: Args) {
    let mut total_results = Vec::new();
    for concurrent_tasks_count in args.concurrent_tasks.iter() {
        println!(
            "
        Starting data size: {} concurrency: {concurrent_tasks_count} client count: {} is_cluster: {} {}",
            args.data_size, args.client_count, args.cluster_mode_enabled, chrono::offset::Utc::now()
        );
        let counter = Arc::new(AtomicUsize::new(0));
        let number_of_operations = if args.minimal {
            1000
        } else {
            max(100000, concurrent_tasks_count * 10000)
        };

        let connections = stream::iter(0..args.client_count)
            .fold(Vec::with_capacity(args.client_count), |mut acc, _| async {
                acc.push(get_connection(&args).await);
                acc
            })
            .await;

        let start = Instant::now();
        let results = join_all((0..*concurrent_tasks_count).map(|_| async {
            single_benchmark_task(
                &connections,
                counter.clone(),
                number_of_operations,
                *concurrent_tasks_count,
                args.data_size,
            )
            .await
        }))
        .await;
        let elapsed = start.elapsed();
        let combined_results = results.into_iter().fold(HashMap::new(), |mut acc, map| {
            if acc.is_empty() {
                return map;
            }
            for key in map.keys() {
                acc.get_mut(key)
                    .unwrap()
                    .extend_from_slice(map.get(key).unwrap());
            }

            acc
        });
        let mut results_json = HashMap::new();
        results_json.insert("client".to_string(), Value::String("glide".to_string()));
        results_json.insert(
            "num_of_tasks".to_string(),
            Value::Number((*concurrent_tasks_count).into()),
        );
        results_json.insert(
            "data_size".to_string(),
            Value::Number(args.data_size.into()),
        );
        results_json.insert(
            "tps".to_string(),
            Value::Number((number_of_operations as i64 * 1000 / elapsed.as_millis() as i64).into()),
        );
        results_json.insert(
            "client_count".to_string(),
            Value::Number(args.client_count.into()),
        );
        results_json.insert(
            "is_cluster".to_string(),
            Value::Bool(args.cluster_mode_enabled),
        );
        results_json.extend(calculate_latencies(
            combined_results.get(&ChosenAction::GetExisting).unwrap(),
            "get_existing",
        ));
        results_json.extend(calculate_latencies(
            combined_results.get(&ChosenAction::GetNonExisting).unwrap(),
            "get_non_existing",
        ));
        results_json.extend(calculate_latencies(
            combined_results.get(&ChosenAction::Set).unwrap(),
            "set",
        ));
        total_results.push(results_json);
    }

    std::fs::write(
        args.results_file,
        serde_json::to_string_pretty(&total_results).unwrap(),
    )
    .unwrap();
}

fn calculate_latencies(values: &[Duration], prefix: &str) -> HashMap<String, Value> {
    let values: Vec<f64> = values
        .iter()
        .map(|duration| duration.as_secs_f64() * 1000.0) // seconds -> ms
        .collect();
    let mut map = HashMap::new();
    map.insert(
        format!("{prefix}_p50_latency"),
        values[values.len() / 2].into(),
    );
    map.insert(
        format!("{prefix}_p90_latency"),
        values[values.len() / 100 * 90].into(),
    );
    map.insert(
        format!("{prefix}_p99_latency"),
        values[values.len() / 100 * 99].into(),
    );
    map.insert(
        format!("{prefix}_average_latency"),
        statistical::mean(values.as_slice()).into(),
    );
    map.insert(
        format!("{prefix}_std_dev"),
        statistical::standard_deviation(values.as_slice(), None).into(),
    );
    map
}

fn generate_random_string(length: usize) -> String {
    rand::thread_rng()
        .sample_iter(&rand::distributions::Alphanumeric)
        .take(length)
        .map(char::from)
        .collect()
}

async fn get_connection(args: &Args) -> Client {
    let address_info: NodeAddress = NodeAddress {
        host: args.host.clone(),
        port: args.port as u16,
    };
    let connection_request = ConnectionRequest {
        addresses: vec![address_info],
        cluster_mode_enabled: args.cluster_mode_enabled,
        request_timeout: Some(2000),
        tls_mode: if args.tls {
            Some(TlsMode::SecureTls)
        } else {
            Some(TlsMode::NoTls)
        },
        ..Default::default()
    };

    glide_core::client::Client::new(connection_request)
        .await
        .unwrap()
}

async fn single_benchmark_task(
    connections: &[Client],
    counter: Arc<AtomicUsize>,
    number_of_operations: usize,
    number_of_concurrent_tasks: usize,
    data_size: usize,
) -> HashMap<ChosenAction, Vec<Duration>> {
    let mut buffer = itoa::Buffer::new();
    let mut results = HashMap::new();
    results.insert(
        ChosenAction::GetNonExisting,
        Vec::with_capacity(number_of_operations / number_of_concurrent_tasks),
    );
    results.insert(
        ChosenAction::GetExisting,
        Vec::with_capacity(number_of_operations / number_of_concurrent_tasks),
    );
    results.insert(
        ChosenAction::Set,
        Vec::with_capacity(number_of_operations / number_of_concurrent_tasks),
    );
    loop {
        let current_op = counter.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        if current_op >= number_of_operations {
            return results;
        }
        let index = current_op % connections.len();
        let mut connection = connections[index].clone();
        let start = Instant::now();
        let action = perform_operation(&mut connection, &mut buffer, data_size).await;
        let elapsed = start.elapsed();
        results.get_mut(&action).unwrap().push(elapsed);
    }
}

async fn perform_operation(
    connection: &mut Client,
    buffer: &mut itoa::Buffer,
    data_size: usize,
) -> ChosenAction {
    let mut cmd = redis::Cmd::new();
    let action = if rand::thread_rng().gen_bool(PROB_GET) {
        if rand::thread_rng().gen_bool(PROB_GET_EXISTING_KEY) {
            cmd.arg("GET")
                .arg(buffer.format(thread_rng().gen_range(0..SIZE_SET_KEYSPACE)));
            ChosenAction::GetExisting
        } else {
            cmd.arg("GET")
                .arg(buffer.format(thread_rng().gen_range(SIZE_SET_KEYSPACE..SIZE_GET_KEYSPACE)));
            ChosenAction::GetNonExisting
        }
    } else {
        cmd.arg("SET")
            .arg(buffer.format(thread_rng().gen_range(0..SIZE_SET_KEYSPACE)))
            .arg(generate_random_string(data_size));
        ChosenAction::Set
    };
    connection.send_command(&cmd, None).await.unwrap();
    action
}
