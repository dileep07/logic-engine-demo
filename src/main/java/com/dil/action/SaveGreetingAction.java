package com.dil.action;

import com.dil.logicengine.api.BaseAction;
import com.dil.logicengine.api.DBAction;
import com.dil.entity.Greeting;
import com.dil.repository.GreetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;

@RequiredArgsConstructor
@Scope("prototype")
public class SaveGreetingAction extends BaseAction<String, Greeting> implements DBAction<String, Greeting> {

    private final GreetingRepository repository;

    @Override
    public Greeting execute(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        Greeting g = new Greeting();
        g.setName(name);
        g.setMessage("Hello " + name);
        log.info("Executing Greeting Action"+ name);
        return repository.save(g);
    }
}
