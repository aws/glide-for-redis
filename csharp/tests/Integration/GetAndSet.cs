// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.InteropServices;

using FluentAssertions;

using Glide;

using static Tests.Integration.IntegrationTestBase;

namespace Tests.Integration;
public class GetAndSet : IClassFixture<IntegrationTestBase>
{
    private async Task GetAndSetValues(AsyncClient client, string key, string value)
    {
        _ = (await client.SetAsync(key, value))
            .Should()
            .Be("OK");
        _ = (await client.GetAsync(key))
            .Should()
            .Be(value);
    }

    private async Task GetAndSetRandomValues(AsyncClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        await GetAndSetValues(client, key, value);
    }

    [Fact]
    public async Task GetReturnsLastSet()
    {
        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);
        await GetAndSetRandomValues(client);
    }

    [Fact]
    public async Task GetAndSetCanHandleNonASCIIUnicode()
    {
        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);
        string key = Guid.NewGuid().ToString();
        string value = "שלום hello 汉字";
        await GetAndSetValues(client, key, value);
    }

    [Fact]
    public async Task GetReturnsNull()
    {
        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);
        _ = (await client.GetAsync(Guid.NewGuid().ToString()))
            .Should()
            .BeNull();
    }

    [Fact]
    public async Task GetReturnsEmptyString()
    {
        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);
        string key = Guid.NewGuid().ToString();
        string value = string.Empty;
        await GetAndSetValues(client, key, value);
    }

    [Fact]
    public async Task HandleVeryLargeInput()
    {
        // TODO invesitage and fix
        if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
        {
            //"Flaky on MacOS"
            return;
        }
        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);

        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        const int expectedSize = 2 << 23;

        while (value.Length < expectedSize)
        {
            value += value;
        }
        await GetAndSetValues(client, key, value);
    }

    // This test is slow and hardly a unit test, but it caught timing and releasing issues in the past,
    // so it's being kept.
    [Fact]
    public void ConcurrentOperationsWork()
    {
        // TODO investigate and fix
        if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
        {
            return;
        }

        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);
        List<Task> operations = [];

        for (int i = 0; i < 1000; ++i)
        {
            int index = i;
            operations.Add(Task.Run(async () =>
            {
                for (int i = 0; i < 1000; ++i)
                {
                    if ((i + index) % 2 == 0)
                    {
                        await GetAndSetRandomValues(client);
                    }
                    else
                    {
                        _ = (await client.GetAsync(Guid.NewGuid().ToString()))
                            .Should()
                            .BeNull();
                    }
                }
            }));
        }

        Task.WaitAll([.. operations]);
    }
}
