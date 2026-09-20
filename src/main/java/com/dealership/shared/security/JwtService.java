package com.dealership.shared.security;

import com.dealership.identity.Role;
import com.dealership.shared.config.AppProperties;
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

  public JwtService(AppProperties properties) {
    this.properties = properties;
  }

  public String issue(UUID userId, String email, Role role) {
    Instant now = Instant.now();
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
      bytes = java.util.Arrays.copyOf(bytes, 32);
    }
    return Keys.hmacShaKeyFor(bytes);
  }
}
