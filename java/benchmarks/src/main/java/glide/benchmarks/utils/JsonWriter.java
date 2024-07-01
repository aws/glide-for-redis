/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class JsonWriter {

    public static void Write(
            Map<ChosenAction, LatencyResults> calculatedResults,
            String resultsFile,
            boolean isCluster,
            int dataSize,
            String client,
            int clientCount,
            int numOfTasks,
            double tps) {

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
            Collection<Measurements> recordings = new ArrayList<>();

            Path path = Path.of(resultsFile);
            if (Files.exists(path)) {
                TypeToken<Collection<Measurements>> collectionType = new TypeToken<>() {};
                var json = new String(Files.readAllBytes(path));
                recordings = gson.fromJson(json, collectionType);
            }
            var data =
                    new Measurements(
                            client,
                            clientCount,
                            dataSize,
                            isCluster,
                            numOfTasks,
                            calculatedResults.get(ChosenAction.GET_EXISTING).avgLatency,
                            calculatedResults.get(ChosenAction.GET_EXISTING).p50Latency,
                            calculatedResults.get(ChosenAction.GET_EXISTING).p90Latency,
                            calculatedResults.get(ChosenAction.GET_EXISTING).p99Latency,
                            calculatedResults.get(ChosenAction.GET_EXISTING).stdDeviation,
                            calculatedResults.get(ChosenAction.GET_NON_EXISTING).avgLatency,
                            calculatedResults.get(ChosenAction.GET_NON_EXISTING).p50Latency,
                            calculatedResults.get(ChosenAction.GET_NON_EXISTING).p90Latency,
                            calculatedResults.get(ChosenAction.GET_NON_EXISTING).p99Latency,
                            calculatedResults.get(ChosenAction.GET_NON_EXISTING).stdDeviation,
                            calculatedResults.get(ChosenAction.SET).avgLatency,
                            calculatedResults.get(ChosenAction.SET).p50Latency,
                            calculatedResults.get(ChosenAction.SET).p90Latency,
                            calculatedResults.get(ChosenAction.SET).p99Latency,
                            calculatedResults.get(ChosenAction.SET).stdDeviation,
                            tps);

            recordings.add(data);

            Files.write(path, gson.toJson(recordings).getBytes());
        } catch (IOException e) {
            System.out.printf(
                    "Failed to write measurement results into a file '%s': %s%n",
                    resultsFile, e.getMessage());
            e.printStackTrace();
        }
    }

    public static class Measurements {
        public Measurements(
                String client,
                int client_count,
                int data_size,
                boolean is_cluster,
                int num_of_tasks,
                double get_existing_average_latency,
                double get_existing_p50_latency,
                double get_existing_p90_latency,
                double get_existing_p99_latency,
                double get_existing_std_dev,
                double get_non_existing_average_latency,
                double get_non_existing_p50_latency,
                double get_non_existing_p90_latency,
                double get_non_existing_p99_latency,
                double get_non_existing_std_dev,
                double set_average_latency,
                double set_p50_latency,
                double set_p90_latency,
                double set_p99_latency,
                double set_std_dev,
                double tps) {
            this.client = client;
            this.client_count = client_count;
            this.data_size = data_size;
            this.is_cluster = is_cluster;
            this.num_of_tasks = num_of_tasks;
            this.get_existing_average_latency = get_existing_average_latency;
            this.get_existing_p50_latency = get_existing_p50_latency;
            this.get_existing_p90_latency = get_existing_p90_latency;
            this.get_existing_p99_latency = get_existing_p99_latency;
            this.get_existing_std_dev = get_existing_std_dev;
            this.get_non_existing_average_latency = get_non_existing_average_latency;
            this.get_non_existing_p50_latency = get_non_existing_p50_latency;
            this.get_non_existing_p90_latency = get_non_existing_p90_latency;
            this.get_non_existing_p99_latency = get_non_existing_p99_latency;
            this.get_non_existing_std_dev = get_non_existing_std_dev;
            this.set_average_latency = set_average_latency;
            this.set_p50_latency = set_p50_latency;
            this.set_p90_latency = set_p90_latency;
            this.set_p99_latency = set_p99_latency;
            this.set_std_dev = set_std_dev;
            this.tps = tps;
        }

        public String client;
        public int client_count;
        public int data_size;
        public boolean is_cluster;
        public int num_of_tasks;
        public double get_existing_average_latency;
        public double get_existing_p50_latency;
        public double get_existing_p90_latency;
        public double get_existing_p99_latency;
        public double get_existing_std_dev;
        public double get_non_existing_average_latency;
        public double get_non_existing_p50_latency;
        public double get_non_existing_p90_latency;
        public double get_non_existing_p99_latency;
        public double get_non_existing_std_dev;
        public double set_average_latency;
        public double set_p50_latency;
        public double set_p90_latency;
        public double set_p99_latency;
        public double set_std_dev;
        public double tps;
    }
}
