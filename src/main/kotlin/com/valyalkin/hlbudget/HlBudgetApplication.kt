package com.valyalkin.hlbudget

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class HlBudgetApplication

fun main(args: Array<String>) {
    runApplication<HlBudgetApplication>(*args)
}
