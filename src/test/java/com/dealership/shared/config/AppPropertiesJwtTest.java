package com.dealership.shared.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AppPropertiesJwtTest {

  @Test
  void committedSecretIsRejectedOutsideDevAndTest() {
    MockEnvironment prod = new MockEnvironment();
    assertTrue(AppProperties.rejectsCommittedJwtSecret(prod));
    MockEnvironment dev = new MockEnvironment();
    dev.setActiveProfiles("dev");
    assertFalse(AppProperties.rejectsCommittedJwtSecret(dev));
    MockEnvironment test = new MockEnvironment();
    test.setActiveProfiles("test");
    assertFalse(AppProperties.rejectsCommittedJwtSecret(test));
  }

  @Test
  void productionRejectsCommittedSecretOnValidate() {
    AppProperties properties = new AppProperties();
    properties.setEnvironment(new MockEnvironment());
    assertThrows(IllegalStateException.class, properties::validate);
  }
}
