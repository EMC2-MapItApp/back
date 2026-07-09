# Dockerización del Backend MapIt

## Stack
- Java 21, Spring Boot 3.3.12, Maven
- Base de datos: MongoDB 7
- Puerto de la app: 8090

---

## Principios aplicados

| Principio SOLID | Aplicación en Docker |
|---|---|
| **S** — Single Responsibility | Cada contenedor tiene una única responsabilidad (app / bbdd) |
| **O** — Open/Closed | La imagen no se modifica para cambiar entorno; se extiende vía variables de entorno |
| **L** — Liskov | MongoDB puede sustituirse por Atlas sin tocar el código; solo cambia `MONGODB_URI` |
| **I** — Interface Segregation | `docker-compose.yml` base + `docker-compose.override.yml` para dev, no se mezclan responsabilidades |
| **D** — Dependency Inversion | La app depende de abstracciones (variables de entorno), no de rutas o credenciales hardcodeadas |

---

## Paso 0 — Ajuste previo en pom.xml (OBLIGATORIO)

El `pom.xml` declara `spring-boot-starter-tomcat` con `<scope>provided</scope>`. Esto hace que el Tomcat embebido **no** se incluya en el fat JAR, y `java -jar` falla en Docker.

**Solución:** eliminar ese bloque del `pom.xml`. El starter-web ya incluye Tomcat de forma transitiva con scope `compile`.

```xml
<!-- ELIMINAR este bloque de pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-tomcat</artifactId>
    <scope>provided</scope>
</dependency>
```

Verificar localmente antes de continuar:
```bash
./mvnw clean package -DskipTests
java -jar target/mapIt-0.0.1-SNAPSHOT.jar
# Debe arrancar en el puerto 8090
```

---

## Paso 1 — .dockerignore

Evita copiar ficheros innecesarios a la imagen. Crea `.dockerignore` en la raíz de `BACK/`:

```
target/
.git/
.github/
.mvn/wrapper/maven-wrapper.jar
*.md
.env
.env.*
.gitignore
.gitattributes
scripts/
```

---

## Paso 2 — Dockerfile (multi-stage)

El multi-stage build aplica **Single Responsibility**: el stage `builder` solo compila; el stage `runtime` solo ejecuta. La imagen final **no contiene el JDK ni Maven**, solo el JRE mínimo.

Crea `Dockerfile` en la raíz de `BACK/`:

```dockerfile
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

RUN ./mvnw clean package -DskipTests -B

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Buena práctica de seguridad: no ejecutar como root
RUN addgroup -S mapit && adduser -S mapit -G mapit

WORKDIR /app

# Copiar solo el JAR final desde el stage builder
COPY --from=builder /build/target/mapIt-0.0.1-SNAPSHOT.jar app.jar

# Cambiar propietario del JAR al usuario no-root
RUN chown mapit:mapit app.jar

USER mapit

EXPOSE 8090

# Configuración JVM optimizada para contenedores:
#   -XX:+UseContainerSupport      → respeta los límites de memoria del contenedor
#   -XX:MaxRAMPercentage=75.0     → usa máximo el 75% de la RAM asignada
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

---

## Paso 3 — Variables de entorno (.env)

El fichero `.env` **nunca se sube al repositorio**. Hay un `.env.example` como plantilla.

Crea `.env` en la raíz de `BACK/` (está en `.gitignore`):

```dotenv
# MongoDB
MONGO_ROOT_USER=mapit_admin
MONGO_ROOT_PASSWORD=cambia_esta_contraseña_segura
MONGODB_DATABASE=mapit_db

# JWT — CAMBIAR siempre en producción
JWT_SECRET=cambia_este_secret_jwt_por_uno_largo_y_aleatorio
JWT_EXPIRATION=86400

# Spring profile
SPRING_PROFILES_ACTIVE=prod
```

---

## Paso 4 — docker-compose.yml

Crea `docker-compose.yml` en la raíz de `BACK/`:

```yaml
services:

  # ── Base de datos ──────────────────────────────────────────────────────────
  mongodb:
    image: mongo:7.0
    container_name: mapit-mongodb
    restart: unless-stopped
    environment:
      MONGO_INITDB_ROOT_USERNAME: ${MONGO_ROOT_USER}
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_ROOT_PASSWORD}
      MONGO_INITDB_DATABASE: ${MONGODB_DATABASE}
    volumes:
      - mongodb_data:/data/db      # persistencia de datos
    networks:
      - mapit-net
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')", "--quiet"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s

  # ── Aplicación Spring Boot ─────────────────────────────────────────────────
  app:
    build:
      context: .
      dockerfile: Dockerfile
      target: runtime                # apunta al stage final del multi-stage
    container_name: mapit-backend
    restart: unless-stopped
    ports:
      - "8090:8090"
    environment:
      # La app se conecta al servicio 'mongodb' por nombre de red interna
      MONGODB_URI: "mongodb://${MONGO_ROOT_USER}:${MONGO_ROOT_PASSWORD}@mongodb:27017/${MONGODB_DATABASE}?authSource=admin"
      MONGODB_DATABASE: ${MONGODB_DATABASE}
      MAPIT_JWT_SECRET: ${JWT_SECRET}
      MAPIT_JWT_EXPIRATION: ${JWT_EXPIRATION:-86400}
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
    depends_on:
      mongodb:
        condition: service_healthy   # espera a que Mongo esté listo
    networks:
      - mapit-net

