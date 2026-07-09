# Despliegue a Azure — Registro de pasos

Propósito: Documentar cada acción realizada para desplegar el backend `mapIt` en Azure, con comandos exactos, valores detectados y resultados.

Fecha inicio: 2026-07-01

## Entorno y datos detectados
- Tenant: `9feef9a5-c8b5-453e-800f-0cbbf11e6ac3`
- Suscripción seleccionada: `Suscripción de Azure 1` (`2691bf9c-a75e-4149-8652-1694add31417`)
- Carpeta del proyecto backend: `d:\MapIt\BACK`

---

## Registro de acciones (cronológico)

### 2026-07-01 10:45 — Intento de login inicial (interactivo)
Comando ejecutado:
```powershell
az login
```
Resultado / salida relevante:
- Error AADSTS50076 (MFA requerido). Recomendación: usar `--use-device-code` o `--tenant`.

---

### 2026-07-01 10:47 — Login forzado con tenant y selección de suscripción
Comando ejecutado:
```powershell
az login --tenant 9feef9a5-c8b5-453e-800f-0cbbf11e6ac3
```
Resultado / salida relevante:
- Se solicitó selección de suscripción.
- Selección: opción `1` → Suscripción seleccionada `Suscripción de Azure 1` (`2691bf9c-a75e-4149-8652-1694add31417`)
- Estado: Autenticación completada y suscripción lista para uso.

---

## Pasos planificados (siguientes, ejecutables)

1. Preparar proyecto para JAR:
   - Editar `pom.xml`: cambiar `<packaging>war</packaging>` → `<packaging>jar</packaging>` y poner `spring-boot-starter-tomcat` como `<scope>provided</scope>`.
   - Actualizar `src/main/resources/application.yaml` para usar variables de entorno:
     ```yaml
     server:
       port: ${PORT:8090}

     spring:
       datasource:
         url: ${DB_URL}
         username: ${DB_USERNAME}
         password: ${DB_PASSWORD}
       jpa:
         hibernate:
           ddl-auto: validate

     mapit:
       jwt:
         secret: ${MAPIT_JWT_SECRET}
         expiration-seconds: 86400
     ```
   - Actualizar `CorsConfig` para usar `ALLOWED_ORIGINS` o restringir a dominio final.

2. Crear recursos en Azure (PowerShell / Azure CLI). Valores recomendados:
   ```powershell
   $RG="mapit-rg"
   $LOCATION="westeurope"
   $DB_SERVER="mapit-db-server-$(Get-Random)"
   $DB_NAME="mapit_db"
   $DB_ADMIN="mapitadmin"
   $APP_PLAN="mapit-plan"
   $APP_NAME="mapit-back-$(Get-Random)"
   $SUBSCRIPTION="2691bf9c-a75e-4149-8652-1694add31417"
   az account set --subscription $SUBSCRIPTION
   az group create --name $RG --location $LOCATION
   ```

   - Crear PostgreSQL Flexible Server (introducir password seguro cuando se pida).
   - Crear base de datos y habilitar PostGIS (`CREATE EXTENSION IF NOT EXISTS postgis;`).
   - Crear App Service Plan y Web App (Java SE / JAR).

3. Configurar Application Settings en App Service:
   - `DB_URL` = `jdbc:postgresql://<server>.postgres.database.azure.com:5432/mapit_db?sslmode=require`
   - `DB_USERNAME` = `<admin>@<server>`
   - `DB_PASSWORD` = `<password>`
   - `MAPIT_JWT_SECRET` = resultado de `openssl rand -hex 64` (o PowerShell RNG)
   - `ALLOWED_ORIGINS` = `https://tu-app.pages.dev`

4. Compilar y desplegar:
   ```powershell
   cd d:\MapIt\BACK
   mvn clean package -DskipTests
   az webapp deploy --resource-group $RG --name $APP_NAME --src-path target\mapIt-0.0.1-SNAPSHOT.jar --type jar
   ```

5. Verificación:
   ```powershell
   az webapp log tail --name $APP_NAME --resource-group $RG
   curl "https://$APP_NAME.azurewebsites.net/api/v1/categories/tree"
   ```

6. Tareas posteriores:
   - Actualiza `src/environments/environment.prod.ts` del frontend con `https://$APP_NAME.azurewebsites.net` para las APIs.
   - Restringe CORS: actualiza `CorsConfig` para usar `ALLOWED_ORIGINS` (variable).
   - Cambia/rota `MAPIT_JWT_SECRET` cuando sea necesario.

---

## Plantilla de nueva entrada de log
Cada vez que ejecutes un paso pega aquí la salida relevante y yo generaré la siguiente entrada en el mismo formato. Ejemplo:

```
### 2026-07-01 11:22 — Crear recurso PostgreSQL Flexible Server
Comando:
<comando>
Salida:
<salida>
Observaciones:
<observaciones>
```

---

## Notas de seguridad y producción
- No subir credenciales a Git. Usar Application Settings (Azure) o Key Vault para secretos.
- Cambiar `mapit.jwt.secret` por un secreto fuerte (256 bits).
- En producción, fijar `spring.jpa.hibernate.ddl-auto: validate` (no `update`).
- Restringir CORS a orígenes permitidos.
