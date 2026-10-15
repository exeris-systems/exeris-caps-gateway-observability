package eu.exeris.caps.observability.internal;

import eu.exeris.caps.observability.api.AccessLogEntry;
import eu.exeris.caps.observability.api.MetricsSnapshot;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * High-performance, zero-dependency renderer producing Prometheus metrics,
 * JSON diagnostics, and self-contained HTML dashboards.
 *
 * @since 0.1.0
 */
public final class DashboardRenderer {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private DashboardRenderer() {
    }

    public static String renderPrometheus(StripedMetricsCollector metrics) {
        StringBuilder sb = new StringBuilder(2048);

        sb.append("# HELP gateway_requests_total Total number of HTTP requests processed\n");
        sb.append("# TYPE gateway_requests_total counter\n");
        sb.append("gateway_requests_total ").append(metrics.total()).append("\n");

        sb.append("# HELP gateway_requests_status_total Requests partitioned by status class\n");
        sb.append("# TYPE gateway_requests_status_total counter\n");
        sb.append("gateway_requests_status_total{class=\"2xx\"} ").append(metrics.get2xx()).append("\n");
        sb.append("gateway_requests_status_total{class=\"3xx\"} ").append(metrics.get3xx()).append("\n");
        sb.append("gateway_requests_status_total{class=\"4xx\"} ").append(metrics.get4xx()).append("\n");
        sb.append("gateway_requests_status_total{class=\"5xx\"} ").append(metrics.get5xx()).append("\n");

        sb.append("# HELP gateway_active_requests Number of requests currently in-flight\n");
        sb.append("# TYPE gateway_active_requests gauge\n");
        sb.append("gateway_active_requests ").append(metrics.active()).append("\n");

        sb.append("# HELP gateway_request_duration_seconds HTTP request latency distribution\n");
        sb.append("# TYPE gateway_request_duration_seconds histogram\n");

        long cumulative = 0;
        for (int i = 0; i < StripedMetricsCollector.BUCKET_LE_SECONDS.length; i++) {
            cumulative += metrics.getBucketCount(i);
            sb.append("gateway_request_duration_seconds_bucket{le=\"")
                    .append(StripedMetricsCollector.BUCKET_LE_SECONDS[i])
                    .append("\"} ")
                    .append(cumulative)
                    .append("\n");
        }
        cumulative += metrics.getBucketCount(StripedMetricsCollector.BUCKET_LE_SECONDS.length);
        sb.append("gateway_request_duration_seconds_bucket{le=\"+Inf\"} ").append(cumulative).append("\n");
        sb.append("gateway_request_duration_seconds_sum ").append(metrics.totalLatencyNanos() / 1_000_000_000.0).append("\n");
        sb.append("gateway_request_duration_seconds_count ").append(metrics.total()).append("\n");

        return sb.toString();
    }

