package com.soma.testwes

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TestWesApplication

fun main(args: Array<String>) {
    runApplication<TestWesApplication>(*args)
}
