# One image with everything: the built frontend and the Spring Boot API that serves it.
#   docker compose up -d --build     (see docs/HOSTING.md)

# ---- 1. frontend
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- 2. backend
FROM eclipse-temurin:21-jdk AS api
WORKDIR /api
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
# The wrapper may have Windows line endings when the repo was checked out on Windows.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src src
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar app.jar

# ---- 3. what runs
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app crm
WORKDIR /app
COPY --from=api /api/app.jar app.jar
COPY --from=web /web/dist frontend
# Production defaults: no test login, no example data.
ENV FRONTEND_DIST=/app/frontend \
    FRONTEND_AUTOSTART=false \
    TEST_ACCOUNT=false \
    DEMO_DATA=false \
    FORWARD_HEADERS=native \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
USER crm
EXPOSE 8082
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
