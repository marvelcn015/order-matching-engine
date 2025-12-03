package cex.crypto.trading.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI Configuration
 * Configures API documentation for the Order Matching Engine
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(apiServers());
    }

    /**
     * API information
     */
    private Info apiInfo() {
        return new Info()
                .title("Order Matching Engine API")
                .description("REST APIs for cryptocurrency exchange order matching system. " +
                             "Supports LIMIT and MARKET orders with price-time priority matching.")
                .version("1.0.0")
                .contact(new Contact()
                        .name("Trading Platform Team")
                        .email("support@example.com"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    /**
     * API servers configuration
     */
    private List<Server> apiServers() {
        Server localServer = new Server()
                .url("http://localhost:" + serverPort)
                .description("Local Development Server");

        return List.of(localServer);
    }
}
