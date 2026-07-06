# Production image: React PWA built and baked into the Spring Boot jar,
# served from one process. Used as-is by Railway (builder: DOCKERFILE).
#
# BASE lets restricted environments point at a Docker Hub mirror, e.g.
#   docker build --build-arg BASE=mirror.gcr.io/library/ .
# Leave it empty (the default) for Docker Hub.
ARG BASE=

# --- Stage 1: frontend build ---
FROM ${BASE}node:22-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Stage 2: backend build (frontend dist becomes static resources) ---
FROM ${BASE}eclipse-temurin:21-jdk AS backend
WORKDIR /app
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN ./mvnw -B -q dependency:go-offline || true
COPY backend/src src
COPY --from=frontend /app/dist src/main/resources/static
RUN ./mvnw -B -DskipTests package

# --- Stage 3: runtime ---
FROM ${BASE}eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/predictor-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
