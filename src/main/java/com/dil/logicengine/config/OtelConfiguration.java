package com.dil.logicengine.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

public class OtelConfiguration {
    public static Tracer getTracer() {
        return GlobalOpenTelemetry.getTracer("com.logicengine");
    }
}
