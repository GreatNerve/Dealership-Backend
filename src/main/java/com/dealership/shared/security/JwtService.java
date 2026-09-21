package com.dealership.shared.security;

import com.dealership.identity.Role;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.time.TimeProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final AppProperties properties;
  private final TimeProvider time;

  public JwtService(AppProperties properties, TimeProvider time) {
    this.properties = properties;
    this.time = time;
  }

  public String issue(UUID userId, String email, Role role) {
    Instant now = time.now();
    Instant exp = now.plus(properties.getJwt().getTtl());
    return Jwts.builder()
        .subject(userId.toString())
        .claim("email", email)
        .claim("role", role.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(key())
        .compact();
  }

  public AuthPrincipal parse(String token) {
    Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    return new AuthPrincipal(
        UUID.fromString(claims.getSubject()),
        claims.get("email", String.class),
        Role.valueOf(claims.get("role", String.class)));
  }

  public long expiresInSeconds() {
    return properties.getJwt().getTtl().toSeconds();
  }

  private SecretKey key() {
    byte[] bytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes");
    }
    return Keys.hmacShaKeyFor(bytes);
  }
}
