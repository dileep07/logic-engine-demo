package com.dil.service;

import com.dil.entity.Greeting;
import com.dil.logicengine.annotations.DeserialisationType;
import com.dil.logicengine.annotations.LogicEngineListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@RequiredArgsConstructor
@Component
public class GreeterEventListener {

    private final GreeterService greeterService;

    @LogicEngineListener(
            topic = "greeting.events",
            groupId = "greeting-service",
            payloadType = Greeting.class,
            deserialization = DeserialisationType.JSON
    )
    public void handleGreeting(Greeting event) {
        greeterService.greet(event.getName());
    }

    @LogicEngineListener(
            topic = "greeting.strings",
            groupId = "greeting-service",
            payloadType = Greeting.class,
            deserialization = DeserialisationType.STRING
    )
    public void handleGreetingString(String event) {
        greeterService.greet(event);
    }


}
