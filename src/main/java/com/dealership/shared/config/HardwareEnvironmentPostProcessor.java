package com.dealership.shared.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class HardwareEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final String SOURCE = "hardware-sizing";

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    int cpus = HardwareSizing.cpus();
    Map<String, Object> values = new HashMap<>();
    if (unsetOrZero(environment, "app.workers.claim-batch", "APP_WORKERS_CLAIM_BATCH")) {
      values.put("app.workers.claim-batch", HardwareSizing.claimBatch(cpus));
    }
    if (unsetOrZero(environment, "spring.datasource.hikari.maximum-pool-size", "HIKARI_MAX_POOL")) {
      values.put("spring.datasource.hikari.maximum-pool-size", HardwareSizing.hikariPool(cpus));
    }
    if (unsetOrZero(environment, "server.tomcat.threads.max", "SERVER_TOMCAT_THREADS_MAX")) {
      values.put("server.tomcat.threads.max", HardwareSizing.tomcatMax(cpus));
    }
    if (!values.isEmpty()) {
      environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, values));
    }
  }

  static boolean unsetOrZero(ConfigurableEnvironment environment, String... keys) {
    for (String key : keys) {
      String value = environment.getProperty(key);
      if (value != null && !value.isBlank() && !"0".equals(value.trim())) {
        return false;
      }
    }
    return true;
  }
}
