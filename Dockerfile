# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q dependency:go-offline
COPY src/ src/
# Tests need Docker (Testcontainers) and run in CI; the image build only packages.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -DskipTests package \
 && java -Djarmode=tools -jar target/bunny-code-*.jar extract --layers --launcher --destination target/extracted

# ---- runtime --------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre
# The SSH sandbox transport shells out to `ssh`; the API never runs user code itself.
RUN apt-get update \
 && apt-get install -y --no-install-recommends openssh-client \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home bunny
WORKDIR /app
# Least-changing layers first so code-only releases reuse the dependency layers.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER bunny
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
