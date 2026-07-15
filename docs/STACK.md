# Stack y servicios — Backend (MapIt API)

> Documento vivo. Actualizado cada vez que cambie una pieza real del stack. La contraparte de este documento en el frontend es
> [`WEB/docs/STACK.md`](https://github.com/EMC2-MapItApp/web/blob/main_web/docs/STACK.md); juntos
> cubren el sistema completo. Este mismo contenido se reutiliza (resumido) en la página
> [`/stack`](https://mapit-web.com/stack) del frontend.

## Por qué este stack

El objetivo de MapIt como proyecto es tener una aplicación **real, mínima y desplegada** — no un
ejercicio de arquitectura sobre el papel. Cada elección de tecnología prioriza:

1. Gratis o casi gratis para un proyecto personal sin tráfico de producción real.
2. Estándar de la industria — lo que se usaría en un equipo profesional, no un atajo de juguete.
3. Poco tiempo de mantenimiento operativo (managed services antes que self-hosting).

## Lenguaje y framework

| Pieza | Versión | Por qué |
|---|---|---|
| [Java](https://openjdk.org/) | 21 (LTS) | Última LTS disponible al empezar el proyecto; virtual threads y pattern matching disponibles aunque aún no explotados a fondo. |
| [Spring Boot](https://spring.io/projects/spring-boot) | 3.3 | Framework de referencia en backend Java; ecosistema de Spring Security/Data/Validation cubre auth, persistencia y validación sin piezas sueltas. |
| [Maven](https://maven.apache.org/) | vía `mvnw` (wrapper) | Build reproducible sin depender de una instalación local de Maven — el wrapper fija la versión exacta. |

Ver `pom.xml` para la lista completa de dependencias y los perfiles `dev`/`prod`.

## Persistencia

| Pieza | Por qué |
|---|---|
| [MongoDB Atlas](https://www.mongodb.com/atlas) | Free tier gestionado (M0) — sin servidor propio que mantener/parchear. El modelo de dominio (publicaciones geolocalizadas, árbol de categorías anidado) encaja mejor con documentos que con un esquema relacional estricto. |
| [Spring Data MongoDB](https://spring.io/projects/spring-data-mongodb) | Repositorios declarativos (`MongoRepository`) sin capa de acceso a datos manual. |

El proyecto migró desde un diseño inicial en PostgreSQL/PostGIS — el requisito de geoconsultas
complejas de PostGIS no llegó a ser necesario para el alcance actual, y Atlas simplificó el
despliegue.

## Autenticación

| Pieza | Por qué |
|---|---|
| JWT propio (HMAC), `JwtService` | Implementado a mano en vez de una librería (`jjwt`, etc.) como ejercicio deliberado de entender el formato JWT (header.payload.firma) y HMAC-SHA a bajo nivel, no por rechazo a las librerías estándar. |
| [Spring Security](https://spring.io/projects/spring-security) | `JwtAuthFilter` (`OncePerRequestFilter`) rellena el `SecurityContextHolder`; las reglas de ruta pública/protegida viven en `SecurityConfig`. Sesiones stateless, CSRF deshabilitado — es una API de tokens pura. |
| [zxcvbn](https://github.com/nulab/zxcvbn4j) (puerto Java) | Validación de fortaleza de contraseña con la misma escala 0-4 que `@zxcvbn-ts` en el frontend, para que el feedback de fuerza sea consistente en ambos lados. |

## Testing

| Pieza | Por qué |
|---|---|
| [JUnit 5](https://junit.org/junit5/) | Estándar de facto en el ecosistema Java/Spring. |
| [Mockito](https://site.mockito.org/) | Aislar servicios de sus dependencias sin levantar contexto de Spring en tests unitarios puros. |
| [AssertJ](https://assertj.github.io/doc/) | Aserciones fluidas, más legibles que JUnit puro. |
| [MockMvc](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html) | Tests de capa web (`@WebMvcTest`) sin arrancar un servidor real. |

Desglose completo por clase de test: [`docs/tests.md`](tests.md).

## Contenedor y CI/CD

| Pieza | Por qué |
|---|---|
| [Docker](https://www.docker.com/) | Imagen runtime-only (`eclipse-temurin:21-jre-alpine`) — el WAR se compila en CI, el Dockerfile solo lo empaqueta. Usuario no-root dentro del contenedor. |
| [GitHub Actions](https://github.com/features/actions) | CI/CD sin infraestructura propia que mantener; runner gratuito de GitHub para repos públicos/privados dentro de cuota. Pipeline completo en [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml). |
| [Google Artifact Registry](https://cloud.google.com/artifact-registry) | Registro de imágenes Docker privado, integrado de forma nativa con Cloud Run (sin credenciales adicionales más allá de la Service Account). |

## Hosting y servicios cloud

| Servicio | Rol | Por qué |
|---|---|---|
| [Google Cloud Run](https://cloud.google.com/run) | Hosting del backend (`europe-southwest1`) | Serverless: escala a cero cuando no hay tráfico (coste ~0 en un proyecto personal), sin gestionar VMs ni orquestador. Despliegue por imagen Docker, no por buildpack, para tener control total del runtime. |
| [Google Secret Manager](https://cloud.google.com/secret-manager) | JWT secret, URI de Mongo, credenciales SMTP | Los secretos nunca viven en YAML del repo ni en la consola de Cloud Run sin rastro — se referencian por nombre desde `deploy.yml`, así queda versionado qué secreto usa qué variable. |
| [Resend](https://resend.com/) (SMTP) | Envío del correo de verificación de cuenta | Free tier suficiente para volumen de proyecto personal; API SMTP estándar, sin acoplarse a un SDK propietario. |
| [ip-api.com](https://ip-api.com/) | Geolocalización aproximada por IP (fallback de ubicación) | Servicio gratuito sin API key para el caso de uso — usuario sin ubicación del navegador todavía recibe un centro de mapa razonable. |

## Control de versiones

| Pieza | Por qué |
|---|---|
| [Git](https://git-scm.com/) + [GitHub](https://github.com/) | Repo: [`EMC2-MapItApp/back`](https://github.com/EMC2-MapItApp/back), rama de despliegue `main_back`. GitHub Actions como CI/CD nativo del mismo sitio donde vive el código, sin sincronizar un sistema externo. |

## Lo que deliberadamente NO se usó (y por qué)

- **Librería JWT externa** (`jjwt`, `nimbus-jose-jwt`...): se implementó `JwtService` a mano como
  ejercicio de aprendizaje. En un contexto de equipo/producción real, una librería mantenida sería
  la opción por defecto.
- **MapStruct**: el mapeo entidad↔DTO es manual (`mapper/`). Con el tamaño actual del dominio no
  compensa aún la curva de aprendizaje/generación de código de MapStruct.
- **Kubernetes**: Cloud Run cubre las necesidades de escalado de un proyecto de portfolio sin la
  sobrecarga operativa de gestionar un clúster.