# ── Volúmenes y redes ──────────────────────────────────────────────────────────
volumes:
  mongodb_data:
    driver: local

networks:
  mapit-net:
    driver: bridge
```

---

## Paso 5 — Ajuste de application.yaml para leer las variables de entorno de Docker

Las variables inyectadas por Docker Compose deben coincidir con las que lee Spring Boot.  
El `application.yaml` ya usa `${MONGODB_URI}` y `${MONGODB_DATABASE}`. Solo hay que añadir las de JWT:

```yaml
# En src/main/resources/application.yaml, sección mapit.jwt:
mapit:
  jwt:
    secret: ${MAPIT_JWT_SECRET:mapit-dev-secret-change-me}
    expiration-seconds: ${MAPIT_JWT_EXPIRATION:86400}
```

---

## Paso 6 — Comandos de uso

### Construcción y arranque
```bash
# Desde d:\MapIt\BACK\

# 1. Construir imagen y levantar todos los servicios en background
docker compose up --build -d

# 2. Ver logs en tiempo real
docker compose logs -f app

# 3. Ver logs solo de MongoDB
docker compose logs -f mongodb
```

### Verificación
```bash
# Estado de los contenedores
docker compose ps

# Comprobar que la API responde
curl http://localhost:8090/api/auth/login -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test"}'
```

### Parada y limpieza
```bash
# Parar (conserva datos)
docker compose down

# Parar Y eliminar volúmenes (borra la BBDD)
docker compose down -v
```

---

## Paso 7 — (Opcional) docker-compose.override.yml para desarrollo local

Crea `docker-compose.override.yml` (no subir a git) para sobreescribir solo en local sin tocar el compose principal. Esto aplica **Open/Closed**:

```yaml
# docker-compose.override.yml  — solo para desarrollo local
services:
  app:
    environment:
      SPRING_PROFILES_ACTIVE: dev
      MAPIT_JWT_SECRET: dev-secret-no-seguro
    # Monta el JAR directamente sin reconstruir la imagen durante desarrollo rápido
    # (requiere haber ejecutado ./mvnw package previamente)

  mongodb:
    ports:
      - "27017:27017"    # exponer MongoDB al host solo en local para Compass/Mongosh
```

---

## Estructura final de ficheros Docker

```
BACK/
├── .dockerignore          ← (nuevo)
├── .env                   ← (nuevo, en .gitignore)
├── .env.example           ← (actualizar con las nuevas variables)
├── docker-compose.yml     ← (nuevo)
├── docker-compose.override.yml  ← (opcional, en .gitignore)
├── Dockerfile             ← (nuevo)
├── pom.xml                ← (modificado: quitar tomcat provided)
└── src/
    └── main/resources/
        └── application.yaml  ← (modificado: JWT desde env vars)
```

---

## Solución de problemas comunes

| Síntoma                                          | Causa                                      |Solución                                                                                 |
|--------------------------------------------------|--------------------------------------------|-----------------------------------------------------------------------------------------|
| `Error: Unable to access jarfile app.jar`        | El fat JAR no se generó bien               | Revisar el Paso 0; comprobar que `target/mapIt-0.0.1-SNAPSHOT.jar` existe y pesa >10 MB |
| App arranca pero no conecta a MongoDB            | URI incorrecta o Mongo no healthy          | Verificar `docker compose ps` y que MongoDB pasa el healthcheck                         |
| `Authentication failed` en Mongo                 | Credenciales del `.env` no coinciden       | Borrar el volumen (`docker compose down -v`) y recrearlo                                |
| Puerto 8090 ocupado                              | Otro proceso usa el puerto                 | Cambiar el mapeo en `docker-compose.yml` a `"8091:8090"`                                |
| Imagen muy pesada                                | Se está usando la stage `builder` completa | Verificar que `target: runtime` está en el compose                                      |