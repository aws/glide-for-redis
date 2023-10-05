# Summary - Java Wrapper

This module contains a Java-client wrapper that connects to the `Babushka`-rust-client. The rust client connects to
redis, while this wrapper provides Java-language binding. The objective of this wrapper is to provide a thin-wrapper
language api to enhance performance and limit cpu cycles at scale. 

## Organization

The Java client (javababushka) contains the following parts:

1. A Java client (lib folder): wrapper to rust-client.
2. An examples script: to sanity test javababushka and similar java-clients against a redis host.
3. A benchmark app: to performance benchmark test javababushka and similar java-clients against a redis host.

## Building

You can assemble the Java clients benchmarks by compiling using `./gradlew build`. 

## Code style

Code style is enforced by spotless with Google Java Format. The build fails if code formatted incorrectly, but you can auto-format code with `./gradlew spotlessApply`.
Run this command before every commit to keep code in the same style.
These IDE plugins can auto-format code on file save or by single click:
* [For Intellij IDEA](https://plugins.jetbrains.com/plugin/18321-spotless-gradle)
* [For VS Code](https://marketplace.visualstudio.com/items?itemName=richardwillis.vscode-spotless-gradle)

## Benchmarks

You can run benchmarks using `./gradlew run`. You can set arguments using the args flag like:

```shell
./gradlew run --args="--clients lettuce"
```

The following arguments are accepted: 
* `resultsFile`: the results output file
* `concurrentTasks`: Number of concurrent tasks
* `clients`: one of: all|jedis|lettuce|babushka
* `clientCount`: Client count
* `host`: redis server host url
* `port`: redis server port number
* `tls`: redis TLS configured

### Troubleshooting

* Connection Timeout: 
  * If you're unable to connect to redis, check that you are connecting to the correct host, port, and TLS configuration.
* Only server-side certificates are supported by the TLS configured redis.

