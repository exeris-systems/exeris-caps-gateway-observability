package eu.exeris.caps.observability.internal;

import eu.exeris.caps.observability.api.AccessLogEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Lock-free circular ring buffer storing recent request access entries.
 *
 * @since 0.1.0
 */
public final class RingBufferAccessLog {

    private final int capacity;
    private final AtomicReferenceArray<AccessLogEntry> buffer;
    private final AtomicLong writeSequence = new AtomicLong(0);

    public RingBufferAccessLog(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.buffer = new AtomicReferenceArray<>(capacity);
    }

    public void add(AccessLogEntry entry) {
        long seq = writeSequence.getAndIncrement();
        int slot = (int) Math.floorMod(seq, capacity);
        buffer.set(slot, entry);
    }

    public List<AccessLogEntry> recent(int limit) {
        if (limit <= 0) {
            return new ArrayList<>(0);
        }
        long currentSeq = writeSequence.get();
        int count = (int) Math.min(limit, Math.min(currentSeq, capacity));
        List<AccessLogEntry> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            long seq = currentSeq - 1 - i;
            if (seq < 0) {
                break;
            }
            int slot = (int) Math.floorMod(seq, capacity);
            AccessLogEntry entry = buffer.get(slot);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    public int capacity() {
        return capacity;
    }
}
