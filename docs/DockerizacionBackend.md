# Dockerización del backend

> **Stack:** Java 21 · Spring Boot 3.3 · Maven · MongoDB 7 · puerto de la app: `8090` (prod/Docker), `8081` (dev sin Docker, ver `application-dev.yaml`).

Este documento describe el Docker **real** del repo hoy: imagen runtime-only construida a partir
de un WAR ya compilado, y un `docker-compose.dev.yml` que solo levanta MongoDB para desarrollo
local (la app corre fuera de Docker, vía `./mvnw spring-boot:run` o desde el IDE). El pipeline de
CI/CD que construye y despliega esta misma imagen a producción está documentado en
[docs/GoogleCloudDeploy.md](GoogleCloudDeploy.md); problemas ya resueltos durante la dockerización
están en [BITACORA.md](../BITACORA.md).

## `Dockerfile` — runtime-only

El WAR se compila **fuera** de la imagen (en local con `mvn package -P prod`, o en CI con
`./mvnw clean verify -P prod`), y el `Dockerfile` solo lo empaqueta:

```dockerfile
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S mapit && adduser -S mapit -G mapit   # usuario sin privilegios

WORKDIR /app
ARG WAR_FILE=target/mapIt-0.0.1-SNAPSHOT.war
COPY ${WAR_FILE} app.war
RUN chown mapit:mapit app.war
USER mapit

EXPOSE 8090
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.war"]
```

Por qué runtime-only y no multi-stage con `COPY src`: el WAR ya viene compilado y **testeado**
por el paso de CI anterior (`./mvnw clean verify`) — recompilar dentro de Docker duplicaría ese
trabajo sin aportar nada, y alargaría cada build en CI. La imagen resultante solo contiene JRE +
WAR, sin JDK ni Maven ni código fuente.

- `-XX:+UseContainerSupport` / `-XX:MaxRAMPercentage=75.0`: la JVM respeta los límites de
  memoria del contenedor en vez de los del host, y deja margen (25%) para overhead fuera del heap.
- Usuario `mapit` sin privilegios: si el proceso se viera comprometido, no tendría acceso root al
  contenedor.

```bash
# Build local de la imagen (requiere el WAR ya compilado en target/)
mvn package -P prod -DskipTests
docker build --build-arg WAR_FILE=target/mapIt-0.0.1-SNAPSHOT.war -t mapit-backend .
docker run --rm -p 8090:8090 --env-file .env.prod mapit-backend
```

## `docker-compose.dev.yml` — solo MongoDB

Para desarrollo local no se dockeriza la app (arranca más rápido desde el IDE con hot reload);
solo se levanta MongoDB:

```bash
docker compose -f docker-compose.dev.yml up -d     # arrancar
docker compose -f docker-compose.dev.yml down      # parar
docker compose -f docker-compose.dev.yml down -v   # parar + borrar datos (reset completo)
```

Expone `27017` al host (a diferencia de un compose de producción, aquí sí conviene para poder
inspeccionar la base con un cliente Mongo local) y persiste los datos en el volumen
`mongodb_dev_data`. El healthcheck (`db.adminCommand('ping')`) evita conectarse antes de que Mongo
esté realmente listo.

## `.dockerignore`

Evita mandar al build context de Docker lo que no debe entrar en la imagen ni exponerse:

```
target/
.git/
.github/
*.md
.env
.env.*
scripts/
```

## Variables de entorno

En local, `application-dev.yaml` trae defaults seguros (`MAPIT_JWT_SECRET`,
`MONGODB_URI=mongodb://localhost:27017/mapit_db`, etc.) para poder arrancar sin configurar nada.
En Docker/producción se inyectan por entorno — ver `.env.example` para la lista completa y
[docs/GoogleCloudDeploy.md](GoogleCloudDeploy.md) para cómo se inyectan en Cloud Run vía Secret
Manager.
