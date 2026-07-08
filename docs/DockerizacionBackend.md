# MapIt Backend – Pasos de Dockerización

> **Stack:** Java 21 · Spring Boot 3.3.12 · Maven · MongoDB 7  
> **Puerto de la app:** `8090`

---

## ¿Por qué dockerizar?

Dockerizar el backend permite que la aplicación se ejecute de forma idéntica en cualquier entorno (local, CI/CD, Azure) sin depender del sistema operativo, versión de Java o configuración de la máquina host. Además, agrupa el backend y la base de datos en servicios aislados que se arrancan con un único comando.

---

## Paso 0 — Fix en `pom.xml` (previo obligatorio)

### ¿Qué es `pom.xml`?

Es el descriptor del proyecto Maven. Define las dependencias, plugins y configuración de compilación. Sin él, Maven no sabe qué librerías descargar ni cómo construir el JAR.

### El problema

`spring-boot-starter-tomcat` estaba declarado con `scope: provided`, lo que le indica a Maven: *"este JAR lo proporcionará el servidor externo, no lo incluyas en el empaquetado"*. Eso tiene sentido cuando se despliega en un servidor de aplicaciones tradicional (Tomcat externo), pero en Docker ejecutamos `java -jar` directamente, así que el Tomcat embebido **debe estar dentro del JAR**.

**Solución:** eliminar ese bloque del `pom.xml`:

```xml
<!-- ELIMINAR -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-tomcat</artifactId>
    <scope>provided</scope>
</dependency>
```

Con `spring-boot-starter-web` ya presente, Tomcat se incluye como dependencia transitiva con `scope: compile` por defecto.

Verificación local antes de continuar:

```bash
./mvnw clean package -DskipTests
java -jar target/mapIt-0.0.1-SNAPSHOT.jar
# Debe arrancar en el puerto 8090
```

---

## Paso 1 — `.dockerignore`

### ¿Qué es y para qué sirve?

Es el equivalente a `.gitignore` pero para Docker. Cuando Docker construye una imagen, envía todo el contenido del directorio al daemon (el "build context"). Sin este fichero, enviaría también `target/` (cientos de MBs de clases compiladas), `.git/`, credenciales del `.env`, etc.

**Beneficios:**
- Imágenes más pequeñas y builds más rápidos.
- Evita filtrar secretos o ficheros sensibles dentro de la imagen.
- El caché de capas de Docker funciona mejor (menos contenido que invalidarlo).

```
target/
.git/
.github/
*.md
.env
.env.*
scripts/
```

---

## Paso 2 — `Dockerfile` (multi-stage)

### ¿Qué es un Dockerfile?

Es el script que describe cómo construir la imagen Docker paso a paso: qué imagen base usar, qué ficheros copiar, qué comandos ejecutar y cómo arrancar la aplicación.

### ¿Por qué multi-stage?

Un build en una sola fase produciría una imagen con el JDK completo + Maven + código fuente + JAR compilado, lo que resulta en imágenes de ~500 MB o más. Con multi-stage se usan **dos fases separadas**:

| Stage | Imagen base | Contenido | Se incluye en la imagen final |
|---|---|---|---|
| `builder` | `maven:3.9.9-eclipse-temurin-21-alpine` | JDK + Maven + código fuente | ❌ No |
| `runtime` | `eclipse-temurin:21-jre-alpine` | Solo JRE + el JAR compilado | ✅ Sí |

La imagen final pesa ~120 MB en lugar de ~500 MB y **no expone el código fuente ni Maven**.

### Optimización de caché de capas

El `pom.xml` se copia **antes** que el código fuente. Docker cachea cada capa: si `pom.xml` no cambia entre builds, Maven no re-descarga las dependencias (que son los archivos más pesados). Solo se re-ejecuta la descarga cuando cambia el fichero de dependencias.

### Seguridad: usuario no-root

Se crea un usuario del sistema `mapit` sin privilegios. Si la aplicación fuera comprometida, el atacante no tendría acceso de root al contenedor ni al host.

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw.cmd ./
RUN ./mvnw dependency:go-offline -B   # descarga dependencias (cacheado)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S mapit && adduser -S mapit -G mapit   # usuario sin privilegios
WORKDIR /app
COPY --from=builder /build/target/mapIt-0.0.1-SNAPSHOT.jar app.jar
RUN chown mapit:mapit app.jar
USER mapit
EXPOSE 8090
# Flags JVM optimizados para contenedores:
# -XX:+UseContainerSupport  → lee los límites de CPU/RAM del contenedor, no del host
# -XX:MaxRAMPercentage=75.0 → usa máximo el 75% de la RAM asignada al contenedor
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

## Paso 3 — `.env`

### ¿Qué es y para qué sirve?

Es un fichero de variables de entorno locales. `docker compose` lo lee automáticamente y sustituye las referencias `${VARIABLE}` en el `docker-compose.yml`. Centraliza todos los secretos y configuraciones en un único lugar.

**Regla de oro:** este fichero **nunca va al repositorio**. Contiene credenciales reales. Se añade a `.gitignore` y se proporciona un `.env.example` con valores de ejemplo para el equipo.

