# CLAUDE.md

Este archivo da contexto a Claude Code (claude.ai/code) al trabajar con el código de este repositorio.

> **Documento vivo**: el proyecto está en desarrollo activo y varias piezas descritas aquí están
> parcialmente implementadas. Actualiza este archivo cuando el estado real del código cambie —no
> lo trates como una foto fija.

## Proyecto

MapIt API — backend en Spring Boot 3.3 / Java 21 para una plataforma de eventos locales con
gamificación. El modelo de dominio contempla tres tipos de usuario (individual, profesional,
entidad) que interactuarían mediante "publicaciones" geolocalizadas asociadas a "lugares"
(places), pero **de momento solo el tipo individual está realmente implementado** — el sistema de
niveles/XP/capacidades y los tipos profesional/entidad están en progreso, no los des por
completos ni cerrados.

La persistencia es **MongoDB** (Spring Data MongoDB) — el diseño original contemplaba
PostgreSQL/PostGIS, pero el código migró a MongoDB Atlas pronto en el desarrollo (ver
[BITACORA.md](BITACORA.md) para el porqué). No queda documento de requisitos relacional en el
repo; confía en el código/paquete `entity` como fuente de verdad del modelo de dominio.

El frontend Angular hermano vive en el directorio `WEB/` adyacente. Despliegue: este backend en
**Google Cloud Run**, el frontend en **Cloudflare** (proyectos independientes, sin relación con
despliegue "Angular" tradicional).

## Comandos

```bash
# Ejecutar en local (perfil dev, puerto 8081, necesita MongoDB accesible vía MONGODB_URI o localhost:27017)
./mvnw spring-boot:run

# Ejecutar todos los tests
./mvnw test

# Ejecutar clases de test específicas
./mvnw test -Dtest="HashServiceTest,JwtServiceTest,AuthServiceTest,PasswordResetServiceTest"
./mvnw test -Dtest="AuthControllerTest"

# Verificación completa (lo que corre CI antes de construir el artefacto de deploy)
./mvnw clean verify

# Empaquetar como WAR
mvn package -P dev    # WAR para Tomcat externo (Tomcat marcado como provided)
mvn package -P prod   # JAR-en-WAR autocontenido para Docker/Cloud Run (perfil por defecto en CI)
```

No hay linter configurado. Stack de tests: JUnit 5, Mockito, AssertJ, MockMvc (`docs/tests.md`
tiene el desglose completo de la cobertura existente por clase — actualízalo al añadir grupos de
tests relevantes).

La documentación de despliegue vive en `docs/` (`DockerizacionBackend.md`, `GoogleCloudDeploy.md`)
— el destino real de producción es **Google Cloud Run**; una exploración previa con Azure se
descartó (ver [BITACORA.md](BITACORA.md)). Revisa `.github/workflows/deploy.yml` para confirmar
el pipeline activo antes de confiar en cualquier doc.

## Arquitectura

Estructura por capas estándar bajo `src/main/java/emc/mapIt/`:

- `controller/` — endpoints REST, `/api/v1/...` (finos; delegan en los servicios)
- `service/` — lógica de negocio (`AuthService`, `UserService`, `PublicationService`,
  `CategoryCrudService`, `HashService`, `JwtService`)
- `repository/` — repositorios de Spring Data MongoDB, uno por entidad
- `entity/` — documentos de MongoDB (`User`, `Place`, `Publication`, `MainCategory`,
  `SubCategory`, `CapabilityDefinition`, `LevelDefinition`, `MilestoneDefinition`, `LocationType`, etc.)
- `dto/` — payloads de request/response, separados de las entidades
- `mapper/` — mapeo manual entidad↔DTO (sin MapStruct)
- `config/` — configuración de Spring + seeders de arranque
- `exception/` — `ApiException` (errores funcionales/de dominio) + `GlobalExceptionHandler`
  (`@RestControllerAdvice`) que traduce excepciones a un JSON de error consistente
  `{"error": {code, message, status}}`

Dos módulos aparte, primer piloto de una organización por dominio en lugar de por capa técnica
(ver `docs/ARQUITECTURA.md`), montados como arquitectura hexagonal (puertos y adaptadores):

- `geo/` — `GeoIpController`, `GeoIpService` (caso de uso), puerto `GeoLocationProvider` y su
  adaptador `IpApiGeoLocationProvider` (ip-api.com), modelo de dominio `GeoLocation`,
  `GeoIpResponse` (DTO de API)
