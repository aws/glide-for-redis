import percentile from "percentile";
import { stdev } from "stats-lite";
import { createClient } from "redis";
import { AsyncClient, SocketConnection } from "babushka-rs";
import commandLineArgs from "command-line-args";
import { writeFileSync } from "fs";

enum ChosenAction {
    GET_NON_EXISTING,
    GET_EXISTING,
    SET,
}

const HOST = "localhost";
const PORT = 6379;
const ADDRESS = `redis://${HOST}:${PORT}`;
const PROB_GET = 0.8;
const PROB_GET_EXISTING_KEY = 0.8;
const SIZE_GET_KEYSPACE = 3750000; // 3.75 million
const SIZE_SET_KEYSPACE = 3000000; // 3 million

let counter = 0;
const running_tasks: Promise<void>[] = [];
const bench_str_results: string[] = [];
const bench_json_results: {}[] = [];
const action_latencies: Record<ChosenAction, Record<string, number[]>> = {
    [ChosenAction.SET]: {},
    [ChosenAction.GET_NON_EXISTING]: {},
    [ChosenAction.GET_EXISTING]: {},
};

interface IAsyncClient {
    set: (key: string, value: string) => Promise<any>;
    get: (key: string) => Promise<string | null>;
}

function generate_value(size: number): string {
    return "0".repeat(size);
}

function generate_key_set(): string {
    return (Math.floor(Math.random() * SIZE_SET_KEYSPACE) + 1).toString();
}
function generate_key_get(): string {
    const range = SIZE_GET_KEYSPACE - SIZE_SET_KEYSPACE;
    return Math.floor(Math.random() * range + SIZE_SET_KEYSPACE + 1).toString();
}

function choose_action(): ChosenAction {
    if (Math.random() > PROB_GET) {
        return ChosenAction.SET;
    }
    if (Math.random() > PROB_GET_EXISTING_KEY) {
        return ChosenAction.GET_NON_EXISTING;
    }
    return ChosenAction.GET_EXISTING;
}

function calculate_latency(latency_list: number[], percentile_point: number) {
    const percentile_calculation = percentile(percentile_point, latency_list);
    const percentile_value = Array.isArray(percentile_calculation)
        ? percentile_calculation[0]
        : percentile_calculation;
    return Math.round(percentile_value * 100.0) / 100.0; // round to 2 decimal points
}

function print_results(resultsFile: string) {
    bench_str_results.sort();
    for (const res of bench_str_results) {
        console.log(res);
    }
    writeFileSync(resultsFile, JSON.stringify(bench_json_results));
}

async function redis_benchmark(
    client: IAsyncClient,
    client_name: string,
    total_commands: number,
    data: string
) {
    while (counter < total_commands) {
        const chosen_action = choose_action();
        let tic = process.hrtime();
        switch (chosen_action) {
            case ChosenAction.GET_EXISTING:
                await client.get(generate_key_set());
                break;
            case ChosenAction.GET_NON_EXISTING:
                await client.get(generate_key_get());
                break;
            case ChosenAction.SET:
                await client.set(generate_key_set(), data);
                break;
        }
        let toc = process.hrtime(tic);
        const latency_list = action_latencies[chosen_action][client_name];
        latency_list.push(toc[0] * 1000 + toc[1] / 1000000);
        counter += 1;
    }
}

async function create_bench_tasks(
    client: IAsyncClient,
    client_name: string,
    total_commands: number,
    num_of_concurrent_tasks: number,
    data: string
) {
    counter = 0;
    action_latencies[ChosenAction.GET_EXISTING][client_name] = [];
    action_latencies[ChosenAction.GET_NON_EXISTING][client_name] = [];
    action_latencies[ChosenAction.SET][client_name] = [];
    let tic = process.hrtime();
    for (let i = 0; i < num_of_concurrent_tasks; i++) {
        running_tasks.push(
            redis_benchmark(client, client_name, total_commands, data)
        );
    }
    await Promise.all(running_tasks);
    let toc = process.hrtime(tic);
    return toc[0] + toc[1] / 1000000000;
}