```dotenv
# Base de datos
MONGO_ROOT_USER=mapit_admin
MONGO_ROOT_PASSWORD=cambia_esta_contraseña_segura
MONGODB_DATABASE=mapit_db

# Seguridad JWT — cambiar siempre en producción
JWT_SECRET=cambia_este_secret_jwt_por_uno_largo_y_aleatorio
JWT_EXPIRATION=86400   # segundos (24h)

# Perfil de Spring
SPRING_PROFILES_ACTIVE=prod
```

---

## Paso 4 — `docker-compose.yml`

### ¿Qué es Docker Compose?

Es una herramienta que permite definir y orquestar múltiples contenedores como si fueran un único servicio. En lugar de ejecutar comandos `docker run` largos y manualmente, se describe la arquitectura completa en un fichero YAML y se levanta todo con `docker compose up`.

### Servicio `mongodb`

- Usa la imagen oficial `mongo:7.0` sin necesidad de construir nada.
- **Volumen `mongodb_data`:** los datos de MongoDB se persisten en un volumen gestionado por Docker. Sin esto, cada vez que se elimina el contenedor se pierden todos los datos.
- **Red interna `mapit-net`:** MongoDB no expone su puerto al host, solo es accesible desde otros contenedores de la misma red (el backend). Esto es una medida de seguridad: la base de datos no es accesible desde fuera.
- **Healthcheck:** Docker comprueba periódicamente si Mongo está listo ejecutando `db.adminCommand('ping')`. Solo cuando pasa esta comprobación se permite que el backend arranque.

### Servicio `app`

- **`build.target: runtime`:** construye la imagen usando solo el stage `runtime` del Dockerfile multi-stage, descartando el stage `builder`.
- **`depends_on: condition: service_healthy`:** el backend no arranca hasta que MongoDB haya pasado el healthcheck. Evita el error de conexión por race condition (la app arranca antes que Mongo).
- **`restart: unless-stopped`:** si el contenedor cae por error, Docker lo reinicia automáticamente.
- Las credenciales se inyectan como variables de entorno desde el `.env`, no están hardcodeadas.

```yaml
services:

  mongodb:
    image: mongo:7.0
    container_name: mapit-mongodb
    restart: unless-stopped
    environment:
      MONGO_INITDB_ROOT_USERNAME: ${MONGO_ROOT_USER}
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_ROOT_PASSWORD}
      MONGO_INITDB_DATABASE: ${MONGODB_DATABASE}
    volumes:
      - mongodb_data:/data/db
    networks:
      - mapit-net
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')", "--quiet"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s

  app:
    build:
      context: .
      dockerfile: Dockerfile
      target: runtime
    container_name: mapit-backend
    restart: unless-stopped
    ports:
      - "8090:8090"
    environment:
      MONGODB_URI: "mongodb://${MONGO_ROOT_USER}:${MONGO_ROOT_PASSWORD}@mongodb:27017/${MONGODB_DATABASE}?authSource=admin"
      MONGODB_DATABASE: ${MONGODB_DATABASE}
      MAPIT_JWT_SECRET: ${JWT_SECRET}
      MAPIT_JWT_EXPIRATION: ${JWT_EXPIRATION:-86400}
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
    depends_on:
      mongodb:
        condition: service_healthy
    networks:
      - mapit-net

volumes:
  mongodb_data:
    driver: local

networks:
  mapit-net:
    driver: bridge
```

---

## Paso 5 — `application.yaml`

### ¿Por qué modificarlo?

Spring Boot necesita saber de dónde leer la clave secreta del JWT y su tiempo de expiración. La sintaxis `${VARIABLE:valor-por-defecto}` permite que:

- En **Docker/producción**: lea el valor real inyectado por Docker Compose.
- En **local sin Docker**: use el valor por defecto definido en el propio YAML, sin necesidad de configurar nada extra.

```yaml
mapit:
  jwt:
    secret: ${MAPIT_JWT_SECRET:mapit-dev-secret-change-me}
    expiration-seconds: ${MAPIT_JWT_EXPIRATION:86400}
```

---

## Paso 6 — Comandos de uso

```bash
# Construir y levantar en background
docker compose up --build -d

# Ver logs de la app
docker compose logs -f app

# Estado de los contenedores
docker compose ps

# Parar (conserva datos)
docker compose down

# Parar y borrar volúmenes (resetea la BBDD)
docker compose down -v
```

---

## Estructura de ficheros resultante

```
BACK/
├── .dockerignore
├── .env                        ← en .gitignore
├── .env.example
├── docker-compose.yml
├── Dockerfile
├── pom.xml                     ← eliminado tomcat provided
└── src/main/resources/
    └── application.yaml        ← JWT desde env vars
```

---

## Problemas encontrados y soluciones

| Síntoma | Causa | Solución |
|---|---|---|
| `Unable to access jarfile app.jar` | Fat JAR no generado | Quitar `tomcat` con `scope:provided` del `pom.xml` |
| App no conecta a MongoDB | URI incorrecta o Mongo no healthy | Comprobar `docker compose ps` y el healthcheck |
| `Authentication failed` en Mongo | Credenciales del `.env` no coinciden | `docker compose down -v` y recrear el volumen |
| Imagen demasiado pesada | Usando stage `builder` completo | Verificar `target: runtime` en el compose |
