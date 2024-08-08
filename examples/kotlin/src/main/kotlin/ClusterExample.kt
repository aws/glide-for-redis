package glide.examples

import glide.api.GlideClusterClient
import glide.api.logging.Logger
import glide.api.models.commands.InfoOptions
import glide.api.models.configuration.GlideClusterClientConfiguration
import glide.api.models.configuration.NodeAddress
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES
import glide.api.models.exceptions.ClosingException
import glide.api.models.exceptions.ConnectionException
import glide.api.models.exceptions.ExecAbortException
import glide.api.models.exceptions.TimeoutException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking

object ClusterExample {

    /**
     * Creates and returns a [GlideClusterClient] instance.
     *
     * This function initializes a [GlideClusterClient] with the provided list of nodes.
     * The [nodesList] may contain the address of one or more cluster nodes, and the
     * client will automatically discover all nodes in the cluster.
     *
     * @param nodesList A list of pairs where each pair
     *     contains a host (String) and port (Int). Defaults to [("localhost", 6379)].
     *
     * @return An instance of [GlideClusterClient] connected to the discovered nodes.
     */
    private suspend fun createClient(nodesList: List<Pair<String, Int>> = listOf(Pair("localhost", 6379))): GlideClusterClient {
        // Check `GlideClusterClientConfiguration` for additional options.
        val config = GlideClusterClientConfiguration.builder()
            .addresses(nodesList.map({ (host: String, port: Int) -> NodeAddress.builder().host(host).port(port).build() }))
            .clientName("test_cluster_client")
            // Enable this field if the servers are configured with TLS.
            //.useTLS(true)
            .build()

        return GlideClusterClient.createClient(config).await()
    }

    /**
     * Executes the main logic of the application, performing basic operations
     * such as SET, GET, PING, and INFO REPLICATION using the provided [GlideClusterClient].
     *
     * @param client An instance of [GlideClusterClient].
     */
    private suspend fun appLogic(client: GlideClusterClient) {
        // Send SET and GET
        val setResponse = client.set("foo", "bar").await()
        Logger.log(Logger.Level.INFO, "app", "Set response is $setResponse")

        val getResponse = client.get("foo").await()
        Logger.log(Logger.Level.INFO, "app", "Get response is $getResponse")

        // Send PING to all primaries (according to Valkey's PING request_policy)
        val pong = client.ping().await()
        Logger.log(Logger.Level.INFO, "app", "Ping response is $pong")

        // Send INFO REPLICATION with routing option to all nodes
        val infoReplResps = client.info(
            InfoOptions.builder()
                .section(InfoOptions.Section.REPLICATION)
                .build(),
            ALL_NODES
        ).await()
        Logger.log(
            Logger.Level.INFO,
            "app",
            "INFO REPLICATION responses from all nodes are=\n$infoReplResps",
        )
    }

    /**
     * Executes the application logic with exception handling.
     */
    private suspend fun execAppLogic() {
        while (true) {
            var client: GlideClusterClient? = null
            try {
                client = createClient()
                return appLogic(client)
            } catch (e: CancellationException) {
                Logger.log(Logger.Level.ERROR, "glide", "Request cancelled: ${e.message}")
                throw e
            } catch (e: Exception) {
                when (e) {
                    is ClosingException -> {
                        // If the error message contains "NOAUTH", raise the exception
                        // because it indicates a critical authentication issue.
                        if (e.message?.contains("NOAUTH") == true) {
                            Logger.log(Logger.Level.ERROR, "glide", "Authentication error encountered: ${e.message}")
                            throw e
                        } else {
                            Logger.log(Logger.Level.WARN, "glide", "Client has closed and needs to be re-created: ${e.message}")
                        }
                    }
                    is TimeoutException -> {
                        // A request timed out. You may choose to retry the execution based on your application's logic
                        Logger.log(Logger.Level.ERROR, "glide", "Timeout encountered: ${e.message}")
                        throw e
                    }
                    is ConnectionException -> {
                        // The client wasn't able to reestablish the connection within the given retries
                        Logger.log(Logger.Level.ERROR, "glide", "Connection error encountered: ${e.message}")
                        throw e
                    }
                    else -> {
                        Logger.log(Logger.Level.ERROR, "glide", "Execution error encountered: ${e.cause}")
                        throw e
                    }
                }
            } finally {
                try {
                    client?.close()
                } catch (e: Exception) {
                    Logger.log(
                        Logger.Level.WARN,
                        "glide",
                        "Encountered an error while closing the client: ${e.cause}"
                    )
                }
            }
        }
    }

    /**
     * The entry point of the cluster example. This method sets up the logger configuration
     * and executes the main application logic.
     *
     * @param args Command-line arguments passed to the application.
     */
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        // In this example, we will utilize the client's logger for all log messages
        Logger.setLoggerConfig(Logger.Level.INFO)
        // Optional - set the logger to write to a file
        // Logger.setLoggerConfig(Logger.Level.INFO, file)
        execAppLogic()
    }
}
