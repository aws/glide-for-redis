/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.utils;

import java.util.Arrays;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

/** Raw timing results in nanoseconds */
public class LatencyResults {
    // measurements are done in nano-seconds, but latencies should be converted to milliseconds
    static final double NANO_TO_MILLI = 1e-6;

    public final double avgLatency;
    public final double p50Latency;
    public final double p90Latency;
    public final double p99Latency;
    public final double stdDeviation;
    public final int totalRequests;

    private double TruncateDecimal(double number, int digits) {
        int stepper = (int) Math.pow((double) 10, (double) digits);
        return Math.floor(number * stepper) / stepper;
    }

    public LatencyResults(double[] latencies) {
        avgLatency =
                TruncateDecimal((NANO_TO_MILLI * Arrays.stream(latencies).sum() / latencies.length), 3);
        p50Latency = TruncateDecimal((NANO_TO_MILLI * new Percentile().evaluate(latencies, 50)), 3);
        p90Latency = TruncateDecimal((NANO_TO_MILLI * new Percentile().evaluate(latencies, 90)), 3);
        p99Latency = TruncateDecimal((NANO_TO_MILLI * new Percentile().evaluate(latencies, 99)), 3);
        stdDeviation =
                TruncateDecimal(
                        (NANO_TO_MILLI * new StandardDeviation().evaluate(latencies, avgLatency)), 3);
        totalRequests = latencies.length;
    }
}