- `notifications/` — puerto `NotificationSender` y su adaptador `EmailNotificationSender`
  (SMTP vía `JavaMailSender`); lo consumen `EmailVerificationService` y `PasswordResetService`
  desde `service/`, que no conocen el canal de entrega concreto

**Auth**: JWT propio basado en HMAC (sin librería externa de JWT) vía `JwtService`, aplicado en
cada request por `JwtAuthFilter` (un `OncePerRequestFilter` que lee la cabecera
`Authorization: Bearer`, rellena el `SecurityContextHolder`, y deja que las reglas por ruta de
`SecurityConfig` decidan el acceso — no rechaza por sí mismo tokens ausentes/inválidos en rutas
públicas). Las rutas públicas vs. protegidas se declaran en `SecurityConfig`
(`GET /api/v1/categories/**`, `GET /api/v1/publications`, `GET /api/v1/users/**`,
`GET /api/v1/geo/**`, y register/login/logout/verify-email/resend-verification/
forgot-password/reset-password de auth son abiertas; el resto requiere token válido). CSRF
está deshabilitado y las sesiones son stateless — es una API de tokens pura, sin cookies.

El restablecimiento de contraseña ("olvidé mi contraseña") sigue el mismo patrón que la
verificación de email: `PasswordResetToken` es una colección hermana de
`EmailVerificationToken` (token de un solo uso, hash SHA-256, TTL vía índice Mongo), gestionada
por `PasswordResetService`. A diferencia de `resend-verification`, `forgot-password` sí revela
si el email existe (404 si no) — decisión de producto deliberada, ya que `register` ya lo revela
vía 409 CONFLICT y no hay anti-enumeración real que proteger aquí.

**Seeders** (`config/*Seeder.java`, p.ej. `AdminUserSeeder`, `CategorySeeder`) pueblan datos de
referencia (categorías, un usuario admin) al arrancar — revísalos antes de asumir que un MongoDB
recién creado está vacío.

**Perfiles de configuración**: `application.yaml` tiene los valores por defecto independientes de
entorno (Jackson, defaults del proveedor de geo); `application-dev.yaml` /
`application-prod.yaml` tienen la URI de Mongo, el secreto JWT y el puerto por entorno, activados
vía los perfiles Maven `dev`/`prod` (`spring.profiles.active`). Nunca hardcodear secretos — todo
lo específico de entorno se lee de variables de entorno con defaults solo-para-dev (p.ej.
`MAPIT_JWT_SECRET`, `MONGODB_URI`).

**Manejo de errores**: lanza `ApiException` (lleva un `HttpStatus`, un `code` legible por máquina
y un mensaje) desde los servicios para errores de dominio; `GlobalExceptionHandler` la convierte,
junto con fallos de Bean Validation y bodies de request no legibles, al `ErrorResponse` común. Los
controllers no deben construir respuestas de error manualmente.

## Convenciones de código

- Aplicar principios **SOLID** y buenas prácticas al diseñar/tocar clases (responsabilidad única
  por servicio/clase, dependencias inyectadas por constructor, evitar acoplar controllers a
  detalles de persistencia, etc.).
- Comentar el código cuando aporte contexto no evidente (decisiones no obvias, workarounds,
  invariantes) — no comentar lo que el nombre del método/clase ya deja claro.
- Logging: usar SLF4J (`emc.mapIt` a `DEBUG` en dev, `INFO`/`WARN` en prod — ver
  `application-dev.yaml` / `application-prod.yaml`). En dev es aceptable loguear con detalle para
  depurar; en producción **nunca** loguear datos sensibles (contraseñas, tokens JWT completos,
  emails/PII innecesarios) — usar el nivel adecuado y enmascarar/omitir esos campos.
- **Documentación de stack/arquitectura sincronizada**: `docs/STACK.md` (este repo) tiene su
  pareja en `../WEB/docs/STACK.md`, y ambos se resumen en
  `../WEB/src/app/features/info/stack/stack-page.data.ts` (página pública `/stack`).
  `docs/ARQUITECTURA.md` documenta las decisiones de arquitectura interna (monolito modular,
  hexagonal en `geo/`/`notifications/`, criterios para pasar a microservicios). Al añadir,
  cambiar o justificar una decisión de stack o arquitectura, actualizar los tres archivos (o los
  que apliquen) en el mismo cambio — no dejarlos desincronizados.
