package eu.exeris.caps.observability.internal;

import eu.exeris.caps.observability.api.GatewayObservabilities;
import eu.exeris.caps.observability.api.GatewayObservabilityService;
import eu.exeris.caps.observability.api.MetricsSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayObservabilityService Unit Tests")
class GatewayObservabilityServiceTest {

    @Test
    @DisplayName("Records requests, maintains recent buffer, and renders Prometheus format")
    void recordsAndExportsPrometheus() {
        try (GatewayObservabilityService service = GatewayObservabilities.newService(10)) {
            service.recordRequest("GET", "/api/bench/small", 200, 1_500_000L, "trace-abc-123");
            service.recordRequest("POST", "/api/transactions", 201, 3_000_000L, "trace-xyz-789");
            service.recordRequest("GET", "/api/missing", 404, 500_000L, "trace-404-000");

            MetricsSnapshot snap = service.snapshot();
            assertThat(snap.totalRequests()).isEqualTo(3);
            assertThat(snap.count2xx()).isEqualTo(2);
            assertThat(snap.count4xx()).isEqualTo(1);
            assertThat(snap.recentRequests()).hasSize(3);

            // Verify Prometheus output format
            String prom = service.exportPrometheus();
            assertThat(prom).contains("gateway_requests_total 3");
            assertThat(prom).contains("gateway_requests_status_total{class=\"2xx\"} 2");
            assertThat(prom).contains("gateway_requests_status_total{class=\"4xx\"} 1");
            assertThat(prom).contains("gateway_request_duration_seconds_bucket");
            assertThat(prom).contains("gateway_request_duration_seconds_count 3");

            // Verify JSON format
            String json = service.exportJson();
            assertThat(json).contains("\"totalRequests\":3");
            assertThat(json).contains("\"count2xx\":2");

            // Verify HTML dashboard format
            String html = service.exportDashboardHtml();
            assertThat(html).contains("Exeris Gateway :: Live Observability Engine");
            assertThat(html).contains("/api/bench/small");
            assertThat(html).contains("trace-abc-123");
        }
    }

    @Test
    @DisplayName("Request-derived text is escaped in the HTML dashboard")
    void dashboardEscapesRequestText() {
        try (GatewayObservabilityService service = GatewayObservabilities.newService()) {
            service.recordRequest("GE<T>", "/a<script>alert(1)</script>\"'&", 200, 1_000_000L, "t\"><img src=x>");
            String html = service.exportDashboardHtml();
            assertThat(html).doesNotContain("<script>alert").doesNotContain("<img src=x>").doesNotContain("GE<T>");
            assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;&quot;&#39;&amp;");
            assertThat(html).contains("GE&lt;T&gt;").contains("t&quot;&gt;&lt;img src=x&gt;");
        }
    }

    @Test
    @DisplayName("JSON strings are escaped and the output does not depend on the default locale")
    void jsonIsEscapedAndLocaleIndependent() {
        java.util.Locale previous = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pl-PL"));
        try (GatewayObservabilityService service = GatewayObservabilities.newService()) {
            service.recordRequest("GET", "/q\"\\\n\u0001\ud800x", 200, 1_500_000L, "id\t1");
            String json = service.exportJson();
            assertThat(json).contains("\"path\":\"/q\\\"\\\\\\n\\u0001\\ud800x\"");
            assertThat(json).contains("\"traceId\":\"id\\t1\"");
            assertThat(json).contains("\"avg_ms\":1.50");
            assertThat(json).doesNotContain("\n").doesNotContain("\t");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("The access log keeps the path without its query string and bounds its length")
    void accessLogKeepsPathOnly() {
        try (GatewayObservabilityService service = GatewayObservabilities.newService()) {
            service.recordRequest("GET", "/cb?token=secret#frag", 200, 1000L, null);
            service.recordRequest("GET", "/" + "a".repeat(10_000), 200, 1000L, null);
            var recent = service.snapshot().recentRequests();
            assertThat(recent).extracting(e -> e.path()).contains("/cb");
            assertThat(recent).allSatisfy(e -> assertThat(e.path().length()).isLessThanOrEqualTo(2048));
            assertThat(service.exportJson()).doesNotContain("secret");
            assertThat(service.exportDashboardHtml()).doesNotContain("secret");
        }
    }

    @Test
    @DisplayName("A negative or zero snapshot limit yields an empty list instead of throwing")
    void ringBufferToleratesNonPositiveLimit() {
        RingBufferAccessLog ring = new RingBufferAccessLog(4);
        assertThat(ring.recent(-1)).isEmpty();
        assertThat(ring.recent(0)).isEmpty();
    }
}
