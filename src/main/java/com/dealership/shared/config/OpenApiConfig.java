package com.dealership.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  static final String SCHEME = "OAuth2PasswordBearer";

  @Bean
  OpenAPI openApi(AppProperties properties) {
    return new OpenAPI()
        .info(new Info().title("Dealership Appointment API").version("v1"))
        .servers(
            List.of(
                new Server().url(properties.getLocalHost()).description("local"),
                new Server().url(properties.getPublicHost()).description("production")))
        .addSecurityItem(new SecurityRequirement().addList(SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .description(
                            "Username is the account email. Leave client_id and client_secret"
                                + " empty.")
                        .flows(
                            new OAuthFlows()
                                .password(
                                    new OAuthFlow()
                                        .tokenUrl("/api/v1/auth/login")
                                        .scopes(new Scopes())))));
  }

  @Bean
  OpenApiCustomizer apiEnvelope() {
    // ResponseBodyAdvice wraps at runtime; springdoc would otherwise document the inner type.
    return openApi -> {
      if (openApi.getPaths() == null) {
        return;
      }
      openApi.getPaths().values().forEach(path -> path.readOperations().forEach(this::wrapSuccess));
    };
  }

  private void wrapSuccess(Operation operation) {
    if (operation.getResponses() == null) {
      return;
    }
    operation
        .getResponses()
        .forEach(
            (code, response) -> {
              if (code == null || !code.startsWith("2") || response.getContent() == null) {
                return;
              }
              response
                  .getContent()
                  .forEach(
                      (media, mediaType) -> {
                        if (mediaType == null
                            || mediaType.getSchema() == null
                            || media.contains("octet-stream")) {
                          return;
                        }
                        mediaType.setSchema(envelope(mediaType.getSchema()));
                      });
            });
  }

  private static Schema<?> envelope(Schema<?> data) {
    if (data.getProperties() != null && data.getProperties().containsKey("success")) {
      return data;
    }
    Schema<Object> wrapped = new ObjectSchema();
    wrapped.addProperty("success", new BooleanSchema().example(true));
    wrapped.addProperty("data", data);
    Schema<String> error = new StringSchema();
    error.setNullable(true);
    wrapped.addProperty("error", error);
    Schema<String> message = new StringSchema();
    message.setNullable(true);
    wrapped.addProperty("message", message);
    wrapped.addProperty(
        "correlationId",
        new StringSchema().format("uuid").example("3fa85f64-5717-4562-b3fc-2c963f66afa6"));
    wrapped.setRequired(List.of("success", "data", "error", "message", "correlationId"));
    return wrapped;
  }
}
