# Single-origin image (ADR 0002): one container serves the built SPA and the API
# from the same origin on port 8080. Build from the repository root:
#
#   docker build -f deploy/Dockerfile -t tichu-turnier .
#
# Run it (see deploy/README.md for the full walkthrough):
#   docker run -p 8080:8080 -e SPRING_DATASOURCE_URL=... tichu-turnier

# Stage 1: build the SPA bundle; VITE_API_URL=/ makes it talk to its own origin.
FROM node:22.23.4-alpine AS frontend
WORKDIR /fe
RUN corepack enable
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/tsconfig.base.json ./
COPY frontend/apps ./apps
COPY frontend/packages ./packages
RUN pnpm install --frozen-lockfile
ENV VITE_API_URL=/
RUN pnpm --filter @tichu/web build

# Stage 2: backend boot jar with the SPA dist on the classpath.
FROM gradle:9-jdk21 AS backend
WORKDIR /app
COPY backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/src ./src
COPY --from=frontend /fe/apps/web/dist ./src/main/resources/static
RUN gradle bootJar --no-daemon

# Stage 3: JRE runtime — the boot jar plus nothing else.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /app/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
