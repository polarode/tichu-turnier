plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    kotlin("plugin.jpa") version "2.1.21"
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1"

// Spring Boot 3.4 manages Testcontainers 1.20.x, whose docker-java cannot talk to
// Docker Engine 29 (the engine answers with 400). 1.21.x fixed the negotiation.
ext["testcontainers.version"] = "1.21.4"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    // Fixed name so the Docker image can copy it without knowing the version.
    archiveFileName = "app.jar"
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Testcontainers needs the Docker endpoint; on macOS it is not at the Linux
    // default the probe tries, so hand it the socket explicitly.
    environment["DOCKER_HOST"] = System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock"
}
