package com.readingtracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("독서 기록 사이트 API")
                        .description("독서 기록을 관리하는 REST API")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Reading Tracker Team")
                                .email("support@readingtracker.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("개발 서버"),
                        new Server().url("https://api.readingtracker.com").description("운영 서버")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT 인증 토큰을 입력하세요 (Bearer 접두사 제외)")
                        )
                );
    }
}

