package com.dil.action;

import com.dil.logicengine.api.BaseAction;
import com.dil.service.GreeterService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;

@Scope("prototype")
public class GreeterAction extends BaseAction<String, String> {

    @Override
    public String execute(String input) {
        log.info("Executing Greeting Action"+ input);
        return "Hello from the Greeter Action : " + input ;
    }
}
