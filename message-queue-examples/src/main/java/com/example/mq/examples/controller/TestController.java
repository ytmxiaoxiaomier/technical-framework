package com.example.mq.examples.controller;

import com.example.mq.core.message.MessageResult;
import com.example.mq.core.message.SendResult;
import com.example.mq.examples.producer.ExampleProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final ExampleProducer producer;

    @PostMapping("/kafka/send")
    public MessageResult sendToKafka(@RequestBody String message) {
        MessageResult result = null;
        for (int i = 0; i < 100; i++) {
            result = producer.sendToKafka(message + " : " + i);
        }
        return result;
    }

    @PostMapping("/rocketmq/send")
    public MessageResult sendToRocketMQ(@RequestBody String message) {
        return producer.sendToRocketMQ(message);
    }

//    @PostMapping("/rabbitmq/send")
//    public SendResult sendToRabbitMQ(
//            @RequestBody String message,
//            @RequestParam String routingKey
//    ) {
//        return producer.sendToRabbitMQ(message, routingKey);
//    }
//
//    @PostMapping("/pulsar/send")
//    public SendResult sendToPulsar(
//            @RequestBody String message,
//            @RequestParam(defaultValue = "0") long delay,
//            @RequestParam(defaultValue = "SECONDS") TimeUnit unit
//    ) {
//        return producer.sendToPulsar(message, delay, unit);
//    }
} 