package com.dealership.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class AppPropertiesWorkersTest {

  @Test
  void zeroClaimBatchUsesHardwareSizing() {
    assertEquals(
        HardwareSizing.claimBatch(HardwareSizing.cpus()),
        new AppProperties.Workers().getClaimBatch());
  }

  @Test
  void claimBatchMustBeBetweenOneAndFiftyWhenPinned() {
    AppProperties properties = new AppProperties();
    properties.setEnvironment(new MockEnvironment());
    properties.getJwt().setSecret("unit-test-secret-must-be-32-bytes!!");
    properties.getNotifications().setWebhookSecret("unit-test-webhook-secret");
    properties.getWorkers().setClaimBatch(51);
    assertThrows(IllegalStateException.class, properties::validate);
    properties.getWorkers().setClaimBatch(0);
    properties.validate();
    properties.getWorkers().setClaimBatch(7);
    properties.validate();
    assertEquals(7, properties.getWorkers().getClaimBatch());
  }

  @Test
  void zeroEnvIsReplacedFromCpus() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("app.workers.claim-batch", "0");
    env.setProperty("spring.datasource.hikari.maximum-pool-size", "0");
    env.setProperty("server.tomcat.threads.max", "0");
    new HardwareEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
    int cpus = HardwareSizing.cpus();
    assertEquals(
        HardwareSizing.claimBatch(cpus), env.getProperty("app.workers.claim-batch", Integer.class));
    assertEquals(
        HardwareSizing.hikariPool(cpus),
        env.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class));
    assertEquals(
        HardwareSizing.tomcatMax(cpus),
        env.getProperty("server.tomcat.threads.max", Integer.class));
  }

  @Test
  void pinnedEnvIsKept() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("app.workers.claim-batch", "7");
    env.setProperty("HIKARI_MAX_POOL", "12");
    env.setProperty("SERVER_TOMCAT_THREADS_MAX", "80");
    new HardwareEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
    assertEquals(7, env.getProperty("app.workers.claim-batch", Integer.class));
    assertEquals("12", env.getProperty("HIKARI_MAX_POOL"));
    assertEquals("80", env.getProperty("SERVER_TOMCAT_THREADS_MAX"));
  }
}
