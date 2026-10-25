import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.GroupingEntityType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

group = "com.valyalkin"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // Makes Spring's nullability annotations binding on Kotlin types.
        freeCompilerArgs.addAll("-Xjsr305=strict")
        allWarningsAsErrors = true
    }
}

// Hibernate needs non-final entity classes for lazy proxies; kotlin("plugin.jpa") only adds the
// no-arg constructor.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation(libs.springdoc.openapi.webmvc)
    // The starter, not bare flyway-core: Boot 4 keeps Flyway's autoconfiguration in the starter.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // developmentOnly does not extend implementation, so the BOM must be declared again here.
    developmentOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    detektPlugins(libs.detekt.formatting)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.mockk)
    testImplementation(libs.wiremock.standalone)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

ktlint {
    version = libs.versions.ktlintTool
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("detekt.yml"))
    basePath = projectDir.absolutePath
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required = true
        sarif.required = true
        txt.required = false
        md.required = false
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // One shared Testcontainers Postgres; parallel forks would start several.
    maxParallelForks = 1
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kover {
    reports {
        filters {
            excludes {
                // Wiring and data holders: their generated members would pad the numbers.
                classes(
                    "com.valyalkin.hlbudget.HlBudgetApplicationKt",
                    "com.valyalkin.hlbudget.HlBudgetApplication",
                    "com.valyalkin.hlbudget.config.*Config",
                    "com.valyalkin.hlbudget.config.*Config\$*",
                    "com.valyalkin.hlbudget.config.HlBudgetProperties*",
                    "com.valyalkin.hlbudget.dto.*Response",
                    "com.valyalkin.hlbudget.repository.*Entity",
                )
            }
        }
        verify {
            rule("the service as a whole is covered") {
                bound {
                    minValue = 80
                    coverageUnits = CoverageUnit.LINE
                }
                bound {
                    minValue = 75
                    coverageUnits = CoverageUnit.BRANCH
                }
            }
            rule("no layer is left untested") {
                groupBy = GroupingEntityType.PACKAGE
                bound {
                    minValue = 75
                    coverageUnits = CoverageUnit.LINE
                }
            }
        }
    }
}

// `./gradlew check` is the single gate: ktlint, detekt, tests and coverage.
tasks.named("check") {
    dependsOn(tasks.named("koverVerify"))
}
