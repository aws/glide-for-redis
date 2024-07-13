/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples;

import glide.api.GlideClient;
import glide.api.logging.Logger;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.TimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class StandaloneExample {

    /**
     * Creates and returns a GlideClient instance. This function initializes a
     * GlideClient with the provided list of nodes.
     *
     * @return A <code>GlideClient</code> connected to the discovered nodes.
     */
    public static GlideClient createClient() throws ExecutionException, InterruptedException {
        String host = "localhost";
        Integer port = 6379;

        // Check GlideClientConfiguration for additional options.
        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port).build())
                        .build();

        try {
            GlideClient client = GlideClient.createClient(config).get();
            return client;
        } catch (ExecutionException | InterruptedException e) {
            Logger.log(Logger.Level.ERROR, "glide", "Failed to create client: " + e.getMessage());
        }
        return null;
    }

    /**
     * Executes the main logic of the application, performing basic operations such as SET, GET, and
     * PING using the provided GlideClient.
     *
     * @param client An instance of <code>GlideClient</code>.
     */
    public static void appLogic(GlideClient client) {

        // Send SET and GET
        try {
            CompletableFuture<String> setResponse = client.set("foo", "bar");
            Logger.log(Logger.Level.INFO, "app", "Set response is " + setResponse.get());
        } catch (ExecutionException | InterruptedException e) {
            Logger.log(Logger.Level.ERROR, "glide", "Error during SET: " + e.getMessage());
        }

        try {
            CompletableFuture<String> getResponse = client.get("foo");
            Logger.log(Logger.Level.INFO, "app", "Get response is " + getResponse.get());
        } catch (ExecutionException | InterruptedException e) {
            Logger.log(Logger.Level.ERROR, "glide", "Error during GET: " + e.getMessage());
        }

        // Send PING to the primary node
        try {
            CompletableFuture<String> pong = client.ping();
            Logger.log(Logger.Level.INFO, "app", "Ping response is " + pong.get());
        } catch (ExecutionException | InterruptedException e) {
            Logger.log(Logger.Level.ERROR, "glide", "Error during PING: " + e.getMessage());
        }
    }

    /** Executes the application logic with exception handling. */
    private static void execAppLogic() {

        while (true) {
            GlideClient client = null;
            try {
                client = createClient();

                assert client != null;
                appLogic(client);
                return;

            } catch (ClosingException e) {
                // If the error message contains "NOAUTH", raise the exception
                // because it indicates a critical authentication issue.
                if (e.getMessage().contains("NOAUTH")) {
                    Logger.log(
                            Logger.Level.ERROR, "glide", "Authentication error encountered: " + e.getMessage());
                    throw e;
                } else {
                    Logger.log(
                            Logger.Level.WARN,
                            "glide",
                            "Client has closed and needs to be re-created: " + e.getMessage());
                }
            } catch (TimeoutException e) {
                // A request timed out. You may choose to retry the execution based on your application's
                // logic
                Logger.log(Logger.Level.ERROR, "glide", "TimeoutError encountered: " + e.getMessage());
            } catch (ConnectionException e) {
                // The client wasn't able to reestablish the connection within the given retries
                Logger.log(Logger.Level.ERROR, "glide", "ConnectionError encountered: " + e.getMessage());
            } catch (Exception e) {
                Logger.log(Logger.Level.ERROR, "glide", "Unexpected error: " + e.getMessage());

            } finally {
                if (client != null) {
                    try {
                        client.close();
                    } catch (Exception e) {
                        Logger.log(
                                Logger.Level.ERROR,
                                "glide",
                                "Error encountered while closing the client: " + e.getMessage());
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        // In this example, we will utilize the client's logger for all log messages
        Logger.setLoggerConfig(Logger.Level.INFO);
        // Optional - set the logger to write to a file
        // Logger.setLoggerConfig(Logger.Level.INFO, file)
        execAppLogic();
    }
}
