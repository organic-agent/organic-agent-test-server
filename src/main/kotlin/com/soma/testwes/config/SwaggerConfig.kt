package com.soma.testwes.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    /**
     * 안 잡아주면 Swagger UI가 요청이 들어온 호스트를 서버로 쓴다. 프록시 뒤에 있으면
     * 내부 주소가 노출되므로 prod에서는 Parameter Store가 준 도메인을 못박는다.
     */
    @Bean
    fun openApi(@Value("\${springdoc.server-url:http://localhost:8080}") serverUrl: String): OpenAPI =
        OpenAPI().servers(listOf(Server().url(serverUrl)))
}
