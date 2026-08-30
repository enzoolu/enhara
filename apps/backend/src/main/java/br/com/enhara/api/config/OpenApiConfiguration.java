package br.com.enhara.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI enharaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Enhara API")
                .version("0.1.0")
                .description("API local de veículos, telemetria, diagnósticos, alertas e eventos em tempo real."));
    }
}
