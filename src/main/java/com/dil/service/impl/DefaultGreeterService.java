package com.dil.service.impl;

import com.dil.action.GreeterAction;
import com.dil.logicengine.LogicEngine;
import com.dil.logicengine.api.LogicAction;
import com.dil.logicengine.api.SimpleResponseBuilder;
import com.dil.service.GreeterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultGreeterService implements GreeterService {

    private final LogicEngine logicEngine;

    @Override
    public String greet(String input) {
        List<LogicAction<String, ?>> actions = List.of(new GreeterAction());
        Map<String, Object> response = logicEngine.executeActions(input, actions, new SimpleResponseBuilder<>());
        return (String) response.values().iterator().next();
    }

}
