package com.dil.logicengine.config;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;


public class OtelConfiguration {

    private static final Tracer tracer = init();

    private static Tracer init() {
        SpanExporter exporter = LoggingSpanExporter.create(); // Logs spans to console

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .build();

        return openTelemetry.getTracer("logicengine");
    }

    public static Tracer getTracer() {
        return tracer;
    }
}
