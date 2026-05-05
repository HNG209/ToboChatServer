FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
# Copy file cấu hình và tải trước các thư viện để cache
COPY pom.xml .
RUN mvn dependency:go-offline
# Copy source code và build
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]