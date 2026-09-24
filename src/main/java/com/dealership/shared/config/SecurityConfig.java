package com.dealership.shared.config;

import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiErrorWriter;
import com.dealership.shared.ratelimit.RateLimitFilter;
import com.dealership.shared.security.JwtAuthFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;
  private final AppProperties properties;
  private final RateLimitFilter rateLimitFilter;
  private final ApiErrorWriter errors;

  public SecurityConfig(
      JwtAuthFilter jwtAuthFilter,
      AppProperties properties,
      ApiErrorWriter errors,
      @Autowired(required = false) RateLimitFilter rateLimitFilter) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.properties = properties;
    this.errors = errors;
    this.rateLimitFilter = rateLimitFilter;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .headers(
            headers ->
                headers
                    .contentTypeOptions(Customizer.withDefaults())
                    .frameOptions(frame -> frame.deny())
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                    // springdoc Swagger UI ships inline script and style
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'self';"
                                    + " form-action 'self'; script-src 'self' 'unsafe-inline';"
                                    + " style-src 'self' 'unsafe-inline'; img-src 'self' data:;"
                                    + " connect-src 'self'; font-src 'self'")))
        .cors(Customizer.withDefaults())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers("/api/v1/webhooks/**")
                    .permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // Filter 401/403 never reach GlobalExceptionHandler; write the same envelope here.
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authException) ->
                            errors.write(
                                response, ApiErrorCode.UNAUTHORIZED, "Authentication required"))
                    .accessDeniedHandler(
                        (request, response, denied) ->
                            errors.write(response, ApiErrorCode.FORBIDDEN, "Access denied")))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    if (rateLimitFilter != null) {
      http.addFilterAfter(rateLimitFilter, JwtAuthFilter.class);
    }
    return http.build();
  }

  @Bean
  @ConditionalOnBean(RateLimitFilter.class)
  FilterRegistrationBean<RateLimitFilter> disableDuplicateRateLimit(RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(properties.getCors().getOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(
        List.of(
            "X-Correlation-Id",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
            "Retry-After"));
    boolean wildcard =
        properties.getCors().getOrigins().stream().anyMatch(origin -> origin.contains("*"));
    config.setAllowCredentials(!wildcard);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
