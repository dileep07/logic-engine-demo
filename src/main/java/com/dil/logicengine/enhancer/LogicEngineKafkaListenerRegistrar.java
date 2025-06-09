package com.dil.logicengine.enhancer;

import com.dil.logicengine.annotations.DeserialisationType;
import com.dil.logicengine.annotations.LogicEngineListener;
import com.dil.logicengine.config.OtelConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;


@Component
@RequiredArgsConstructor
public class LogicEngineKafkaListenerRegistrar {

    private final ApplicationContext context;
    private final ConcurrentKafkaListenerContainerFactory<String, String> factory;
    private final ObjectMapper objectMapper;
   // private final AvroDeserializer avroDeserializer;
   // private final CustomDeserializer customDeserializer;
   private final ActionLogger log = new ActionLogger(LogicEngineKafkaListenerRegistrar.class);
   private final Tracer tracer = OtelConfiguration.getTracer();

    @EventListener(ApplicationReadyEvent.class)
    public void registerListeners() {

        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Class<?> beanClass = AopUtils.getTargetClass(bean); // Handle proxies

            for (Method method : beanClass.getDeclaredMethods()) {
                LogicEngineListener ann = method.getAnnotation(LogicEngineListener.class);
                if (ann == null) continue;
                Class<?> payloadType = ann.payloadType();
                DeserialisationType deserialization = ann.deserialization();
                // Build Kafka container
                ContainerProperties props = new ContainerProperties(ann.topic());
                props.setGroupId(ann.groupId());

                props.setMessageListener((MessageListener<String, String>) record -> {
                    Span span = tracer.spanBuilder("KafkaListener:" + method.getName())
                            .setSpanKind(SpanKind.CONSUMER)
                            .setAttribute("messaging.system", "kafka")
                            .setAttribute("messaging.destination", record.topic())
                            .setAttribute("messaging.destination_kind", "topic")
                            .setAttribute("messaging.operation", "receive")
                            .setAttribute("messaging.kafka.partition", record.partition())
                            .setAttribute("messaging.kafka.offset", record.offset())
                            .startSpan();

                    try (Scope scope = span.makeCurrent()) {
                        // Optional: set MDC context for logging
                        MDC.put("traceid", span.getSpanContext().getTraceId());
                        MDC.put("spanid", span.getSpanContext().getSpanId());
                        MDC.put("action", method.getName());

                        Object payload = switch (deserialization) {
                            case JSON -> objectMapper.readValue(record.value(), payloadType);
                            case STRING -> record.value();
                        };

                        log.info("Kafka message received: topic={}, method={}, payload={}",
                                record.topic(), method.getName(), payload);

                        method.setAccessible(true);
                        method.invoke(bean, payload);

                    } catch (Exception e) {
                        span.recordException(e);
                        span.setStatus(StatusCode.ERROR);
                        log.error("Error while invoking method {}: {}", method.getName(), e.getMessage(), e);
                    } finally {
                        span.end();
                        MDC.clear(); // Always clear to prevent leaking context
                    }
                });

                ConsumerFactory<String, String> consumerFactory = (ConsumerFactory<String, String>) factory.getConsumerFactory();
                ConcurrentMessageListenerContainer<String, String> container =
                        new ConcurrentMessageListenerContainer<>(consumerFactory, props);

                try {
                    log.info("Starting Kafka container for topic={} groupId={} method={}",
                            ann.topic(), ann.groupId(), method.getName());
                    container.start();
                    log.info("Kafka container started for topic={} groupId={}",
                            ann.topic(), ann.groupId());
                } catch (Exception e) {
                    log.error("Failed to start Kafka container for topic={}: {}", ann.topic(), e.getMessage(), e);
                }
            }
        }
    }
}
