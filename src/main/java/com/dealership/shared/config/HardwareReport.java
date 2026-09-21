package com.dealership.shared.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class HardwareReport implements ApplicationListener<ApplicationReadyEvent> {

  private static final Logger log = LoggerFactory.getLogger(HardwareReport.class);

  private final AppProperties properties;
  private final DataSource dataSource;
  private final Environment environment;

  public HardwareReport(AppProperties properties, DataSource dataSource, Environment environment) {
    this.properties = properties;
    this.dataSource = dataSource;
    this.environment = environment;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    int hikari = dataSource instanceof HikariDataSource pool ? pool.getMaximumPoolSize() : -1;
    log.info(
        "hardware cpus={} heapMb={} hikari={} tomcatMax={} claimBatch={} workers={}",
        HardwareSizing.cpus(),
        Runtime.getRuntime().maxMemory() / (1024 * 1024),
        hikari,
        environment.getProperty("server.tomcat.threads.max", Integer.class, -1),
        properties.getWorkers().getClaimBatch(),
        properties.getWorkers().getConcurrency());
  }
}
