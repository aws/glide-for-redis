import glide.api.GlideClusterClient
import glide.api.logging.Logger
import glide.api.models.commands.InfoOptions
import glide.api.models.configuration.{GlideClusterClientConfiguration, NodeAddress}
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES
import glide.api.models.exceptions.{ClosingException, ConnectionException, ExecAbortException, TimeoutException}

import scala.concurrent.{Await, CancellationException, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.util.{Failure, Try}

object ClusterExample {

    /**
     * Creates and returns a <code>GlideClusterClient</code> instance.
     *
     * This function initializes a <code>GlideClusterClient</code> with the provided list of nodes.
     * The <code>nodesList</code> may contain the address of one or more cluster nodes, and the
     * client will automatically discover all nodes in the cluster.
     *
     * @param nodesList A list of pairs where each pair
     *     contains a host (String) and port (Int). Defaults to [("localhost", 6379)].
     *
     * @return An instance of <code>Future[GlideClusterClient]</code> connected to the specified nodes.
     */
    private def createClient(nodesList: List[(String, Int)] = List(("localhost", 6379))): Future[GlideClusterClient] = {
        // Check `GlideClientConfiguration` for additional options.
        val config = GlideClusterClientConfiguration.builder()
            .addresses(nodesList.map((host, port) => NodeAddress.builder().host(host).port(port).build()).asJava)
            // Enable this field if the servers are configured with TLS.
            //.useTLS(true)
            .build()
            // This cast is required in order to pass the config to createClient because the Scala type system
            // is unable to resolve the Lombok builder result type.
            .asInstanceOf[GlideClusterClientConfiguration]

        GlideClusterClient.createClient(config).asScala
    }

    /**
     * Executes the main logic of the application, performing basic operations
     * such as SET, GET, PING, and INFO REPLICATION using the provided <code>GlideClusterClient</code>.
     *
     * @param client An instance of <code>GlideClusterClient</code>.
     */
    private def appLogic(client: GlideClusterClient): Future[Unit] = {
        for {
            // Send SET and GET
            setResponse <- client.set("foo", "bar").asScala
            _ = Logger.log(Logger.Level.INFO, "app", s"Set response is $setResponse")

            getResponse <- client.get("foo").asScala
            _ = Logger.log(Logger.Level.INFO, "app", s"Get response is $getResponse")

            // Send PING to all primaries (according to Valkey's PING request_policy)
            pong <- client.ping().asScala
            _ = Logger.log(Logger.Level.INFO, "app", s"Ping response is $pong")

            // Send INFO REPLICATION with routing option to all nodes
            infoReplResps <- client.info(
                InfoOptions.builder()
                    .section(InfoOptions.Section.REPLICATION)
                    .build(),
                ALL_NODES
            ).asScala
            _ = Logger.log(
                Logger.Level.INFO,
                "app",
                "INFO REPLICATION responses from all nodes are=\n$infoReplResps",
            )
        } yield ()
    }

    /**
     * Executes the application logic with exception handling.
     */
    private def execAppLogic(): Future[Unit] = {
        def loop(): Future[Unit] = {
            createClient().flatMap(client => appLogic(client).andThen {
                case _ => Try(client.close()) match {
                    case Failure(e) =>
                        Logger.log(
                            Logger.Level.WARN,
                            "glide",
                            s"Encountered an error while closing the client: ${e.getCause}"
                        )
                    case _ => ()
                }
            }).recoverWith {
                case e: CancellationException =>
                    Logger.log(Logger.Level.ERROR, "glide", s"Request cancelled: ${e.getMessage}")
                    Future.failed(e)
                case e: Exception => e.getCause match {
                    case e: ClosingException if e.getMessage.contains("NOAUTH") =>
                        // If the error message contains "NOAUTH", raise the exception
                        // because it indicates a critical authentication issue.
                        Logger.log(Logger.Level.ERROR, "glide", s"Authentication error encountered: ${e.getMessage}")
                        Future.failed(e)
                    case e: ClosingException =>
                        Logger.log(Logger.Level.WARN, "glide", s"Client has closed and needs to be re-created: ${e.getMessage}")
                        loop()
                    case e: TimeoutException =>
                        // A request timed out. You may choose to retry the execution based on your application's logic
                        Logger.log(Logger.Level.ERROR, "glide", s"Timeout encountered: ${e.getMessage}")
                        Future.failed(e)
                    case e: ConnectionException =>
                        // The client wasn't able to reestablish the connection within the given retries
                        Logger.log(Logger.Level.ERROR, "glide", s"Connection error encountered: ${e.getMessage}")
                        Future.failed(e)
                    case _ =>
                        Logger.log(Logger.Level.ERROR, "glide", s"Execution error encountered: ${e.getCause}")
                        Future.failed(e)
                }
            }
        }

        loop()
    }

    /**
     * The entry point of the standalone example. This method sets up the logger configuration
     * and executes the main application logic.
     *
     * @param args Command-line arguments passed to the application.
     */
    def main(args: Array[String]): Unit = {
        // In this example, we will utilize the client's logger for all log messages
        Logger.setLoggerConfig(Logger.Level.INFO)
        // Optional - set the logger to write to a file
        // Logger.setLoggerConfig(Logger.Level.INFO, file)

        // Await is used only for this example. Not recommended for use in production environments.
        Await.result(execAppLogic(), Duration.Inf)
    }
}
