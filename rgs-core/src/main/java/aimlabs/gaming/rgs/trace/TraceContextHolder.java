package aimlabs.gaming.rgs.trace;

import java.lang.ScopedValue;

public class TraceContextHolder {

    private static final ScopedValue<String> TRACE = ScopedValue.newInstance();

    public static String getTraceId() {
        if (TRACE.isBound())
            return TRACE.get();
        return null;
    }

    public static ScopedValue<String> getScopedValue() {
        return TRACE;
    }
}

