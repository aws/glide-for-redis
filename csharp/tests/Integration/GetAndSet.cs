/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */


using System.Runtime.InteropServices;

using Glide;

using static Tests.Integration.IntegrationTestBase;

namespace Tests.Integration;
public class GetAndSet
{
    private async Task GetAndSetRandomValues(AsyncClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        await client.SetAsync(key, value);
        string? result = await client.GetAsync(key);
        Assert.That(result, Is.EqualTo(value));
    }

    [Test]
    public async Task GetReturnsLastSet()
    {
        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);
        await GetAndSetRandomValues(client);
    }

    [Test]
    public async Task GetAndSetCanHandleNonASCIIUnicode()
    {
        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);
        string key = Guid.NewGuid().ToString();
        string value = "שלום hello 汉字";
        await client.SetAsync(key, value);
        string? result = await client.GetAsync(key);
        Assert.That(result, Is.EqualTo(value));
    }

    [Test]
    public async Task GetReturnsNull()
    {
        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);
        string? result = await client.GetAsync(Guid.NewGuid().ToString());
        Assert.That(result, Is.EqualTo(null));
    }

    [Test]
    public async Task GetReturnsEmptyString()
    {
        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);
        string key = Guid.NewGuid().ToString();
        string value = "";
        await client.SetAsync(key, value);
        string? result = await client.GetAsync(key);
        Assert.That(result, Is.EqualTo(value));
    }

    [Test]
    public async Task HandleVeryLargeInput()
    {
        if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
        {
            Assert.Ignore("Flaky on MacOS");
        }
        using AsyncClient client = new("localhost", TestConfiguration.STANDALONE_PORTS[0], false);
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        const int expectedSize = 2 << 23;
        while (value.Length < expectedSize)
        {
            value += value;
        }
        await client.SetAsync(key, value);
        string? result = await client.GetAsync(key);
        Assert.That(result, Is.EqualTo(value));
    }

    // This test is slow and hardly a unit test, but it caught timing and releasing issues in the past,
    // so it's being kept.
    [Test]
    public void ConcurrentOperationsWork()
    {
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
                        string? result = await client.GetAsync(Guid.NewGuid().ToString());
                        Assert.That(result, Is.EqualTo(null));
                    }
                }
            }));
        }

        Task.WaitAll([.. operations]);
    }
}
