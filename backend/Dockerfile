# Etapa 1: Compilación
FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /app

COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B package


# Etapa 2: Ejecución
FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

COPY --from=build /app/target/*.jar /app/app.jar

ENV SERVER_PORT=8888

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

