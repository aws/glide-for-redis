/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks;

import static glide.benchmarks.utils.Benchmarking.testClientSetGet;

import glide.benchmarks.clients.glide.GlideAsyncClient;
import glide.benchmarks.clients.jedis.JedisClient;
import glide.benchmarks.clients.lettuce.LettuceAsyncClient;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/** Benchmarking app for reporting performance of various Redis Java-clients */
public class BenchmarkingApp {

    // main application entrypoint
    public static void main(String[] args) {

        // create the parser
        CommandLineParser parser = new DefaultParser();
        Options options = getOptions();
        RunConfiguration runConfiguration = new RunConfiguration();
        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);

            // generate the help statement
            if (line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("glide", options);
                return;
            }

            runConfiguration = verifyOptions(line);
        } catch (ParseException exp) {
            // oops, something went wrong
            System.err.println("Parsing failed. Reason: " + exp.getMessage());
        }

        for (ClientName client : runConfiguration.clients) {
            switch (client) {
                case JEDIS:
                    System.out.println("Run JEDIS sync client");
                    testClientSetGet(JedisClient::new, runConfiguration, false);
                    break;
                case LETTUCE:
                    System.out.println("Run LETTUCE async client");
                    testClientSetGet(LettuceAsyncClient::new, runConfiguration, true);
                    break;
                case GLIDE:
                    System.out.println("GLIDE for Redis async client");
                    testClientSetGet(GlideAsyncClient::new, runConfiguration, true);
                    break;
            }
        }
    }

    private static Options getOptions() {
        // create the Options
        Options options = new Options();

        options.addOption(Option.builder("h").longOpt("help").desc("Print this message").build());
        options.addOption(
                Option.builder()
                        .longOpt("configuration")
                        .hasArg(true)
                        .desc("Configuration flag [Release]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("resultsFile")
                        .hasArg(true)
                        .desc("Result filepath (stdout if empty) []")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("dataSize")
                        .hasArg(true)
                        .desc("Data block size [100 4000]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("concurrentTasks")
                        .hasArg(true)
                        .desc("Number of concurrent tasks [100, 1000]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("clients")
                        .hasArg(true)
                        .desc("one of: all|jedis|lettuce|glide")
                        .build());
        options.addOption(
                Option.builder().longOpt("host").hasArg(true).desc("Hostname [localhost]").build());
        options.addOption(
                Option.builder().longOpt("port").hasArg(true).desc("Port number [6379]").build());
        options.addOption(
                Option.builder()
                        .longOpt("clientCount")
                        .hasArg(true)
                        .desc("Number of clients to run [1]")
                        .build());
        options.addOption(Option.builder().longOpt("tls").hasArg(false).desc("TLS [false]").build());
        options.addOption(
                Option.builder()
                        .longOpt("clusterModeEnabled")
                        .hasArg(false)
                        .desc("Is cluster-mode enabled, other standalone mode is used [false]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("minimal")
                        .hasArg(false)
                        .desc("Run benchmark in minimal mode")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("debugLogging")
                        .hasArg(false)
                        .desc("Verbose logs [false]")
                        .build());

        return options;
    }

    private static RunConfiguration verifyOptions(CommandLine line) throws ParseException {
        RunConfiguration runConfiguration = new RunConfiguration();

        if (line.hasOption("configuration")) {
            String configuration = line.getOptionValue("configuration");
            if (configuration.equalsIgnoreCase("Release") || configuration.equalsIgnoreCase("Debug")) {
                runConfiguration.configuration = configuration;
            } else {
                throw new ParseException(
                        "Invalid run configuration (" + configuration + "), must be (Release|Debug)");
            }
        }

        if (line.hasOption("resultsFile")) {
            runConfiguration.resultsFile = Optional.ofNullable(line.getOptionValue("resultsFile"));
        }

        if (line.hasOption("dataSize")) {
            runConfiguration.dataSize = parseIntListOption(line.getOptionValue("dataSize"));
        }

        if (line.hasOption("concurrentTasks")) {
            runConfiguration.concurrentTasks = parseIntListOption(line.getOptionValue("concurrentTasks"));
        }

        if (line.hasOption("clients")) {
            String[] clients = line.getOptionValue("clients").split(",");
            runConfiguration.clients =
                    Arrays.stream(clients)
                            .map(c -> Enum.valueOf(ClientName.class, c.toUpperCase()))
                            .flatMap(
                                    e -> {
                                        switch (e) {
                                            case ALL:
                                                return Stream.of(ClientName.JEDIS, ClientName.GLIDE, ClientName.LETTUCE);
                                            default:
                                                return Stream.of(e);
                                        }
                                    })
                            .toArray(ClientName[]::new);
        }

        if (line.hasOption("host")) {
            runConfiguration.host = line.getOptionValue("host");
        }

        if (line.hasOption("clientCount")) {
            runConfiguration.clientCount = parseIntListOption(line.getOptionValue("clientCount"));
        }

        if (line.hasOption("dataSize")) {
            runConfiguration.dataSize = parseIntListOption(line.getOptionValue("dataSize"));
        }

        runConfiguration.tls = line.hasOption("tls");
        runConfiguration.clusterModeEnabled = line.hasOption("clusterModeEnabled");
        runConfiguration.minimal = line.hasOption("minimal");
        runConfiguration.debugLogging = line.hasOption("debugLogging");

        return runConfiguration;
    }

    private static int[] parseIntListOption(String line) throws ParseException {
        String lineValue = line;

        // remove optional square brackets
        if (lineValue.startsWith("[") && lineValue.endsWith("]")) {
            lineValue = lineValue.substring(1, lineValue.length() - 1);
        }

        // trim whitespace
        lineValue = lineValue.trim();

        // check if it's the correct format
        if (!lineValue.matches("\\d+(\\s+\\d+)*")) {
            throw new ParseException("Invalid option: " + line);
        }
        // split the string into a list of integers
        return Arrays.stream(lineValue.split("\\s+")).mapToInt(Integer::parseInt).toArray();
    }

    public enum ClientName {
        JEDIS("Jedis"), // sync
        LETTUCE("Lettuce"), // async
        GLIDE("Glide"), // async
        ALL("All");

        private String name;

        private ClientName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }

        public boolean isEqual(String other) {
            return this.toString().equalsIgnoreCase(other);
        }
    }

    public static class RunConfiguration {
        public String configuration;
        public Optional<String> resultsFile;
        public int[] dataSize;
        public int[] concurrentTasks;
        public ClientName[] clients;
        public String host;
        public int port;
        public int[] clientCount;
        public boolean tls;
        public boolean clusterModeEnabled;
        public boolean debugLogging = false;
        public boolean minimal = false;

        public RunConfiguration() {
            configuration = "Release";
            resultsFile = Optional.empty();
            dataSize = new int[] {100, 4000};
            concurrentTasks = new int[] {1, 10, 100, 1000};
            clients =
                    new ClientName[] {
                        ClientName.ALL,
                    };
            host = "localhost";
            port = 6379;
            clientCount = new int[] {1};
            tls = false;
            clusterModeEnabled = false;
            minimal = false;
        }
    }
}