    public static String renderJson(MetricsSnapshot s) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{");
        sb.append("\"totalRequests\":").append(s.totalRequests()).append(",");
        sb.append("\"activeRequests\":").append(s.activeRequests()).append(",");
        sb.append("\"count2xx\":").append(s.count2xx()).append(",");
        sb.append("\"count3xx\":").append(s.count3xx()).append(",");
        sb.append("\"count4xx\":").append(s.count4xx()).append(",");
        sb.append("\"count5xx\":").append(s.count5xx()).append(",");
        sb.append("\"latency\":{");
        sb.append("\"avg_ms\":").append(String.format(Locale.ROOT, "%.2f", s.avgMillis())).append(",");
        sb.append("\"p50_ms\":").append(String.format(Locale.ROOT, "%.2f", s.p50Millis())).append(",");
        sb.append("\"p90_ms\":").append(String.format(Locale.ROOT, "%.2f", s.p90Millis())).append(",");
        sb.append("\"p95_ms\":").append(String.format(Locale.ROOT, "%.2f", s.p95Millis())).append(",");
        sb.append("\"p99_ms\":").append(String.format(Locale.ROOT, "%.2f", s.p99Millis())).append(",");
        sb.append("\"max_ms\":").append(String.format(Locale.ROOT, "%.2f", s.maxMillis()));
        sb.append("},");
        sb.append("\"recentCount\":").append(s.recentRequests().size()).append(",");
        sb.append("\"recent\":[");
        boolean first = true;
        for (AccessLogEntry e : s.recentRequests()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{\"timestampMillis\":").append(e.timestampMillis());
            sb.append(",\"method\":");
            appendJsonString(sb, e.method());
            sb.append(",\"path\":");
            appendJsonString(sb, e.path());
            sb.append(",\"statusCode\":").append(e.statusCode());
            sb.append(",\"durationMicros\":").append(e.durationMicros());
            sb.append(",\"traceId\":");
            appendJsonString(sb, e.traceId());
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String renderHtmlDashboard(MetricsSnapshot s) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<meta http-equiv=\"refresh\" content=\"1\">\n");
        sb.append("<title>Exeris Gateway :: Live Observability</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }\n");
        sb.append(".header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155; padding-bottom: 16px; margin-bottom: 24px; }\n");
        sb.append(".title { font-size: 24px; font-weight: 700; color: #38bdf8; display: flex; align-items: center; gap: 8px; }\n");
        sb.append(".badge-pulse { display: inline-block; width: 10px; height: 10px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 8px #22c55e; }\n");
        sb.append(".grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 24px; }\n");
        sb.append(".card { background: #1e293b; border-radius: 8px; padding: 18px; border: 1px solid #334155; }\n");
        sb.append(".card-label { font-size: 13px; color: #94a3b8; text-transform: uppercase; font-weight: 600; margin-bottom: 6px; }\n");
        sb.append(".card-value { font-size: 28px; font-weight: 700; }\n");
        sb.append(".c-blue { color: #38bdf8; } .c-green { color: #4ade80; } .c-yellow { color: #facc15; } .c-red { color: #f87171; }\n");
        sb.append(".section { background: #1e293b; border-radius: 8px; padding: 20px; border: 1px solid #334155; margin-bottom: 24px; }\n");
        sb.append(".section-title { font-size: 16px; font-weight: 600; margin-bottom: 16px; color: #e2e8f0; }\n");
        sb.append("table { width: 100%; border-collapse: collapse; font-size: 13px; font-family: monospace; }\n");
        sb.append("th { text-align: left; padding: 8px 12px; background: #0f172a; color: #94a3b8; border-bottom: 1px solid #334155; }\n");
        sb.append("td { padding: 8px 12px; border-bottom: 1px solid #1e293b; }\n");
        sb.append(".tag-2xx { color: #4ade80; font-weight: bold; } .tag-4xx { color: #facc15; font-weight: bold; } .tag-5xx { color: #f87171; font-weight: bold; }\n");
        sb.append(".trace-id { color: #cbd5e1; font-size: 11px; }\n");
        sb.append("</style>\n</head>\n<body>\n");

        sb.append("<div class=\"header\">\n");
        sb.append("  <div class=\"title\"><span class=\"badge-pulse\"></span> Exeris Gateway :: Live Observability Engine</div>\n");
        sb.append("  <div style=\"color:#94a3b8; font-size:13px;\">Auto-refresh: 1s | Active In-Flight: <strong style=\"color:#38bdf8;\">")
                .append(s.activeRequests()).append("</strong></div>\n");
        sb.append("</div>\n");

        // KPI Grid
        sb.append("<div class=\"grid\">\n");
        sb.append("  <div class=\"card\"><div class=\"card-label\">Total Requests</div><div class=\"card-value c-blue\">")
                .append(String.format(Locale.ROOT, "%,d", s.totalRequests())).append("</div></div>\n");
        sb.append("  <div class=\"card\"><div class=\"card-label\">2xx Success</div><div class=\"card-value c-green\">")
                .append(String.format(Locale.ROOT, "%,d", s.count2xx())).append("</div></div>\n");
        sb.append("  <div class=\"card\"><div class=\"card-label\">4xx Client Error</div><div class=\"card-value c-yellow\">")
                .append(String.format(Locale.ROOT, "%,d", s.count4xx())).append("</div></div>\n");
        sb.append("  <div class=\"card\"><div class=\"card-label\">5xx Server Error</div><div class=\"card-value c-red\">")
                .append(String.format(Locale.ROOT, "%,d", s.count5xx())).append("</div></div>\n");
        sb.append("</div>\n");

        // Latency Percentiles Grid
        sb.append("<div class=\"section\">\n");
        sb.append("  <div class=\"section-title\">Latency Percentiles & Distribution</div>\n");
        sb.append("  <div class=\"grid\" style=\"margin-bottom:0;\">\n");
        sb.append("    <div class=\"card\"><div class=\"card-label\">p50 Latency</div><div class=\"card-value\">")
                .append(String.format(Locale.ROOT, "%.2f ms", s.p50Millis())).append("</div></div>\n");
        sb.append("    <div class=\"card\"><div class=\"card-label\">p90 Latency</div><div class=\"card-value\">")
                .append(String.format(Locale.ROOT, "%.2f ms", s.p90Millis())).append("</div></div>\n");
        sb.append("    <div class=\"card\"><div class=\"card-label\">p95 Latency</div><div class=\"card-value\">")
                .append(String.format(Locale.ROOT, "%.2f ms", s.p95Millis())).append("</div></div>\n");
        sb.append("    <div class=\"card\"><div class=\"card-label\">p99 Latency</div><div class=\"card-value c-yellow\">")
                .append(String.format(Locale.ROOT, "%.2f ms", s.p99Millis())).append("</div></div>\n");
        sb.append("    <div class=\"card\"><div class=\"card-label\">Max Latency</div><div class=\"card-value c-red\">")
                .append(String.format(Locale.ROOT, "%.2f ms", s.maxMillis())).append("</div></div>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // Recent Requests Log
        sb.append("<div class=\"section\">\n");
        sb.append("  <div class=\"section-title\">Recent Ingress Stream (Last ")
                .append(s.recentRequests().size()).append(" Requests)</div>\n");
        sb.append("  <table>\n");
        sb.append("    <thead><tr><th>Time</th><th>Method</th><th>Path</th><th>Status</th><th>Duration</th><th>W3C Trace ID</th></tr></thead>\n");
        sb.append("    <tbody>\n");

        List<AccessLogEntry> recent = s.recentRequests();
        if (recent.isEmpty()) {
            sb.append("      <tr><td colspan=\"6\" style=\"text-align:center; color:#64748b;\">Waiting for incoming traffic...</td></tr>\n");
        } else {
            for (AccessLogEntry entry : recent) {
                String statusClass = (entry.statusCode() >= 200 && entry.statusCode() < 300) ? "tag-2xx" :
                        (entry.statusCode() >= 400 && entry.statusCode() < 500) ? "tag-4xx" : "tag-5xx";
                sb.append("      <tr>");
                sb.append("<td>").append(TIME_FORMATTER.format(Instant.ofEpochMilli(entry.timestampMillis()))).append("</td>");
                sb.append("<td><strong>").append(escapeHtml(entry.method())).append("</strong></td>");
                sb.append("<td>").append(escapeHtml(entry.path())).append("</td>");
                sb.append("<td><span class=\"").append(statusClass).append("\">").append(entry.statusCode()).append("</span></td>");
                sb.append("<td>").append(entry.durationMicros() >= 1000 ? String.format(Locale.ROOT, "%.2f ms", entry.durationMicros() / 1000.0) : entry.durationMicros() + " µs").append("</td>");
                sb.append("<td class=\"trace-id\">").append(entry.traceId() != null ? escapeHtml(entry.traceId()) : "-").append("</td>");
                sb.append("</tr>\n");
            }
        }

        sb.append("    </tbody>\n");
        sb.append("  </table>\n");
        sb.append("</div>\n");

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    /** Escapes text for an HTML element body or a quoted attribute value. */
    static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String repl = switch (c) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                case '\'' -> "&#39;";
                default -> null;
            };
            if (repl != null && sb == null) {
                sb = new StringBuilder(text.length() + 16).append(text, 0, i);
            }
            if (sb != null) {
                if (repl != null) {
                    sb.append(repl);
                } else {
                    sb.append(c);
                }
            }
        }
        return sb == null ? text : sb.toString();
    }

    /** Appends {@code value} as a JSON string literal (RFC 8259), or {@code null}. */
    static void appendJsonString(StringBuilder sb, String value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    boolean lone = Character.isSurrogate(c) && !(Character.isHighSurrogate(c)
                            && i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1)))
                            && !(Character.isLowSurrogate(c) && i > 0 && Character.isHighSurrogate(value.charAt(i - 1)));
                    if (c < 0x20 || c == 0x7f || c == 0x2028 || c == 0x2029 || lone) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
