package com.dil.action;

import com.dil.logicengine.api.DBAction;
import com.dil.entity.Greeting;
import com.dil.repository.GreetingRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SaveGreetingAction implements DBAction<String, Greeting> {

    private final GreetingRepository repository;

    @Override
    public Greeting execute(String name) {
        Greeting g = new Greeting();
        g.setName(name);
        g.setMessage("Hello " + name);
        return repository.save(g);
    }
}
