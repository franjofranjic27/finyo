package ch.finyoapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finyoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("finyo API")
                        .description("Personal finance planner REST API")
                        .version("0.0.1-SNAPSHOT"));
    }
}
