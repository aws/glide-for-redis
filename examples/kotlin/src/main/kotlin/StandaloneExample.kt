package glide.examples

import glide.api.GlideClient
import glide.api.logging.Logger
import glide.api.models.configuration.GlideClientConfiguration
import glide.api.models.configuration.NodeAddress
import glide.api.models.exceptions.ClosingException
import glide.api.models.exceptions.ConnectionException
import glide.api.models.exceptions.ExecAbortException
import glide.api.models.exceptions.TimeoutException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking

object StandaloneExample {

    /**
     * Creates and returns a [GlideClient] instance.
     *
     * This function initializes a [GlideClient] with the provided list of nodes.
     * The [nodesList] may contain either only primary node or a mix of primary
     * and replica nodes. The [GlideClient] use these nodes to connect to
     * the Standalone setup servers.
     *
     * @param nodesList A list of pairs where each pair
     *     contains a host (String) and port (Int). Defaults to [("localhost", 6379)].
     *
     * @return An instance of [GlideClient] connected to the specified nodes.
     */
    private suspend fun createClient(nodesList: List<Pair<String, Int>> = listOf(Pair("localhost", 6379))): GlideClient {
        // Check `GlideClientConfiguration` for additional options.
        val config = GlideClientConfiguration.builder()
            .addresses(nodesList.map({ (host: String, port: Int) -> NodeAddress.builder().host(host).port(port).build() }))
            // Enable this field if the servers are configured with TLS.
            //.useTLS(true)
            .build()

        return GlideClient.createClient(config).await()
    }

    /**
     * Executes the main logic of the application, performing basic operations such as SET, GET, and
     * PING using the provided [GlideClient].
     *
     * @param client An instance of [GlideClient].
     */
    private suspend fun appLogic(client: GlideClient) {
        // Send SET and GET
        val setResponse = client.set("foo", "bar").await()
        Logger.log(Logger.Level.INFO, "app", "Set response is $setResponse")

        val getResponse = client.get("foo").await()
        Logger.log(Logger.Level.INFO, "app", "Get response is $getResponse")

        // Send PING to the primary node
        val pong = client.ping().await()
        Logger.log(Logger.Level.INFO, "app", "Ping response is $pong")
    }

    /**
     * Executes the application logic with exception handling.
     */
    private suspend fun execAppLogic() {
        while (true) {
            var client: GlideClient? = null
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
     * The entry point of the standalone example. This method sets up the logger configuration
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
