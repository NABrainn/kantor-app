plugins {
    kotlin("jvm") version "2.4.20"
}

group = "com.kantor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jdbi:jdbi3-kotlin:3.55.0")
    implementation("org.flywaydb:flyway-core:13.9.0")
    implementation("org.flywaydb:flyway-database-postgresql:13.10.0")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("io.javalin:javalin:7.2.3")
    implementation("org.slf4j:slf4j-simple:2.0.20")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("io.github.cdimascio:java-dotenv:5.2.2")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}