async function run_client(
    client: IAsyncClient,
    client_name: string,
    total_commands: number,
    num_of_concurrent_tasks: number,
    data_size: number,
    data: string
) {
    const time = await create_bench_tasks(
        client,
        client_name,
        total_commands,
        num_of_concurrent_tasks,
        data
    );
    const tps = Math.round(counter / time);

    const get_non_existing_latency =
        action_latencies[ChosenAction.GET_NON_EXISTING];
    const get_non_existing_p50_latency = calculate_latency(
        get_non_existing_latency[client_name],
        50
    );
    const get_non_existing_p90_latency = calculate_latency(
        get_non_existing_latency[client_name],
        90
    );
    const get_non_existing_p99_latency = calculate_latency(
        get_non_existing_latency[client_name],
        99
    );
    const get_non_existing_std_dev = stdev(
        get_non_existing_latency[client_name]
    );

    const get_existing_latency = action_latencies[ChosenAction.GET_EXISTING];
    const get_existing_p50_latency = calculate_latency(
        get_existing_latency[client_name],
        50
    );
    const get_existing_p90_latency = calculate_latency(
        get_existing_latency[client_name],
        90
    );
    const get_existing_p99_latency = calculate_latency(
        get_existing_latency[client_name],
        99
    );
    const get_existing_std_dev = stdev(get_existing_latency[client_name]);

    const set_latency = action_latencies[ChosenAction.SET];
    const set_p50_latency = calculate_latency(set_latency[client_name], 50);
    const set_p90_latency = calculate_latency(set_latency[client_name], 90);
    const set_p99_latency = calculate_latency(set_latency[client_name], 99);
    const set_std_dev = stdev(set_latency[client_name]);

    const json_res = {
        client: client_name,
        num_of_tasks: num_of_concurrent_tasks,
        data_size,
        tps,
        get_non_existing_p50_latency,
        get_non_existing_p90_latency,
        get_non_existing_p99_latency,
        get_non_existing_std_dev,
        get_existing_p50_latency,
        get_existing_p90_latency,
        get_existing_p99_latency,
        get_existing_std_dev,
        set_p50_latency,
        set_p90_latency,
        set_p99_latency,
        set_std_dev,
    };
    bench_json_results.push(json_res);
    bench_str_results.push(
        `client: ${client_name}, concurrent_tasks: ${num_of_concurrent_tasks}, data_size: ${data_size}, TPS: ${tps}, ` +
            `get_non_existing_p50: ${get_non_existing_p50_latency}, get_non_existing_p90: ${get_non_existing_p90_latency}, get_non_existing_p99: ${get_non_existing_p99_latency}, get_non_existing_std_dev: ${get_non_existing_std_dev}, ` +
            `get_existing_p50: ${get_existing_p50_latency}, get_existing_p90: ${get_existing_p90_latency}, get_existing_p99: ${get_existing_p99_latency}, get_existing_std_dev: ${get_existing_std_dev}, ` +
            `set_p50: ${set_p50_latency}, set_p90: ${set_p90_latency}, set_p99: ${set_p99_latency}, set_std_dev: ${set_std_dev}`
    );
}

async function main(
    total_commands: number,
    num_of_concurrent_tasks: number,
    data_size: number,
    clients_to_run: "all" | "ffi" | "socket" | "babushka"
) {
    const data = generate_value(data_size);
    if (
        clients_to_run == "ffi" ||
        clients_to_run == "all" ||
        clients_to_run == "babushka"
    ) {
        const babushka_client = await AsyncClient.CreateConnection(ADDRESS);
        await run_client(
            babushka_client,
            "babushka FFI",
            total_commands,
            num_of_concurrent_tasks,
            data_size,
            data
        );
    }

    if (
        clients_to_run == "socket" ||
        clients_to_run == "all" ||
        clients_to_run == "babushka"
    ) {
        const babushka_socket_client = await SocketConnection.CreateConnection(
            ADDRESS
        );
        await run_client(
            babushka_socket_client,
            "babushka socket",
            total_commands,
            num_of_concurrent_tasks,
            data_size,
            data
        );
        babushka_socket_client.dispose();
        await new Promise((resolve) => setTimeout(resolve, 100));
    }

    if (clients_to_run == "all") {
        const node_redis_client = createClient({ url: ADDRESS });
        await node_redis_client.connect();
        await run_client(
            node_redis_client,
            "node_redis",
            total_commands,
            num_of_concurrent_tasks,
            data_size,
            data
        );
    }
}

const optionDefinitions = [
    { name: "resultsFile", type: String },
    { name: "dataSize", type: String, multiple: true },
    { name: "concurrentTasks", type: String, multiple: true },
    { name: "clients", type: String },
];
const receivedOptions = commandLineArgs(optionDefinitions);

const number_of_iterations = (num_of_concurrent_tasks: number) =>
    Math.max(100000, num_of_concurrent_tasks * 10000);

Promise.resolve() // just added to clean the indentation of the rest of the calls
    .then(async () => {
        const data_sizes: string[] = receivedOptions.dataSize;
        const concurrent_tasks: string[] = receivedOptions.concurrentTasks;
        const clients_to_run = receivedOptions.clients;
        const product = data_sizes.flatMap((dataSize: string) =>
            concurrent_tasks.map((concurrentTasks: string) => [
                parseInt(concurrentTasks),
                parseInt(dataSize),
            ])
        );
        for (let [concurrent_tasks, data_size] of product) {
            await main(
                number_of_iterations(concurrent_tasks),
                concurrent_tasks,
                data_size,
                clients_to_run
            );
        }

        print_results(receivedOptions.resultsFile);
    })
    .then(() => {
        process.exit(0);
    });
