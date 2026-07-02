# syntax=docker/dockerfile:1

# 1) Build the SPA
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) Build the backend jar, bundling the SPA into Spring Boot's static resources (served same-origin)
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && (./mvnw -B -q -DskipTests dependency:go-offline || true)
COPY backend/src/ src/
COPY --from=frontend /app/frontend/dist/ src/main/resources/static/
RUN ./mvnw -B -q -DskipTests clean package

# 3) Runtime — slim JRE
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd -r -u 1001 appuser && mkdir -p /app/data && chown -R appuser /app
COPY --from=backend /app/target/prompt-arena-*.jar app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
