plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.soma"
version = "0.0.1-SNAPSHOT"
description = "test-wes"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot 4.x BOM은 Testcontainers 버전을 관리하지 않으므로 직접 임포트한다.
// Spring Cloud AWS 4.0.x가 Boot 4 계열 대응 라인이다(3.x는 Boot 3 전용).
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
        mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:4.0.2")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.awspring.cloud:spring-cloud-aws-starter-parameter-store")
    // presigned URL 발급용. 이미지 바이트는 이 서버를 거치지 않는다.
    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3")

    // 세션 로그인. 가입 API는 없고 DB에 직접 넣은 계정으로만 로그인한다.
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // pgvector의 vector 타입을 float[] 필드로 매핑한다.
    implementation("org.hibernate.orm:hibernate-vector")

    // vector 확장은 Hibernate ddl-auto로 만들 수 없다(테이블보다 먼저 CREATE EXTENSION이
    // 필요). 스키마는 마이그레이션이 소유한다.
    // Boot 4는 Flyway 자동설정을 별도 모듈로 뺐다. flyway-core만 넣으면 마이그레이션이 돌지 않는다.
    implementation("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // springdoc 2.x는 Spring Boot 3 전용이다. Boot 4는 3.x 라인을 써야 한다.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    // CD의 배포 성공 판정이 /actuator/health 200을 본다.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // 테스트는 AWS도 로컬 DB도 없이 떠야 한다. 컨테이너로 postgres를 띄운다.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.jar {
    enabled = false
}
