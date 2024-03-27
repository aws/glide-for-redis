// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

using System.Collections.Concurrent;
using System.Diagnostics;
using System.Text.Json;

using CommandLine;

using Glide;

using LinqStatistics;

using StackExchange.Redis;

public static class MainClass
{
    private enum ChosenAction { GET_NON_EXISTING, GET_EXISTING, SET };

    public class CommandLineOptions
    {
        [Option('r', "resultsFile", Required = false, HelpText = "Set the file to which the JSON results are written.")]
        public string ResultsFile { get; set; } = "../results/csharp-results.json";

        [Option('d', "dataSize", Required = false, HelpText = "The size of the sent data in bytes.")]
        public int DataSize { get; set; } = 100;

        [Option('c', "concurrentTasks", Required = false, HelpText = "The number of concurrent operations to perform.", Default = new[] { 1, 10, 100, 1000 })]
        public IEnumerable<int> ConcurrentTasks { get; set; } = [];

        [Option('l', "clients", Required = false, HelpText = "Which clients should run")]
        public string ClientsToRun { get; set; } = "all";

        [Option('h', "host", Required = false, HelpText = "What host to target")]
        public string Host { get; set; } = "localhost";

        [Option('C', "clientCount", Required = false, HelpText = "Number of clients to run concurrently", Default = new[] { 1 })]
        public IEnumerable<int> ClientCount { get; set; } = [];

        [Option('t', "tls", HelpText = "Should benchmark a TLS server")]
        public bool Tls { get; set; } = false;


        [Option('m', "minimal", HelpText = "Should use a minimal number of actions")]
        public bool Minimal { get; set; } = false;
    }

    private const int PORT = 6379;
    private static string GetAddress(string host) => $"{host}:{PORT}";

    private static string GetAddressForStackExchangeRedis(string host, bool useTLS) => $"{GetAddress(host)},ssl={useTLS}";

    private static string GetAddressWithRedisPrefix(string host, bool useTLS)
    {
        string protocol = useTLS ? "rediss" : "redis";
        return $"{protocol}://{GetAddress(host)}";
    }
    private const double PROB_GET = 0.8;

    private const double PROB_GET_EXISTING_KEY = 0.8;
    private const int SIZE_GET_KEYSPACE = 3750000; // 3.75 million
    private const int SIZE_SET_KEYSPACE = 3000000; // 3 million

    private static readonly Random Randomizer = new();
    private static long s_started_tasks_counter = 0;
    private static readonly List<Dictionary<string, object>> BenchJsonResults = [];

    private static string GenerateValue(int size) => new('0', size);

    private static string GenerateKeySet() => (Randomizer.Next(SIZE_SET_KEYSPACE) + 1).ToString();
    private static string GenerateKeyGet() => (Randomizer.Next(SIZE_SET_KEYSPACE, SIZE_GET_KEYSPACE) + 1).ToString();

    private static ChosenAction ChooseAction() =>
        Randomizer.NextDouble() > PROB_GET
            ? ChosenAction.SET
            : Randomizer.NextDouble() > PROB_GET_EXISTING_KEY ? ChosenAction.GET_NON_EXISTING : ChosenAction.GET_EXISTING;

    /// copied from https://stackoverflow.com/questions/8137391/percentile-calculation
    private static double Percentile(double[] sequence, double excelPercentile)
    {
        Array.Sort(sequence);
        double n = ((sequence.Length - 1) * excelPercentile) + 1;
        if (n == 1d)
        {
            return sequence[0];
        }
        else if (n == sequence.Length)
        {
            return sequence[^1];
        }
        else
        {
            int k = (int)n;
            double d = n - k;
            return sequence[k - 1] + (d * (sequence[k] - sequence[k - 1]));
        }
    }

    private static double CalculateLatency(IEnumerable<double> latency_list, double percentile_point) => Math.Round(Percentile(latency_list.ToArray(), percentile_point), 2);

    private static void PrintResults(string resultsFile)
    {
        using FileStream createStream = File.Create(resultsFile);
        JsonSerializer.Serialize(createStream, BenchJsonResults);
    }

