
# ─── Runtime only ─────────────────────────────────────────────────────────────
# El WAR ya viene compilado y testeado por el CI (mvnw clean verify).
# Este Dockerfile solo empaqueta el artefacto; no recompila con Maven.
FROM eclipse-temurin:21-jre-alpine

# Buena práctica de seguridad: no ejecutar como root
RUN addgroup -S mapit && adduser -S mapit -G mapit

WORKDIR /app

# El CI pasa el WAR pre-construido mediante --build-arg o contexto de build
ARG WAR_FILE=target/mapIt-0.0.1-SNAPSHOT.war
COPY ${WAR_FILE} app.war

# Cambiar propietario del WAR al usuario no-root
RUN chown mapit:mapit app.war

USER mapit

EXPOSE 8090

# Configuración JVM optimizada para contenedores:
#   -XX:+UseContainerSupport      → respeta los límites de memoria del contenedor
#   -XX:MaxRAMPercentage=75.0     → usa máximo el 75% de la RAM asignada
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.war"]