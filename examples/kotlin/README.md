## Run
Ensure that you have a server running on "localhost" on port "6379". To run the ClusterExample, make sure that the server has cluster mode enabled. If the server is running on a different host and/or port, update the StandaloneExample or ClusterExample with a configuration that matches your server settings.

To run the Standalone example:
```shell
cd valkey-glide/examples/kotlin
./gradlew runStandalone
```

To run the Cluster example:
```shell
cd valkey-glide/examples/kotlin
./gradlew runCluster
```

## Version
These examples are running `valkey-glide` version `1.+`. In order to change the version, update the following section in the `build.gradle.kts` file:
```kotlin
dependencies {
    implementation("io.valkey:valkey-glide:1.+:$classifier")
}
```
