package com.dil.logicengine.enhancer;

import com.dil.logicengine.annotations.DeserialisationType;
import com.dil.logicengine.annotations.LogicEngineListener;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class LogicEngineKafkaListenerRegistrar {

    private final ApplicationContext context;
    private final ConcurrentKafkaListenerContainerFactory<String, String> factory;
    private final ObjectMapper objectMapper;
   // private final AvroDeserializer avroDeserializer;
   // private final CustomDeserializer customDeserializer;

    public LogicEngineKafkaListenerRegistrar(
            ApplicationContext context,
            ConcurrentKafkaListenerContainerFactory<String, String> factory,
            ObjectMapper objectMapper
           // AvroDeserializer avroDeserializer,
           // CustomDeserializer customDeserializer
    ) {
        this.context = context;
        this.factory = factory;
        this.objectMapper = objectMapper;
        //this.avroDeserializer = avroDeserializer;
        //this.customDeserializer = customDeserializer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerListeners() {
        System.out.println("Inside register Listeners.....");
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
                    try {
                        Object payload = switch (deserialization) {
                            case JSON -> objectMapper.readValue(record.value(), payloadType);
                           // case AVRO -> avroDeserializer.deserialize(record.value(), payloadType);
                            case STRING -> record.value();
                            //case CUSTOM -> customDeserializer.deserialize(record, payloadType);
                        };

                        method.setAccessible(true);
                        method.invoke(bean, payload);

                    } catch (Exception e) {
                        // Log or handle error
                        e.printStackTrace();
                    }
                });

                ConsumerFactory<String, String> consumerFactory = (ConsumerFactory<String, String>) factory.getConsumerFactory();
                ConcurrentMessageListenerContainer<String, String> container =
                        new ConcurrentMessageListenerContainer<>(consumerFactory, props);

                container.start();
                System.out.println("Kafka listener registered: topic=" + ann.topic() );
            }
        }
    }
}
