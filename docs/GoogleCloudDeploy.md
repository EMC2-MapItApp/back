# Despliegue en Google Cloud Run

> Documento vivo — si cambia el pipeline (`.github/workflows/deploy.yml`) o la config del
> servicio en Cloud Run, actualiza esto. Ante cualquier duda, `deploy.yml` y el propio servicio
> de Cloud Run (`gcloud run services describe backend --project mapitback --region
> europe-southwest1`) son la fuente de verdad, no este documento.

## Pipeline (`.github/workflows/deploy.yml`)

Se dispara con push a la rama `main_back`. Tres fases:

1. **CI**: `./mvnw clean verify -P prod` (compila, testea, empaqueta el WAR con el perfil `prod`
   activo — `pom.xml` fija `spring.profiles.active=prod` en ese perfil, así que el WAR ya trae el
   perfil correcto sin depender de una variable de entorno en runtime).
2. **Build de imagen**: Docker build con el WAR ya compilado (no recompila dentro del contenedor)
   y push a Artifact Registry (`europe-southwest1-docker.pkg.dev/mapitback/mapit-repo/backend`).
3. **Deploy**: `gcloud run deploy backend` en el proyecto `mapitback`, región
   `europe-southwest1`, inyectando toda la configuración de runtime vía `--set-env-vars` y
   `--set-secrets` (ver abajo).

## Variables de entorno y secretos en producción

Toda la config de runtime está declarada en `deploy.yml`, no solo en la consola de Cloud Run —
así queda versionada y su historial se puede seguir con `git log` sobre ese archivo.

**Variables normales** (`--set-env-vars`):

| Variable | Valor | Notas |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Redundante con el perfil Maven `prod` del WAR, pero inofensivo — se deja explícito. |
| `MAPIT_JWT_EXPIRATION` | `86400` | Puente hacia `mapit.jwt.expiration-seconds` (el relaxed binding de Spring no lo mapea solo). |
| `MAPIT_SMTP_HOST` | `smtp.resend.com` | |
| `MAPIT_SMTP_PORT` | `587` | |
| `MAPIT_MAIL_FROM` | `no-reply@mapit-web.com` | Tiene default en `application.yaml`, pero se fija explícito en prod. |
| `MAPIT_FRONTEND_URL` | `https://mapit-web.com` | **Sin barra final.** `MailService` concatena `frontendBaseUrl + "/verify-email?..."`; con barra final el link del correo de verificación sale con `//` duplicada. |

**Secretos** (`--set-secrets`, Google Secret Manager):

| Variable de entorno | Secreto en Secret Manager |
|---|---|
| `MAPIT_JWT_SECRET` | `MAPIT_JWT_SECRET:latest` |
| `SPRING_DATA_MONGODB_URI` | `MAPIT_MONGO_URI:latest` |
| `MAPIT_SMTP_USERNAME` | `MAPIT_SMTP_USERNAME:latest` |
| `MAPIT_SMTP_PASSWORD` | `MAPIT_SMTP_PASSWORD:latest` |
| `MAPIT_ADMIN_EMAIL` | `MAPIT_ADMIN_EMAIL:latest` |
| `MAPIT_ADMIN_NICK` | `MAPIT_ADMIN_NICK:latest` |
| `MAPIT_ADMIN_PASSWORD` | `MAPIT_ADMIN_PASSWORD:latest` |

Los tres `MAPIT_ADMIN_*` alimentan `AdminUserSeeder` (mapean por relaxed binding a
`mapit.admin.email`/`nick`/`password`). El seeder es idempotente: si el usuario admin ya existe con ese email, no
lo vuelve a crear ni le cambia la contraseña — para rotar la de una cuenta ya existente hay que
hacerlo aparte (a mano en Mongo o vía un futuro endpoint de cambio de password), no solo
actualizando el secreto.

## Cómo añadir una variable o secreto nuevo

**Variable no sensible** (URL, flag, host...):
1. Referenciarla en `application-prod.yaml` (o `application.yaml` si aplica a todos los
   perfiles), p. ej. `mi-propiedad: ${MI_VARIABLE}`.
2. Sumarla a la lista de `--set-env-vars` en `deploy.yml` (no crear un segundo bloque).
3. Commit + push a `main_back` → se despliega en la siguiente revisión.

**Secreto** (contraseña, clave, URI con credenciales):
1. Crearlo en Secret Manager:
   ```bash
   gcloud secrets create NOMBRE_SECRETO --project mapitback
   echo -n "valor" | gcloud secrets versions add NOMBRE_SECRETO --data-file=- --project mapitback
   ```
2. Dar acceso a la service account que usa Cloud Run:
   ```bash
   gcloud secrets add-iam-policy-binding NOMBRE_SECRETO \
     --member="serviceAccount:<SA-de-Cloud-Run>" \
     --role="roles/secretmanager.secretAccessor" \
     --project mapitback
   ```
3. Referenciar la propiedad correspondiente en el YAML de Spring.
4. Sumarlo a `--set-secrets` en `deploy.yml` con el formato `VAR_ENTORNO=NOMBRE_SECRETO:latest`.
5. Commit + push → se despliega solo.

### ⚠️ `--set-env-vars` y `--set-secrets` reemplazan, no fusionan

Cada uno de estos flags fija el conjunto **completo** de esa categoría para la nueva revisión de
Cloud Run — no añaden a lo que hubiera antes. Por eso el paso 2/4 de arriba dice "sumar a la
lista existente" y no "usar un flag nuevo": si una variable o secreto solo está puesto a mano en
la consola de Cloud Run y no en `deploy.yml`, el siguiente deploy automático lo hace desaparecer
sin aviso. La consola solo sirve para inspeccionar (`gcloud run services describe ...`), no como
fuente de configuración persistente.
