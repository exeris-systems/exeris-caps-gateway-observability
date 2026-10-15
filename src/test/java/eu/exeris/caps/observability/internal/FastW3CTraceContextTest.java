package eu.exeris.caps.observability.internal;

import eu.exeris.caps.observability.api.TraceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FastW3CTraceContext Unit Tests")
class FastW3CTraceContextTest {

    private final FastW3CTraceContext manager = new FastW3CTraceContext();

    @Test
    @DisplayName("Generates fresh valid root trace context")
    void generatesValidRoot() {
        TraceContext ctx = manager.createRoot();

        assertThat(ctx).isNotNull();
        assertThat(ctx.traceId()).hasSize(32);
        assertThat(ctx.spanId()).hasSize(16);
        assertThat(ctx.sampled()).isTrue();

        String traceparent = ctx.toTraceparent();
        assertThat(traceparent).startsWith("00-" + ctx.traceId() + "-" + ctx.spanId() + "-01");
    }

    @Test
    @DisplayName("Parses valid incoming traceparent header")
    void parsesValidIncomingTraceparent() {
        String input = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        TraceContext ctx = manager.extractOrCreate(input);

        assertThat(ctx.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(ctx.spanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(ctx.sampled()).isTrue();
    }

    @Test
    @DisplayName("Generates new root when incoming traceparent is invalid or malformed")
    void generatesRootOnMalformedInput() {
        TraceContext ctx1 = manager.extractOrCreate(null);
        assertThat(ctx1.traceId()).hasSize(32);

        TraceContext ctx2 = manager.extractOrCreate("garbage-header");
        assertThat(ctx2.traceId()).hasSize(32);
    }

    @Test
    @DisplayName("Derives child span preserving trace ID")
    void derivesChildSpan() {
        TraceContext parent = manager.createRoot();
        TraceContext child = manager.createChild(parent);

        assertThat(child.traceId()).isEqualTo(parent.traceId());
        assertThat(child.spanId()).isNotEqualTo(parent.spanId());
        assertThat(child.sampled()).isEqualTo(parent.sampled());
    }

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN = "00f067aa0ba902b7";

    @Test
    @DisplayName("Only a fully well-formed version 00 traceparent is propagated")
    void propagatesOnlyWellFormedHeaders() {
        FastW3CTraceContext manager = new FastW3CTraceContext();

        TraceContext ok = manager.extractOrCreate("00-" + TRACE + "-" + SPAN + "-01");
        assertThat(ok.traceId()).isEqualTo(TRACE);
        assertThat(ok.spanId()).isEqualTo(SPAN);
        assertThat(ok.sampled()).isTrue();
        assertThat(manager.extractOrCreate("00-" + TRACE + "-" + SPAN + "-00").sampled()).isFalse();
        assertThat(manager.extractOrCreate("00-" + TRACE + "-" + SPAN + "-03").sampled()).isTrue();

        String[] rejected = {
                "00-" + TRACE.toUpperCase() + "-" + SPAN + "-01",
                "00-" + TRACE + "-" + SPAN.toUpperCase() + "-01",
                "00-" + "z".repeat(32) + "-" + SPAN + "-01",
                "00-" + "0".repeat(32) + "-" + SPAN + "-01",
                "00-" + TRACE + "-" + "0".repeat(16) + "-01",
                "ff-" + TRACE + "-" + SPAN + "-01",
                "01-" + TRACE + "-" + SPAN + "-01",
                "00-" + TRACE + "-" + SPAN + "-0g",
                "00-" + TRACE + "-" + SPAN + "-0A",
                "00-" + TRACE + "-" + SPAN + "-01-extra",
                "00-" + TRACE + "-" + SPAN + "-011",
                "00-" + TRACE + "-" + SPAN + "-0\n",
                "00-" + TRACE.substring(0, 31) + "<-" + SPAN + "-01",
        };
        for (String header : rejected) {
            TraceContext ctx = manager.extractOrCreate(header);
            assertThat(ctx.traceId()).as(header).isNotEqualTo(TRACE.toUpperCase()).doesNotContain("<", "z");
            assertThat(ctx.spanId()).as(header).isNotEqualTo("0".repeat(16));
            assertThat(ctx.traceId()).as(header).isNotEqualTo("0".repeat(32));
            assertThat(ctx.toTraceparent()).as(header).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
        }
        // A header whose ids are well-formed but whose flags are not must not be propagated
        assertThat(manager.extractOrCreate("00-" + TRACE + "-" + SPAN + "-0g").traceId()).isNotEqualTo(TRACE);
        assertThat(manager.extractOrCreate("00-" + TRACE + "-" + SPAN + "-01-extra").traceId()).isNotEqualTo(TRACE);
        assertThat(manager.extractOrCreate("ff-" + TRACE + "-" + SPAN + "-01").traceId()).isNotEqualTo(TRACE);
        assertThat(manager.extractOrCreate("00-" + TRACE + "-" + SPAN.toUpperCase() + "-01").traceId()).isNotEqualTo(TRACE);
    }

    @Test
    @DisplayName("A TraceContext rejects non-hex, upper-case and all-zero ids")
    void traceContextValidatesItsIds() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TraceContext("0".repeat(32), SPAN, true))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TraceContext(TRACE, "0".repeat(16), true))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TraceContext(TRACE.toUpperCase(), SPAN, true))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TraceContext("<".repeat(32), SPAN, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
