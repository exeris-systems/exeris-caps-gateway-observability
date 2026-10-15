package eu.exeris.caps.observability.internal;

import eu.exeris.caps.observability.api.AccessLogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RingBufferAccessLog")
class RingBufferAccessLogTest {

    @Test
    @DisplayName("Entries are read back newest first and the oldest are overwritten in place")
    void wrapsAndKeepsNewestFirst() {
        RingBufferAccessLog ring = new RingBufferAccessLog(3);
        for (int i = 0; i < 7; i++) {
            ring.add(i, "GET", "/p" + i, 200 + i, i, "t" + i);
        }
        List<AccessLogEntry> recent = ring.recent(10);
        assertThat(recent).extracting(AccessLogEntry::path).containsExactly("/p6", "/p5", "/p4");
        assertThat(recent.get(0)).isEqualTo(new AccessLogEntry(6, "GET", "/p6", 206, 6, "t6"));
    }

    @Test
    @DisplayName("Null texts stay null, a longer text is cut, and a shorter one never shows the previous lap's tail")
    void nullsCutsAndNoStaleTail() {
        RingBufferAccessLog ring = new RingBufferAccessLog(1);
        ring.add(1, "M".repeat(100), "/" + "a".repeat(5000) + "?q=1", 200, 1, "t".repeat(500));
        AccessLogEntry cut = ring.recent(1).get(0);
        assertThat(cut.method()).hasSize(RingBufferAccessLog.MAX_METHOD_LENGTH);
        assertThat(cut.path()).hasSize(RingBufferAccessLog.MAX_PATH_LENGTH);
        assertThat(cut.traceId()).hasSize(RingBufferAccessLog.MAX_TRACE_ID_LENGTH);

        ring.add(2, "GET", "/x#frag", 200, 1, null);
        assertThat(ring.recent(1).get(0)).isEqualTo(new AccessLogEntry(2, "GET", "/x", 200, 1, null));

        ring.add(3, null, null, 200, 1, "");
        assertThat(ring.recent(1).get(0)).isEqualTo(new AccessLogEntry(3, null, null, 200, 1, ""));
    }

    @Test
    @DisplayName("A reader racing writers only ever sees complete entries")
    void readersSeeConsistentEntries() throws Exception {
        RingBufferAccessLog ring = new RingBufferAccessLog(8);
        AtomicBoolean stop = new AtomicBoolean();
        List<Thread> writers = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            int id = w;
            Thread t = new Thread(() -> {
                long i = 0;
                while (!stop.get()) {
                    String n = id + "-" + (i++ % 1000);
                    ring.add(i, "M" + n, "/path/" + n, 200, i, "trace-" + n);
                }
            });
            writers.add(t);
            t.start();
        }
        try {
            for (int r = 0; r < 20_000; r++) {
                for (AccessLogEntry e : ring.recent(8)) {
                    String n = e.method().substring(1);
                    assertThat(e.path()).isEqualTo("/path/" + n);
                    assertThat(e.traceId()).isEqualTo("trace-" + n);
                }
            }
        } finally {
            stop.set(true);
            for (Thread t : writers) {
                t.join();
            }
        }
    }
}
