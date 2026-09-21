package com.dealership.shared.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private String publicHost = "https://dealership.greatnerve.com";
  private String localHost = "http://localhost:8080";
  private final Cors cors = new Cors();
  private final Jwt jwt = new Jwt();
  private final Reminders reminders = new Reminders();
  private final Notifications notifications = new Notifications();
  private final Workers workers = new Workers();
  private final Idempotency idempotency = new Idempotency();
  private final RateLimit rateLimit = new RateLimit();
  private final Pagination pagination = new Pagination();
  private final Appointments appointments = new Appointments();

  public String getPublicHost() {
    return publicHost;
  }

  public void setPublicHost(String publicHost) {
    this.publicHost = publicHost;
  }

  public String getLocalHost() {
    return localHost;
  }

  public void setLocalHost(String localHost) {
    this.localHost = localHost;
  }

  public Cors getCors() {
    return cors;
  }

  public Jwt getJwt() {
    return jwt;
  }

  public Reminders getReminders() {
    return reminders;
  }

  public Notifications getNotifications() {
    return notifications;
  }

  public Workers getWorkers() {
    return workers;
  }

  public Idempotency getIdempotency() {
    return idempotency;
  }

  public RateLimit getRateLimit() {
    return rateLimit;
  }

  public Pagination getPagination() {
    return pagination;
  }

  public Appointments getAppointments() {
    return appointments;
  }

  @PostConstruct
  void validate() {
    reminders.offsetMinutes();
    byte[] secret =
        jwt.getSecret() == null ? new byte[0] : jwt.getSecret().getBytes(StandardCharsets.UTF_8);
    if (secret.length < 32) {
      throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes");
    }
  }

  public static class Cors {
    private List<String> origins = List.of("*");

    public List<String> getOrigins() {
      return origins;
    }

    public void setOrigins(List<String> origins) {
      this.origins = origins;
    }
  }

  public static class Jwt {
    private String secret = "local-dev-only-change-me-32bytes-min!!";
    private Duration ttl = Duration.ofDays(7);

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }

    public Duration getTtl() {
      return ttl;
    }

    public void setTtl(Duration ttl) {
      this.ttl = ttl;
    }
  }

  public static class Reminders {
    private Duration noShowGrace = Duration.ofHours(1);
    private List<Duration> offsets =
        new ArrayList<>(List.of(Duration.ofHours(24), Duration.ofHours(2)));

    public Duration getNoShowGrace() {
      return noShowGrace;
    }

    public void setNoShowGrace(Duration noShowGrace) {
      this.noShowGrace = noShowGrace;
    }

    public List<Duration> getOffsets() {
      return offsets;
    }

    public void setOffsets(List<Duration> offsets) {
      this.offsets = offsets;
    }

    public List<Integer> offsetMinutes() {
      List<Integer> minutes = new ArrayList<>(offsets.size());
      Set<Integer> seen = new HashSet<>();
      for (Duration offset : offsets) {
        if (offset == null || offset.isNegative() || offset.isZero()) {
          throw new IllegalStateException("each reminder offset must be positive");
        }
        long value = offset.toMinutes();
        if (!offset.equals(Duration.ofMinutes(value))) {
          throw new IllegalStateException("each reminder offset must be whole minutes");
        }
        if (value > Integer.MAX_VALUE) {
          throw new IllegalStateException("reminder offset exceeds integer minutes");
        }
        int asMinutes = (int) value;
        if (!seen.add(asMinutes)) {
          throw new IllegalStateException("reminder offsets must be unique");
        }
        minutes.add(asMinutes);
      }
      return List.copyOf(minutes);
    }
  }

  public static class Notifications {
    private String mode = "stub";
    private String from = "dheeraj@greatnerve.com";
    private String logDir = "logs";

    public String getMode() {
      return mode;
    }

    public void setMode(String mode) {
      this.mode = mode;
    }

    public String getFrom() {
      return from;
    }

    public void setFrom(String from) {
      this.from = from;
    }

    public String getLogDir() {
      return logDir;
    }

    public void setLogDir(String logDir) {
      this.logDir = logDir;
    }
  }

  public static class Workers {
    private int concurrency = 2;
    private Duration lease = Duration.ofSeconds(30);
    private Duration pollInterval = Duration.ofMillis(500);

    public int getConcurrency() {
      return Math.min(4, Math.max(2, concurrency));
    }

    public void setConcurrency(int concurrency) {
      this.concurrency = concurrency;
    }

    public Duration getLease() {
      return lease;
    }

    public void setLease(Duration lease) {
      this.lease = lease;
    }

    public Duration getPollInterval() {
      return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
      this.pollInterval = pollInterval;
    }
  }

  public static class Idempotency {
    private Duration ttl = Duration.ofHours(24);

    public Duration getTtl() {
      return ttl;
    }

    public void setTtl(Duration ttl) {
      this.ttl = ttl;
    }
  }

  public static class RateLimit {
    private boolean enabled = true;
    private long loginCapacity = 15;
    private Duration loginPeriod = Duration.ofSeconds(60);
    private long customerCapacity = 15;
    private Duration customerPeriod = Duration.ofSeconds(60);
    private long staffCapacity = 15;
    private Duration staffPeriod = Duration.ofSeconds(60);
    private long ipCapacity = 15;
    private Duration ipPeriod = Duration.ofSeconds(60);
    private boolean trustForwardedFor = false;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public long getLoginCapacity() {
      return loginCapacity;
    }

    public void setLoginCapacity(long loginCapacity) {
      this.loginCapacity = loginCapacity;
    }

    public Duration getLoginPeriod() {
      return loginPeriod;
    }

    public void setLoginPeriod(Duration loginPeriod) {
      this.loginPeriod = loginPeriod;
    }

    public long getCustomerCapacity() {
      return customerCapacity;
    }

    public void setCustomerCapacity(long customerCapacity) {
      this.customerCapacity = customerCapacity;
    }

    public Duration getCustomerPeriod() {
      return customerPeriod;
    }

    public void setCustomerPeriod(Duration customerPeriod) {
      this.customerPeriod = customerPeriod;
    }

    public long getStaffCapacity() {
      return staffCapacity;
    }

    public void setStaffCapacity(long staffCapacity) {
      this.staffCapacity = staffCapacity;
    }

    public Duration getStaffPeriod() {
      return staffPeriod;
    }

    public void setStaffPeriod(Duration staffPeriod) {
      this.staffPeriod = staffPeriod;
    }

    public long getIpCapacity() {
      return ipCapacity;
    }

    public void setIpCapacity(long ipCapacity) {
      this.ipCapacity = ipCapacity;
    }

    public Duration getIpPeriod() {
      return ipPeriod;
    }

    public void setIpPeriod(Duration ipPeriod) {
      this.ipPeriod = ipPeriod;
    }

    public boolean isTrustForwardedFor() {
      return trustForwardedFor;
    }

    public void setTrustForwardedFor(boolean trustForwardedFor) {
      this.trustForwardedFor = trustForwardedFor;
    }
  }

  public static class Appointments {
    private boolean oneConfirmedPerVehicle = true;

    public boolean isOneConfirmedPerVehicle() {
      return oneConfirmedPerVehicle;
    }

    public void setOneConfirmedPerVehicle(boolean oneConfirmedPerVehicle) {
      this.oneConfirmedPerVehicle = oneConfirmedPerVehicle;
    }
  }

  public static class Pagination {
    private int defaultSize = 100;
    private int maxSize = 1000;
    private int qMax = 100;

    public int getDefaultSize() {
      return defaultSize;
    }

    public void setDefaultSize(int defaultSize) {
      this.defaultSize = defaultSize;
    }

    public int getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(int maxSize) {
      this.maxSize = maxSize;
    }

    public int getQMax() {
      return qMax;
    }

    public void setQMax(int qMax) {
      this.qMax = qMax;
    }
  }
}
