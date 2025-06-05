package com.dil.logicengine.api;

public interface AvroDeserializer {
    <T> T deserialize(byte[] data, Class<T> targetType);
}
