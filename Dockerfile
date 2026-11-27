# Etap 1: budowanie aplikacji Gradle Wrapperem z repozytorium
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /project
COPY . .
# gradlew mógł dostać windowsowe końce linii (CRLF) – usuwamy je, żeby skrypt dał się uruchomić
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew installDist --no-daemon

# Etap 2: lekki obraz tylko z JRE i gotową aplikacją (bin/ + lib/)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /project/build/install/kantor-app/ .
EXPOSE 2137
ENTRYPOINT ["bin/kantor-app"]
