# Build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime
FROM gcr.io/distroless/java17-debian12

WORKDIR /app
COPY --from=builder /app/target/cart-service.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar", "app.jar"]