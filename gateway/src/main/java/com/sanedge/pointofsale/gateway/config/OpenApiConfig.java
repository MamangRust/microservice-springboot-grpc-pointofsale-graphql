package com.sanedge.pointofsale.gateway.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {
        @Value("${pointofsale.openapi.dev-url}")
        private String devUrl;

        @Value("${pointofsale.openapi.prod-url}")
        private String prodUrl;

        private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

        @Bean
        public OpenAPI myOpenAPI() {
                Server devServer = new Server();
                devServer.setUrl(devUrl);
                devServer.setDescription("pointofsale  Development Server");

                Server prodServer = new Server();
                prodServer.setUrl(prodUrl);
                prodServer.setDescription("pointofsale Production Server");

                Contact contact = new Contact();
                contact.setEmail("renaldyhidayatt@gmail.com");
                contact.setName("Renaldy Hidayat");
                contact.setUrl("https://pointofsale.sanedge.com");

                License mitLicense = new License()
                                .name("MIT License")
                                .url("https://choosealicense.com/licenses/mit/");

                Info info = new Info()
                                .title("Point of Sale API")
                                .version("1.0")
                                .contact(contact)
                                .description("REST API documentation for the Point of Sale service. This API handles POS transactions, cashier management, and reporting securely.")
                                .termsOfService("https://pos.sanedge.com/terms")
                                .license(mitLicense);

                return new OpenAPI()
                                .info(info)
                                .servers(List.of(devServer, prodServer))
                                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                                .schemaRequirement(SECURITY_SCHEME_NAME, createSecurityScheme());
        }

        private SecurityScheme createSecurityScheme() {
                return new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your JWT token in the format: Bearer <token>")
                                .in(SecurityScheme.In.HEADER);
        }
}
