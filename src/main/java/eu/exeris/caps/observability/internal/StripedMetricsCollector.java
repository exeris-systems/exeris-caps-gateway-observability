package eu.exeris.caps.observability.internal;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * Zero-allocation, lock-free metrics collector.
 *
 * <p>Uses striped {@link LongAdder} counters and a flat {@link AtomicLongArray}
 * for latency bucket distribution without memory allocation on the hot path.
 *
 * @since 0.1.0
 */
public final class StripedMetricsCollector {

    public static final long[] BUCKET_UPPER_BOUNDS_NANOS = {
            100_000L,       // 0.1 ms
            250_000L,       // 0.25 ms
            500_000L,       // 0.5 ms
            1_000_000L,     // 1 ms
            2_500_000L,     // 2.5 ms
            5_000_000L,     // 5 ms
            10_000_000L,    // 10 ms
            25_000_000L,    // 25 ms
            50_000_000L,    // 50 ms
            100_000_000L,   // 100 ms
            250_000_000L,   // 250 ms
            500_000_000L,   // 500 ms
            1_000_000_000L  // 1000 ms
    };

    public static final double[] BUCKET_LE_SECONDS = {
            0.0001, 0.00025, 0.0005, 0.001, 0.0025, 0.005,
            0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0
    };

    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder count2xx = new LongAdder();
    private final LongAdder count3xx = new LongAdder();
    private final LongAdder count4xx = new LongAdder();
    private final LongAdder count5xx = new LongAdder();

    private final AtomicLong activeRequests = new AtomicLong(0);
    private final LongAdder totalLatencyNanos = new LongAdder();
    private final AtomicLong maxLatencyNanos = new AtomicLong(0);

    // One slot per bound + 1 for +Inf
    private final AtomicLongArray bucketCounts = new AtomicLongArray(BUCKET_UPPER_BOUNDS_NANOS.length + 1);

    public void record(int statusCode, long durationNanos) {
        durationNanos = Math.max(0L, durationNanos);
        totalRequests.increment();

        if (statusCode >= 200 && statusCode < 300) {
            count2xx.increment();
        } else if (statusCode >= 300 && statusCode < 400) {
            count3xx.increment();
        } else if (statusCode >= 400 && statusCode < 500) {
            count4xx.increment();
        } else if (statusCode >= 500) {
            count5xx.increment();
        }

        totalLatencyNanos.add(durationNanos);

        // Update max latency atomically
        long curMax = maxLatencyNanos.get();
        while (durationNanos > curMax) {
            if (maxLatencyNanos.compareAndSet(curMax, durationNanos)) {
                break;
            }
            curMax = maxLatencyNanos.get();
        }

        // Increment histogram bucket
        int bucketIndex = findBucket(durationNanos);
        bucketCounts.incrementAndGet(bucketIndex);
    }

    public void incrementActive() {
        activeRequests.incrementAndGet();
    }

    public void decrementActive() {
        activeRequests.updateAndGet(v -> v > 0 ? v - 1 : 0);
    }

    private int findBucket(long durationNanos) {
        for (int i = 0; i < BUCKET_UPPER_BOUNDS_NANOS.length; i++) {
            if (durationNanos <= BUCKET_UPPER_BOUNDS_NANOS[i]) {
                return i;
            }
        }
        return BUCKET_UPPER_BOUNDS_NANOS.length; // +Inf
    }

    public long total() {
        return totalRequests.sum();
    }

    public long get2xx() {
        return count2xx.sum();
    }

    public long get3xx() {
        return count3xx.sum();
    }

    public long get4xx() {
        return count4xx.sum();
    }

    public long get5xx() {
        return count5xx.sum();
    }

    public long active() {
        return Math.max(0, activeRequests.get());
    }

    public double maxMillis() {
        return maxLatencyNanos.get() / 1_000_000.0;
    }

    public double avgMillis() {
        long count = total();
        return count > 0 ? (totalLatencyNanos.sum() / (double) count) / 1_000_000.0 : 0.0;
    }

    public long totalLatencyNanos() {
        return totalLatencyNanos.sum();
    }

    public long getBucketCount(int index) {
        return bucketCounts.get(index);
    }

    public double estimateQuantile(double percentile) {
        long total = total();
        if (total == 0) {
            return 0.0;
        }

        long targetRank = (long) Math.ceil(total * percentile);
        long cumulative = 0;

        for (int i = 0; i < BUCKET_UPPER_BOUNDS_NANOS.length; i++) {
            cumulative += bucketCounts.get(i);
            if (cumulative >= targetRank) {
                return BUCKET_UPPER_BOUNDS_NANOS[i] / 1_000_000.0;
            }
        }

        return maxMillis();
    }
}
