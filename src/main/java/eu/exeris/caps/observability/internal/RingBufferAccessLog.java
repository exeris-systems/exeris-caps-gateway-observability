package eu.exeris.caps.observability.internal;

import eu.exeris.caps.observability.api.AccessLogEntry;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Lock-free circular buffer of recent request access entries, with every slot preallocated.
 *
 * <p>Recording overwrites a slot in place: the method, the path and the trace ID are copied into fixed-capacity
 * character arrays owned by the slot, so {@link #add} allocates nothing. Each slot carries a stamp that works
 * as a sequence lock: a writer takes the slot by moving its stamp to an odd value, writes, then publishes an
 * even value that names the entry; {@link #recent} copies a slot out and keeps the copy only if the stamp is
 * the same before and after. {@code AccessLogEntry} objects exist only in the lists that {@code recent}
 * returns, which is the export path.
 *
 * <p>Recording never waits. A writer that finds its slot being written by another writer, or already holding
 * a newer entry, which happens only when writers are a full lap of the buffer apart, drops its entry.
 * A reader skips a slot that is being written or has been overwritten while it was copied.
 *
 * @since 0.1.0
 */
public final class RingBufferAccessLog {

    /** Characters of a path that an entry keeps; a longer path is cut. */
    static final int MAX_PATH_LENGTH = 2048;
    /** Characters of a method that an entry keeps; a longer method is cut. */
    static final int MAX_METHOD_LENGTH = 32;
    /** Characters of a trace ID that an entry keeps; a longer one is cut. */
    static final int MAX_TRACE_ID_LENGTH = 128;

    private static final int ABSENT = -1;

    private final int capacity;
    private final Slot[] slots;
    /** Per slot: {@code 0} empty, {@code 2 * seq + 1} being written, {@code 2 * seq + 2} holds entry {@code seq}. */
    private final AtomicLongArray stamps;
    private final AtomicLong writeSequence = new AtomicLong(0);

    public RingBufferAccessLog(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.slots = new Slot[capacity];
        for (int i = 0; i < capacity; i++) {
            slots[i] = new Slot();
        }
        this.stamps = new AtomicLongArray(capacity);
    }

    /**
     * Stores one entry without allocating. The path is kept without its query string and fragment and cut to
     * {@value #MAX_PATH_LENGTH} characters; the method is cut to {@value #MAX_METHOD_LENGTH} and the trace ID
     * to {@value #MAX_TRACE_ID_LENGTH}. A {@code null} text is kept as {@code null}.
     */
    public void add(long timestampMillis, String method, String path, int statusCode, long durationMicros,
                    String traceId) {
        long seq = writeSequence.getAndIncrement();
        int index = (int) Math.floorMod(seq, capacity);
        long current = stamps.get(index);
        if ((current & 1L) != 0L || current > 2 * seq || !stamps.compareAndSet(index, current, 2 * seq + 1)) {
            return;
        }
        VarHandle.storeStoreFence();
        Slot slot = slots[index];
        slot.timestampMillis = timestampMillis;
        slot.statusCode = statusCode;
        slot.durationMicros = durationMicros;
        slot.methodLength = copy(method, method == null ? 0 : method.length(), MAX_METHOD_LENGTH, slot.method);
        slot.pathLength = copy(path, pathEnd(path), MAX_PATH_LENGTH, slot.path);
        slot.traceIdLength = copy(traceId, traceId == null ? 0 : traceId.length(), MAX_TRACE_ID_LENGTH, slot.traceId);
        stamps.set(index, 2 * seq + 2);
    }

    /** Index at which the path ends: before the query string or the fragment, whichever comes first. */
    private static int pathEnd(String path) {
        if (path == null) {
            return 0;
        }
        int end = path.length();
        int q = path.indexOf('?');
        if (q >= 0) {
            end = q;
        }
        int f = path.indexOf('#');
        if (f >= 0 && f < end) {
            end = f;
        }
        return end;
    }

    private static int copy(String text, int end, int max, char[] target) {
        if (text == null) {
            return ABSENT;
        }
        int length = Math.min(end, max);
        text.getChars(0, length, target, 0);
        return length;
    }

    /**
     * Copies out the most recent entries, newest first. Allocates the list and the entries; this is the
     * export path.
     */
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
            AccessLogEntry entry = read(seq);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    private AccessLogEntry read(long seq) {
        int index = (int) Math.floorMod(seq, capacity);
        long expected = 2 * seq + 2;
        if (stamps.get(index) != expected) {
            return null;
        }
        Slot slot = slots[index];
        long timestamp = slot.timestampMillis;
        int status = slot.statusCode;
        long duration = slot.durationMicros;
        String method = text(slot.method, slot.methodLength);
        String path = text(slot.path, slot.pathLength);
        String traceId = text(slot.traceId, slot.traceIdLength);
        VarHandle.loadLoadFence();
        if (stamps.get(index) != expected) {
            return null;
        }
        return new AccessLogEntry(timestamp, method, path, status, duration, traceId);
    }

    private static String text(char[] source, int length) {
        if (length < 0) {
            return null;
        }
        // A concurrent writer can change the length between the check and the copy; the stamp check discards it.
        return new String(source, 0, Math.min(length, source.length));
    }

    public int capacity() {
        return capacity;
    }

    /** The storage of one entry, overwritten in place. */
    private static final class Slot {
        long timestampMillis;
        int statusCode;
        long durationMicros;
        int methodLength = ABSENT;
        int pathLength = ABSENT;
        int traceIdLength = ABSENT;
        final char[] method = new char[MAX_METHOD_LENGTH];
        final char[] path = new char[MAX_PATH_LENGTH];
        final char[] traceId = new char[MAX_TRACE_ID_LENGTH];
    }
}
