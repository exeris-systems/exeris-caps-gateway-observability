package eu.exeris.caps.observability.internal;

import eu.exeris.caps.observability.api.TraceContext;
import eu.exeris.caps.observability.api.TraceContextManager;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fast, low-overhead implementation of {@link TraceContextManager} for W3C TraceContext.
 *
 * @since 0.1.0
 */
public final class FastW3CTraceContext implements TraceContextManager {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    @Override
    public TraceContext extractOrCreate(CharSequence incomingHeader) {
        // version "00" has exactly this layout: 00-{32}-{16}-{2}
        if (incomingHeader == null || incomingHeader.length() != 55) {
            return createRoot();
        }
        if (incomingHeader.charAt(0) != '0' || incomingHeader.charAt(1) != '0'
                || incomingHeader.charAt(2) != '-' || incomingHeader.charAt(35) != '-'
                || incomingHeader.charAt(52) != '-') {
            return createRoot();
        }
        if (!lowerHexNonZero(incomingHeader, 3, 35) || !lowerHexNonZero(incomingHeader, 36, 52)) {
            return createRoot();
        }
        int hi = hexValue(incomingHeader.charAt(53));
        int lo = hexValue(incomingHeader.charAt(54));
        if (hi < 0 || lo < 0) {
            return createRoot();
        }

        String traceId = incomingHeader.subSequence(3, 35).toString();
        String spanId = incomingHeader.subSequence(36, 52).toString();
        boolean sampled = (lo & 1) == 1;

        return new TraceContext(traceId, spanId, sampled);
    }

    private static boolean lowerHexNonZero(CharSequence s, int from, int to) {
        boolean nonZero = false;
        for (int i = from; i < to; i++) {
            int v = hexValue(s.charAt(i));
            if (v < 0) {
                return false;
            }
            if (v != 0) {
                nonZero = true;
            }
        }
        return nonZero;
    }

    /** Lower-case hex digit value, or -1. Upper case is not valid in a traceparent. */
    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }

    @Override
    public TraceContext createRoot() {
        return new TraceContext(generateNonZeroHex(32), generateNonZeroHex(16), true);
    }

    @Override
    public TraceContext createChild(TraceContext parent) {
        if (parent == null) {
            return createRoot();
        }
        return new TraceContext(parent.traceId(), generateNonZeroHex(16), parent.sampled());
    }

    private static String generateNonZeroHex(int length) {
        String hex;
        do {
            hex = generateHex(length);
        } while (hex.chars().allMatch(c -> c == '0'));
        return hex;
    }

    public static String generateHex(int length) {
        char[] chars = new char[length];
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < length; i += 8) {
            long val = rnd.nextLong();
            for (int j = 0; j < 8 && (i + j) < length; j++) {
                chars[i + j] = HEX_CHARS[(int) (val & 0x0F)];
                val >>>= 4;
            }
        }
        return new String(chars);
    }
}
