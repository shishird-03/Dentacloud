package com.dentacloud.shared;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * docs/openapi.yaml is exported from this (OpenApiExportTest) and the Android client is generated
 * from that file. Fixed server and stable operation ids keep the export deterministic.
 */
@Configuration
class OpenApiConfig {
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("DentaCloud API").version("v1"))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }

    /** operationId = "patient" + "List" style names instead of springdoc's list, list_1, list_2. */
    @Bean
    OperationCustomizer operationIds() {
        return (op, handler) -> {
            String type = handler.getBeanType().getSimpleName().replaceAll("(Controller|Catalog)$", "");
            String method = handler.getMethod().getName();
            op.setOperationId(Character.toLowerCase(type.charAt(0)) + type.substring(1)
                    + Character.toUpperCase(method.charAt(0)) + method.substring(1));
            return op;
        };
    }
}