    private static async Task RedisBenchmark(
        ClientWrapper[] clients,
        long total_commands,
        string data,
        Dictionary<ChosenAction, ConcurrentBag<double>> action_latencies)
    {
        Stopwatch stopwatch = new();
        do
        {
            _ = Interlocked.Increment(ref s_started_tasks_counter);
            int index = (int)(s_started_tasks_counter % clients.Length);
            ClientWrapper client = clients[index];
            ChosenAction action = ChooseAction();
            stopwatch.Start();
            switch (action)
            {
                case ChosenAction.GET_EXISTING:
                    _ = await client.Get(GenerateKeySet());
                    break;
                case ChosenAction.GET_NON_EXISTING:
                    _ = await client.Get(GenerateKeyGet());
                    break;
                case ChosenAction.SET:
                    await client.Set(GenerateKeySet(), data);
                    break;
                default:
                    break;
            }
            stopwatch.Stop();
            ConcurrentBag<double> latency_list = action_latencies[action];
            latency_list.Add(((double)stopwatch.ElapsedMilliseconds) / 1000);
        } while (s_started_tasks_counter < total_commands);
    }

    private static async Task<long> CreateBenchTasks(
        ClientWrapper[] clients,
        int total_commands,
        string data,
        int num_of_concurrent_tasks,
        Dictionary<ChosenAction, ConcurrentBag<double>> action_latencies
    )
    {
        s_started_tasks_counter = 0;
        Stopwatch stopwatch = Stopwatch.StartNew();
        List<Task> running_tasks = [];
        for (int i = 0; i < num_of_concurrent_tasks; i++)
        {
            running_tasks.Add(
                RedisBenchmark(clients, total_commands, data, action_latencies)
            );
        }
        await Task.WhenAll(running_tasks);
        stopwatch.Stop();
        return stopwatch.ElapsedMilliseconds;
    }

    private static Dictionary<string, object> LatencyResults(
        string prefix,
        ConcurrentBag<double> latencies
    ) => new()
    {
            {prefix + "_p50_latency", CalculateLatency(latencies, 0.5)},
            {prefix + "_p90_latency", CalculateLatency(latencies, 0.9)},
            {prefix + "_p99_latency", CalculateLatency(latencies, 0.99)},
            {prefix + "_average_latency", Math.Round(latencies.Average(), 3)},
            {prefix + "_std_dev", latencies.StandardDeviation()},
        };

    private static async Task RunClients(
        ClientWrapper[] clients,
        string client_name,
        int total_commands,
        int data_size,
        int num_of_concurrent_tasks
    )
    {
        Console.WriteLine($"Starting {client_name} data size: {data_size} concurrency: {num_of_concurrent_tasks} client count: {clients.Length} {DateTime.UtcNow:HH:mm:ss}");
        Dictionary<ChosenAction, ConcurrentBag<double>> action_latencies = new() {
            {ChosenAction.GET_NON_EXISTING, new()},
            {ChosenAction.GET_EXISTING, new()},
            {ChosenAction.SET, new()},
        };
        string data = GenerateValue(data_size);
        long elapsed_milliseconds = await CreateBenchTasks(
            clients,
            total_commands,
            data,
            num_of_concurrent_tasks,
            action_latencies
        );
        double tps = Math.Round(s_started_tasks_counter / ((double)elapsed_milliseconds / 1000));

        ConcurrentBag<double> get_non_existing_latencies = action_latencies[ChosenAction.GET_NON_EXISTING];
        Dictionary<string, object> get_non_existing_latency_results = LatencyResults("get_non_existing", get_non_existing_latencies);

        ConcurrentBag<double> get_existing_latencies = action_latencies[ChosenAction.GET_EXISTING];
        Dictionary<string, object> get_existing_latency_results = LatencyResults("get_existing", get_existing_latencies);

        ConcurrentBag<double> set_latencies = action_latencies[ChosenAction.SET];
        Dictionary<string, object> set_latency_results = LatencyResults("set", set_latencies);

        Dictionary<string, object> result = new()
        {
            {"client", client_name},
            {"num_of_tasks", num_of_concurrent_tasks},
            {"data_size", data_size},
            {"tps", tps},
            {"client_count", clients.Length},
            {"is_cluster", "false"}
        };
        result = result
            .Concat(get_existing_latency_results)
            .Concat(get_non_existing_latency_results)
            .Concat(set_latency_results)
            .ToDictionary(pair => pair.Key, pair => pair.Value);
        BenchJsonResults.Add(result);
    }

