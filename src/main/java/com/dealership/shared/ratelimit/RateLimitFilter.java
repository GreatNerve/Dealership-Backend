package com.dealership.shared.ratelimit;

import com.dealership.identity.Role;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiResponse;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true")
public class RateLimitFilter extends OncePerRequestFilter {

  private final AppProperties properties;
  private final ObjectMapper mapper;
  private final RedisClient redisClient;
  private final StatefulRedisConnection<String, byte[]> connection;
  private final ProxyManager<String> buckets;

  public RateLimitFilter(
      AppProperties properties, ObjectMapper mapper, LettuceConnectionFactory lettuce) {
    this.properties = properties;
    this.mapper = mapper;
    // Upstash (and similar) need TLS + password; plain redis://host:port is local only.
    RedisStandaloneConfiguration redis = lettuce.getStandaloneConfiguration();
    String host = redis.getHostName() == null ? "localhost" : redis.getHostName();
    RedisURI.Builder uri =
        RedisURI.builder().withHost(host).withPort(redis.getPort()).withSsl(lettuce.isUseSsl());
    String username = redis.getUsername();
    char[] passwordChars = redis.getPassword().toOptional().orElse(null);
    if (passwordChars != null && passwordChars.length > 0) {
      if (username != null && !username.isBlank()) {
        uri.withAuthentication(username, passwordChars);
      } else {
        uri.withPassword(passwordChars);
      }
    }
    this.redisClient = RedisClient.create(uri.build());
    this.connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    this.buckets =
        LettuceBasedProxyManager.builderFor(connection)
            .withExpirationStrategy(
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                    Duration.ofSeconds(60)))
            .build();
  }

  @PreDestroy
  void close() {
    connection.close();
    redisClient.shutdown();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!properties.getRateLimit().isEnabled()) {
      return true;
    }
    String path = request.getRequestURI();
    return path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String endpoint = RateLimitKeys.endpoint(request.getMethod(), request.getRequestURI());
    AuthPrincipal principal = principal();
    String who;
    long capacity;
    Duration period;
    if (RateLimitKeys.loginOrRegister(endpoint)) {
      who = "ip:" + clientIp(request);
      capacity = properties.getRateLimit().getLoginCapacity();
      period = properties.getRateLimit().getLoginPeriod();
    } else if (principal != null) {
      who = "user:" + principal.userId();
      boolean staff = principal.role() == Role.DEALERSHIP_STAFF;
      capacity =
          staff
              ? properties.getRateLimit().getStaffCapacity()
              : properties.getRateLimit().getCustomerCapacity();
      period =
          staff
              ? properties.getRateLimit().getStaffPeriod()
              : properties.getRateLimit().getCustomerPeriod();
    } else {
      who = "ip:" + clientIp(request);
      capacity = properties.getRateLimit().getIpCapacity();
      period = properties.getRateLimit().getIpPeriod();
    }
    if (!consume(response, who + ":" + endpoint, capacity, period)) {
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean consume(HttpServletResponse response, String key, long capacity, Duration period)
      throws IOException {
    Supplier<io.github.bucket4j.BucketConfiguration> config =
        () ->
            io.github.bucket4j.BucketConfiguration.builder()
                .addLimit(
                    Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                .build();
    Bucket bucket = buckets.builder().build(key, config);
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    response.setHeader("X-RateLimit-Limit", Long.toString(capacity));
    response.setHeader(
        "X-RateLimit-Remaining", Long.toString(Math.max(0, probe.getRemainingTokens())));
    long reset = Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds();
    response.setHeader("X-RateLimit-Reset", Long.toString(reset));
    if (probe.isConsumed()) {
      return true;
    }
    response.setStatus(429);
    response.setHeader("Retry-After", Long.toString(Math.max(1, reset)));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(
        response.getWriter(), ApiResponse.fail(ApiErrorCode.RATE_LIMITED, "Too many requests"));
    return false;
  }

  private String clientIp(HttpServletRequest request) {
    if (properties.getRateLimit().isTrustForwardedFor()) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].trim();
      }
    }
    return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
  }

  private static AuthPrincipal principal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
      return p;
    }
    return null;
  }
}
