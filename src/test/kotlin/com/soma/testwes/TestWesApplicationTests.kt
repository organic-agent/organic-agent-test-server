package com.soma.testwes

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
class TestWesApplicationTests {

    @Test
    fun contextLoads() {
    }

    companion object {
        // @ServiceConnection이 spring.datasource.* 를 이 컨테이너로 채운다.
        // prod처럼 Parameter Store를 타지 않으므로 CI에 AWS 자격증명이 필요 없다.
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
