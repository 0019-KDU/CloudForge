# Production image for the CloudForge backend (Spring Boot 4, Java 21).
#
# Build (from the backend/ directory):
#   docker build -t cloudforge-backend:0.0.1 .
#
# Run (configuration comes only from environment variables; no profile is active by default):
#   docker run -d --name cloudforge-backend -p 8080:8080 \
#     -e CLOUDFORGE_DB_URL=jdbc:postgresql://db-host:5432/cloudforge \
#     -e CLOUDFORGE_DB_USERNAME=cloudforge \
#     -e CLOUDFORGE_DB_PASSWORD=... \
#     -e CLOUDFORGE_JWT_SECRET=... \
#     --read-only --tmpfs /tmp \
#     cloudforge-backend:0.0.1
#
# Stages:
#   1. build   - JDK + Maven wrapper compile the jar, then Spring Boot's jarmode splits it into layers
#   2. jre     - jlink builds a trimmed Java runtime with exactly the modules the app needs
#   3. runtime - Ubuntu + that runtime + the layered app; non-root, healthcheck, no build tools
#
# Base images are pinned by exact version AND digest (reproducible builds). They are written out in
# each FROM line, not through ARGs, so Dependabot can update the tag and digest together.

# ---------------------------------------------------------------------------------------------------
# Stage 1: build the jar and extract its layers
# ---------------------------------------------------------------------------------------------------
FROM eclipse-temurin:21.0.13_9-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d AS build
WORKDIR /workspace

# Wrapper and pom first: this layer and the dependency download only rerun when they change.
COPY --chmod=0755 mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

# Source last. Tests are not run here: unit tests and the Testcontainers *IT tests belong in CI
# (`./mvnw verify`), which needs Docker and must pass before this image is built.
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress package -DskipTests \
 && cp target/*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------------------------------------------------------------------------------------------------
# Stage 2: custom Java runtime
# ---------------------------------------------------------------------------------------------------
# The stock Temurin JRE image leaves out jdk.random, which RandomGenerator.getDefault() (used by the
# provisioning RetryPolicy) needs. This runtime is the stock JRE module set plus jdk.random, minus the
# remote debugger agent. It does not depend on the build stage, so code changes never rerun it.
FROM eclipse-temurin:21.0.13_9-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d AS jre
RUN jlink \
      --add-modules java.se,jdk.random,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.charsets,jdk.localedata,jdk.zipfs,jdk.naming.dns,jdk.net,jdk.security.auth,jdk.security.jgss,jdk.management,jdk.management.agent,jdk.management.jfr,jdk.jfr,jdk.httpserver,jdk.nio.mapmode \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress=zip-6 \
      --output /opt/java/runtime

# ---------------------------------------------------------------------------------------------------
# Stage 3: runtime
# ---------------------------------------------------------------------------------------------------
FROM ubuntu:24.04@sha256:fe81560990fae86dc4743202b2cb4d3df98d0bcf949173cecef15ce1127ef852 AS runtime

ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="cloudforge-backend" \
      org.opencontainers.image.description="Cloud resource provisioning and governance platform - backend API" \
      org.opencontainers.image.vendor="CloudForge" \
      org.opencontainers.image.licenses="Proprietary" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"

# Patch the OS, add only what the runtime needs (TLS roots, time zones, curl for the healthcheck),
# then a fixed, unprivileged user with no login shell and no home directory.
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && apt-get install -y --no-install-recommends ca-certificates tzdata curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 cloudforge \
 && useradd --system --uid 10001 --gid cloudforge --no-create-home --shell /usr/sbin/nologin cloudforge

ENV JAVA_HOME=/opt/java/runtime
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre /opt/java/runtime ${JAVA_HOME}

WORKDIR /app

# Least-changing layers first, so a code change only re-pushes the small "application" layer.
# Files stay root-owned and read-only for the app user: the process cannot modify its own code.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# JVM tuning for containers (Java 21 reads cgroup limits automatically):
#   MaxRAMPercentage       - heap sized from the container memory limit, leaving room for metaspace,
#                            threads and direct buffers
#   ExitOnOutOfMemoryError - crash fast on OOM so the orchestrator restarts a clean instance
#   java.security.egd      - non-blocking entropy for faster startup
# Override or extend at runtime with -e JAVA_TOOL_OPTIONS="..." (for example to change the heap share).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    LANG=C.UTF-8 \
    TZ=UTC \
    SERVER_PORT=8080

USER 10001:10001

EXPOSE 8080

# Liveness probe for plain Docker. Kubernetes ignores this; use /actuator/health/liveness and
# /actuator/health/readiness as the pod's livenessProbe and readinessProbe instead.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

# Exec form: java is PID 1 and receives SIGTERM directly, so the graceful shutdown configured in
# application.yaml (server.shutdown=graceful, 30s per phase) drains requests before exit.
# application.jar here is the thin jar from the extract step: app code plus a classpath pointing at lib/.
ENTRYPOINT ["java", "-jar", "application.jar"]
