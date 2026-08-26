# Bitácora — problemas reales y cómo se resolvieron

> A diferencia de `docs/STACK.md` (qué se usa y por qué), este documento recoge **incidentes
> concretos** encontrados durante el desarrollo y despliegue del backend, con su causa raíz y la
> solución aplicada. Se actualiza cuando aparece un problema real — no es una lista de riesgos
> hipotéticos. La contraparte de este documento en el frontend es
> [`WEB/BITACORA.md`](https://github.com/EMC2-MapItApp/web/blob/main_web/BITACORA.md).

## Migración de PostgreSQL/PostGIS a MongoDB Atlas

**Contexto:** el diseño original de la API (documentado inicialmente como un esquema relacional
con PostGIS para consultas geoespaciales) se migró a MongoDB Atlas durante el desarrollo.

**Por qué:** el alcance real de las geoconsultas necesarias (filtrar publicaciones por proximidad
aproximada) no llegó a requerir las capacidades avanzadas de PostGIS, y MongoDB Atlas simplificó
el despliegue al no tener que gestionar ni parchear un servidor de base de datos relacional propio
— un factor decisivo en un proyecto personal sin equipo de infraestructura detrás. Se escribió un
script puntual de migración de datos (ya retirado del repo tras completarse) para el traspaso.

**Resultado:** el modelo de dominio (`entity/`) es hoy 100% documentos de MongoDB vía Spring Data
MongoDB; no queda código dependiente de JPA/Hibernate ni de PostGIS.

## Despliegue: exploración de Azure, descartada

**Contexto:** antes de asentarse en Google Cloud Run, se exploró desplegar en Azure — primero con
App Service + PostgreSQL Flexible Server, después con App Service (backend, Docker) + Azure
Container Instances (MongoDB) + Azure Container Registry.

**Por qué se descartó:** la combinación App Service + ACI requería mantener y pagar por recursos
corriendo de forma continua (ACI no escala a cero), y coordinar manualmente el registro de
imágenes, la identidad gestionada para acceso al ACR y el ciclo de vida de cada recurso por
separado. Para un proyecto personal sin tráfico constante, esa carga operativa y de coste no
compensaba frente a una alternativa serverless.

**Solución adoptada:** Google Cloud Run — despliegue por imagen Docker con escalado a cero
automático, y MongoDB Atlas (gestionado, sin infraestructura propia) en vez de una base de datos
autoalojada en ACI. Ver [docs/GoogleCloudDeploy.md](docs/GoogleCloudDeploy.md).

## Dockerización: `spring-boot-starter-tomcat` con `scope: provided` rompía el arranque en contenedor

**Síntoma:** `Unable to access jarfile app.jar` / `app.war` al ejecutar `java -jar` dentro del
contenedor.

**Causa:** el perfil `dev` original marcaba `spring-boot-starter-tomcat` como `provided` porque en
ese perfil el servidor lo pone un Tomcat externo. Al reutilizar el mismo `pom.xml` sin perfil para
el build de Docker, Maven no empaquetaba Tomcat dentro del artefacto — y sin servidor embebido, la
app no levanta con `java -jar`.

**Solución:** separar los perfiles Maven `dev` (WAR + Tomcat `provided`, para desplegar en un
Tomcat externo) y `prod` (sin ese `provided`, Tomcat embebido incluido) — ver `pom.xml`. El CI y
Docker siempre usan `-P prod`.

## Cloud Run: `--set-env-vars` / `--set-secrets` reemplazan el conjunto completo, no lo fusionan

**Síntoma:** una variable de entorno o secreto que se añadía a mano desde la consola de Cloud Run
desaparecía sin aviso en el siguiente despliegue automático.

**Causa:** ambos flags de `gcloud run deploy` fijan el conjunto **completo** de esa categoría para
la nueva revisión — no añaden a lo que hubiera antes. Cualquier configuración puesta solo en la
consola (no en `deploy.yml`) se pierde en el próximo deploy.

**Solución:** toda la configuración de runtime vive versionada en
[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml), nunca solo en la consola de GCP.
Añadir una variable o secreto nuevo significa sumarlo a la lista existente en ese archivo, no
crear un flag adicional. Detalle paso a paso en
[docs/GoogleCloudDeploy.md](docs/GoogleCloudDeploy.md#cómo-añadir-una-variable-o-secreto-nuevo).

## Cloud Run: `MAPIT_JWT_EXPIRATION` no se mapeaba solo a `mapit.jwt.expiration-seconds`

**Síntoma:** el tiempo de expiración del JWT en producción no cogía el valor esperado pese a
declarar la variable de entorno.

**Causa:** el *relaxed binding* de Spring solo mapea automáticamente variables de entorno cuyo
nombre coincide de forma relajada con la propiedad (`MAPIT_JWT_EXPIRATION_SECONDS` habría mapeado
a `expiration-seconds`), pero la variable ya en uso en Cloud Run era `MAPIT_JWT_EXPIRATION`.

**Solución:** puente explícito en `application-prod.yaml`:
`expiration-seconds: ${MAPIT_JWT_EXPIRATION:86400}`. Documentado inline en el propio YAML para que
no se repita el mismo despiste al tocarlo.

## Enlace de verificación de email con `//` duplicada

**Síntoma:** el link del correo de verificación de cuenta llegaba con una barra duplicada
(`.../verify-email//?token=...`), rompiendo el enlace en algunos clientes de correo.

**Causa:** `MailService` concatena `frontendBaseUrl + "/verify-email?token=..."`; si
`MAPIT_FRONTEND_URL` se configuraba con barra final (`https://mapit-web.com/`), el resultado
quedaba con `//`.

**Solución:** fijar `MAPIT_FRONTEND_URL` **sin barra final** en la configuración de Cloud Run
(`https://mapit-web.com`, no `https://mapit-web.com/`). Anotado explícitamente en
[docs/GoogleCloudDeploy.md](docs/GoogleCloudDeploy.md) para no repetir el error al rotar el valor.

## `nick` en el perfil rompió la compilación en dos call sites

**Síntoma:** el proyecto dejó de compilar (`./mvnw compile` fallaba) tras añadir el campo `nick`
al perfil de usuario.

**Causa:** `UserPatchRequest` (record) ganó un nuevo componente `nick`, pero
`UserController#addFavoriteLocationType` y `#removeFavoriteLocationType` seguían construyéndolo
con la lista de argumentos posicional antigua (8 valores en vez de 9) — al ser un `record`, el
constructor canónico exige todos los componentes en orden, y el compilador lo rechaza en vez de
rellenar en silencio.

**Solución:** añadir el argumento `null` para `nick` en la posición correcta en ambos call sites.
Lección de proceso: al añadir un campo a un `record` usado como DTO, buscar (`grep`) todos los
`new NombreRecord(` existentes antes de dar el cambio por terminado — el compilador solo detecta
el desajuste de aridad, no de significado, así que un `record` con más campos "compatibles" por
tipo podría compilar igual con los valores desplazados.

## Repositorios con tipos heredados de la era Postgres/UUID sin migrar

**Síntoma:** ninguno visible — ambos métodos estaban sin usar en el código de negocio.

**Causa:** `PlaceRepository.findByLocationTypeId` y `UserRepository.findByFavoriteLocationTypeIds`
declaraban su parámetro como `Long`, heredado de cuando los ids eran numéricos/UUID en el diseño
Postgres original; los campos reales (`Place.locationTypeId`, `User.favoriteLocationTypeIds`) son
`String` desde la migración a MongoDB. Al no tener ningún caller, el desajuste nunca se manifestó
en tests ni en producción.

**Solución:** corregidos a `String` (o eliminados si quedaban duplicados). Lección: una consulta
derivada de Spring Data no falla al arrancar solo por un tipo de parámetro incorrecto si nadie la
invoca — conviene revisar periódicamente los métodos de repositorio sin caller tras una migración
de tipos de id.

## `CategoryMapper` duplicaba, sin usarla, la lógica real de `CategoryCrudService`

**Síntoma:** ninguno funcional — hallazgo de revisión de código antes de hacer público el repo.

**Causa:** existía un `CategoryMapper` completo (entidad → DTO para el árbol de categorías) que
ningún controller ni servicio llegó a invocar; `CategoryCrudService#getCategoryTree` construye los
mismos DTOs de forma manual e inline, con su propia lógica de agrupación por
`mainCategoryId`/`subCategoryId`. Probablemente quedó de una refactorización a medias.

**Solución:** eliminado `CategoryMapper` — mantener dos implementaciones del mismo mapeo invita a
que diverjan sin que ningún test lo detecte (el mapper muerto no tenía test propio).

## Contraseña de administrador hardcodeada en el seeder

**Síntoma:** ninguno funcional — hallazgo de revisión de código antes de hacer público el repo.

**Causa:** `AdminUserSeeder` tenía el email, nick y password del usuario admin como literales en
el código fuente (incluida una password débil, `"12345678"`), en vez de leerlos de entorno como el
resto de secretos del proyecto.

**Solución:** parametrizados vía `@Value("${mapit.admin.*:default-solo-dev}")`, con el mismo
patrón que `JwtService`/`GeoIpService`. **Importante:** este cambio no rota la contraseña ya
existente en la base de datos de producción ni la borra del historial de git — sigue pendiente
cambiar manualmente la contraseña de esa cuenta admin real antes (o justo después) de hacer
público el repositorio.

## BCrypt trunca en silencio contraseñas de más de 72 bytes

**Síntoma:** ninguno visible para el usuario — el riesgo era silencioso: dos contraseñas distintas
que solo difirieran después del byte 72 se habrían validado como iguales, porque BCrypt solo
procesa los primeros 72 bytes de la entrada.

**Causa:** sin un chequeo explícito, una contraseña larga (posible con passphrases o copy-paste de
un gestor de contraseñas) se trunca en `PasswordEncoder` sin que ni el usuario ni el sistema lo
noten.

**Solución:** `PasswordPolicyService` rechaza explícitamente cualquier contraseña que supere 72
bytes UTF-8 con `ApiException` (`PASSWORD_TOO_LONG`) antes de llegar al encoder, en vez de dejar
que BCrypt trunque en silencio. Cubierto por
`PasswordPolicyServiceTest#validate_conPasswordSuperando72Bytes_lanzaApiException`.
