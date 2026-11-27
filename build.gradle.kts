plugins {
    kotlin("jvm") version "2.4.20"
    application
}

group = "com.kantor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Serwer HTTP + JSON
    implementation("io.javalin:javalin:7.2.3")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.slf4j:slf4j-simple:2.0.20")

    // Baza danych: PostgreSQL + pula połączeń + JDBI + migracje Flyway
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.jdbi:jdbi3-kotlin:3.55.0")
    implementation("org.flywaydb:flyway-core:13.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:13.10.0")

    // Uwierzytelnianie: hashowanie haseł (bcrypt) i tokeny JWT
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("com.auth0:java-jwt:4.5.0")

    // Konfiguracja z pliku .env
    implementation("io.github.cdimascio:java-dotenv:5.2.2")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("MainKt")
}