    private class ClientWrapper : IDisposable
    {
        internal ClientWrapper(Func<string, Task<string?>> get, Func<string, string, Task> set, Action disposalFunction)
        {
            Get = get;
            Set = set;
            _disposalFunction = disposalFunction;
        }

        public void Dispose() => _disposalFunction();

        internal Func<string, Task<string?>> Get;
        internal Func<string, string, Task> Set;

        private readonly Action _disposalFunction;
    }

    private static async Task<ClientWrapper[]> CreateClients(int clientCount,
        Func<Task<(Func<string, Task<string?>>,
                   Func<string, string, Task>,
                   Action)>> clientCreation)
    {
        IEnumerable<Task<ClientWrapper>> tasks = Enumerable.Range(0, clientCount).Select(async (_) =>
        {
            (Func<string, Task<string?>>, Func<string, string, Task>, Action) tuple = await clientCreation();
            return new ClientWrapper(tuple.Item1, tuple.Item2, tuple.Item3);
        });
        return await Task.WhenAll(tasks);
    }

    private static async Task RunWithParameters(int total_commands,
        int data_size,
        int num_of_concurrent_tasks,
        string clientsToRun,
        string host,
        int clientCount,
        bool useTLS)
    {
        if (clientsToRun is "all" or "glide")
        {
            ClientWrapper[] clients = await CreateClients(clientCount, () =>
            {
                AsyncClient glide_client = new(host, PORT, useTLS);
                return Task.FromResult<(Func<string, Task<string?>>, Func<string, string, Task>, Action)>(
                    (async (key) => await glide_client.GetAsync(key),
                     async (key, value) => await glide_client.SetAsync(key, value),
                     () => glide_client.Dispose()));
            });

            await RunClients(
                clients,
                "glide",
                total_commands,
                data_size,
                num_of_concurrent_tasks
            );
        }

        if (clientsToRun == "all")
        {
            ClientWrapper[] clients = await CreateClients(clientCount, () =>
                {
                    ConnectionMultiplexer connection = ConnectionMultiplexer.Connect(GetAddressForStackExchangeRedis(host, useTLS));
                    IDatabase db = connection.GetDatabase();
                    return Task.FromResult<(Func<string, Task<string?>>, Func<string, string, Task>, Action)>(
                        (async (key) => await db.StringGetAsync(key),
                         async (key, value) => await db.StringSetAsync(key, value),
                         () => connection.Dispose()));
                });
            await RunClients(
                clients,
                "StackExchange.Redis",
                total_commands,
                data_size,
                num_of_concurrent_tasks
            );

            foreach (ClientWrapper client in clients)
            {
                client.Dispose();
            }
        }
    }

    private static int NumberOfIterations(int num_of_concurrent_tasks) => Math.Min(Math.Max(100000, num_of_concurrent_tasks * 10000), 10000000);

    public static async Task Main(string[] args)
    {
        CommandLineOptions options = new();
        _ = Parser.Default
            .ParseArguments<CommandLineOptions>(args).WithParsed(parsed => options = parsed);

        Logger.SetLoggerConfig(Level.Info, Path.GetFileNameWithoutExtension(options.ResultsFile));
        IEnumerable<(int concurrentTasks, int dataSize, int clientCount)> product = options.ConcurrentTasks.SelectMany(concurrentTasks =>
            options.ClientCount.Select(clientCount => (concurrentTasks, options.DataSize, clientCount))).Where(tuple => tuple.concurrentTasks >= tuple.clientCount);
        foreach ((int concurrentTasks, int dataSize, int clientCount) in product)
        {
            int iterations = options.Minimal ? 1000 : NumberOfIterations(concurrentTasks);
            await RunWithParameters(iterations, dataSize, concurrentTasks, options.ClientsToRun, options.Host, clientCount, options.Tls);
        }

        PrintResults(options.ResultsFile);
    }
}
