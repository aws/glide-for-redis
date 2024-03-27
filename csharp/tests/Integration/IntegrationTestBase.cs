// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

// Note: All IT should be in the same namespace
namespace Tests.Integration;

[SetUpFixture]
public class IntegrationTestBase
{
    internal class TestConfiguration
    {
        public static List<uint> STANDALONE_PORTS { get; internal set; } = [];
        public static List<uint> CLUSTER_PORTS { get; internal set; } = [];
        public static Version REDIS_VERSION { get; internal set; } = new();
    }

    [OneTimeSetUp]
    public void SetUp()
    {
        // Stop all if weren't stopped on previous test run
        StopRedis(false);

        // Delete dirs if stop failed due to https://github.com/aws/glide-for-redis/issues/849
        Directory.Delete(Path.Combine(_scriptDir, "clusters"), true);

        // Start cluster
        TestConfiguration.CLUSTER_PORTS = StartRedis(true);
        // Start standalone
        TestConfiguration.STANDALONE_PORTS = StartRedis(false);
        // Get redis version
        TestConfiguration.REDIS_VERSION = GetRedisVersion();

        TestContext.Progress.WriteLine($"Cluster ports = {string.Join(',', TestConfiguration.CLUSTER_PORTS)}");
        TestContext.Progress.WriteLine($"Standalone ports = {string.Join(',', TestConfiguration.STANDALONE_PORTS)}");
        TestContext.Progress.WriteLine($"Redis version = {TestConfiguration.REDIS_VERSION}");
    }

    [OneTimeTearDown]
    public void TearDown() =>
        // Stop all
        StopRedis(true);

    private readonly string _scriptDir;

    // Nunit requires a public default constructor. These variables would be set in SetUp method.
    public IntegrationTestBase()
    {
        string? projectDir = Directory.GetCurrentDirectory();
        while (!(Path.GetFileName(projectDir) == "csharp" || projectDir == null))
        {
            projectDir = Path.GetDirectoryName(projectDir);
        }

        if (projectDir == null)
        {
            throw new FileNotFoundException("Can't detect the project dir. Are you running tests from `csharp` directory?");
        }

        _scriptDir = Path.Combine(projectDir, "..", "utils");
    }

    internal List<uint> StartRedis(bool cluster, bool tls = false, string? name = null)
    {
        string cmd = $"start {(cluster ? "--cluster-mode" : "-r 0")} {(tls ? " --tls" : "")} {(name != null ? " --prefix " + name : "")}";
        return ParsePortsFromOutput(RunClusterManager(cmd, false));
    }

    /// <summary>
    /// Stop <b>all</b> instances on the given <paramref name="name"/>.
    /// </summary>
    internal void StopRedis(bool keepLogs, string? name = null)
    {
        string cmd = $"stop --prefix {name ?? "redis-cluster"} {(keepLogs ? "--keep-folder" : "")}";
        _ = RunClusterManager(cmd, true);
    }

    private string RunClusterManager(string cmd, bool ignoreExitCode)
    {
        ProcessStartInfo info = new()
        {
            WorkingDirectory = _scriptDir,
            FileName = "python3",
            Arguments = "cluster_manager.py " + cmd,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        Process? script = Process.Start(info);
        script?.WaitForExit();
        string? error = script?.StandardError.ReadToEnd();
        string? output = script?.StandardOutput.ReadToEnd();
        int? exit_code = script?.ExitCode;

        TestContext.Progress.WriteLine($"cluster_manager.py stdout\n====\n{output}\n====\ncluster_manager.py stderr\n====\n{error}\n====\n");

        return !ignoreExitCode && exit_code != 0
            ? throw new ApplicationException($"cluster_manager.py script failed: exit code {exit_code}.")
            : output ?? "";
    }

    private static List<uint> ParsePortsFromOutput(string output)
    {
        List<uint> ports = [];
        foreach (string line in output.Split("\n"))
        {
            if (!line.StartsWith("CLUSTER_NODES="))
            {
                continue;
            }

            string[] addresses = line.Split("=")[1].Split(",");
            foreach (string address in addresses)
            {
                ports.Add(uint.Parse(address.Split(":")[1]));
            }
        }
        return ports;
    }

    private static Version GetRedisVersion()
    {
        ProcessStartInfo info = new()
        {
            FileName = "redis-server",
            Arguments = "-v",
            UseShellExecute = false,
            RedirectStandardOutput = true,
        };
        Process? proc = Process.Start(info);
        proc?.WaitForExit();
        string output = proc?.StandardOutput.ReadToEnd() ?? "";

        // Redis server v=7.2.3 sha=00000000:0 malloc=jemalloc-5.3.0 bits=64 build=7504b1fedf883f2
        return new Version(output.Split(" ")[2].Split("=")[1]);
    }
}
