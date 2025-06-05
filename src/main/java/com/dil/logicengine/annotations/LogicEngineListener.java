package com.dil.logicengine.annotations;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogicEngineListener {
    String topic();
    String groupId();
    Class<?> payloadType();
    DeserialisationType deserialization() default DeserialisationType.JSON;
}