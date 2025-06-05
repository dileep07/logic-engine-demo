package com.dil.controller;

import com.dil.action.GreeterAction;
import com.dil.logicengine.LogicEngine;
import com.dil.logicengine.api.LogicAction;
import com.dil.logicengine.api.SimpleResponseBuilder;
import com.dil.service.GreeterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;


@RestController
@RequiredArgsConstructor
public class HelloController {

    private final GreeterService greeterService;

    @GetMapping("/hello")
    public Object runHelloAction(@RequestParam(defaultValue = "friend") String name) {
       return greeterService.greet(name);
    }
}
