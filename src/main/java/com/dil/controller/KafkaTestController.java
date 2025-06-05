package com.dil.controller;

import com.dil.entity.Greeting;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KafkaTestController {

    private final KafkaTemplate<Object, Greeting> kafkaTemplate;
    private final KafkaTemplate<Object, String> rawkafkaTemplate;
    @PostMapping("/send-json")
    public ResponseEntity<String> sendTestJson(@RequestBody Greeting json) {
        kafkaTemplate.send("greeting.events", json);
        return ResponseEntity.ok("Message sent Json");
    }

    @PostMapping("/send-string")
    public ResponseEntity<String> sendTestString(@RequestBody String json) {
        rawkafkaTemplate.send("greeting.strings", json);
        return ResponseEntity.ok("Message sent String");
    }

}