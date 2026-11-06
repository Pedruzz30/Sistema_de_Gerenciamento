# Multi-stage build: Maven never runs inside the published runtime container.
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
# Maven Central may rate-limit clean CI builders (HTTP 429). Retry only this
# dependency download stage with a bounded backoff; never ignore final failure.
RUN set -eu; \
    for attempt in 1 2 3; do \
      if mvn -B -ntp -Dmaven.wagon.http.retryHandler.count=3 dependency:go-offline; then exit 0; fi; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      sleep "$((attempt * 10))"; \
    done
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
