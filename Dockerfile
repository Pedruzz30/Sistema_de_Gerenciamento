# Multi-stage build: Maven never runs inside the published runtime container.
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre
RUN groupadd --system --gid 10001 stockos && \
    useradd --system --uid 10001 --gid 10001 --home-dir /opt/stockos stockos
WORKDIR /opt/stockos
COPY --from=build --chown=10001:10001 /workspace/target/sistema-gerenciamento-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/stockos/app.jar"]
