
# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 1. Copiar solo el descriptor de dependencias primero → caching de capas
#    Si pom.xml no cambia, Maven no re-descarga las dependencias.
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw.cmd ./

RUN ./mvnw dependency:go-offline -B

# 2. Copiar el código fuente y compilar
COPY src ./src

RUN ./mvnw clean package -P preprod -DskipTests -B

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Buena práctica de seguridad: no ejecutar como root
RUN addgroup -S mapit && adduser -S mapit -G mapit

WORKDIR /app

# Copiar solo el JAR final desde el stage builder
COPY --from=builder /build/target/mapIt-0.0.1-SNAPSHOT.war app.war

# Cambiar propietario del JAR al usuario no-root
RUN chown mapit:mapit app.war

USER mapit

EXPOSE 8090

# Configuración JVM optimizada para contenedores:
#   -XX:+UseContainerSupport      → respeta los límites de memoria del contenedor
#   -XX:MaxRAMPercentage=75.0     → usa máximo el 75% de la RAM asignada
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.war"]