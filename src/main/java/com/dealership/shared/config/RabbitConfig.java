package com.dealership.shared.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

  public static final String NOTIFICATION_QUEUE = "dealership.notifications.send";

  @Bean
  Queue notificationQueue() {
    return new Queue(NOTIFICATION_QUEUE, true);
  }

  @Bean
  MessageConverter jacksonMessageConverter(com.fasterxml.jackson.databind.ObjectMapper mapper) {
    return new Jackson2JsonMessageConverter(mapper);
  }
}
