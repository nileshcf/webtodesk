# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom and modules
COPY pom.xml .
COPY common/ common/
COPY api-gateway/ api-gateway/
COPY user-service/pom.xml user-service/pom.xml
# CHANGED: The Maven build previously failed with 'Child module does not exist' because the root pom.xml
# requires all module poms to be present. Added the following two lines to copy the missing poms and fix the build.
COPY conversion-service/pom.xml conversion-service/pom.xml
COPY discovery-service/pom.xml discovery-service/pom.xml

# Install common module first
RUN mvn clean install -pl common -DskipTests

# Package api-gateway
RUN mvn clean package -pl api-gateway -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/api-gateway/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]