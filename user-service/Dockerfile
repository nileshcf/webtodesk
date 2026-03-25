# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom and modules
COPY pom.xml .
COPY common/ common/
COPY user-service/ user-service/
COPY api-gateway/pom.xml api-gateway/pom.xml

# Install common module specifically first to populate local repo
RUN mvn clean install -pl common -DskipTests

# Package user-service
RUN mvn clean package -pl user-service -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/user-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]