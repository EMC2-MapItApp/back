# Tests del Backend MapIt

Suite de tests unitarios y de capa web para el backend Spring Boot.
Tecnologías utilizadas: **JUnit 5**, **Mockito**, **AssertJ**, **MockMvc**.

---

## Estructura

```
src/test/java/emc/mapIt/
├── service/
│   ├── HashServiceTest.java               ← unitario puro
│   ├── JwtServiceTest.java                ← unitario puro
│   ├── AuthServiceTest.java               ← unitario con Mockito
│   ├── PasswordPolicyServiceTest.java     ← unitario puro (Fase 1 auth)
│   ├── EmailVerificationServiceTest.java  ← unitario con Mockito (Fase 1 auth)
│   ├── PasswordResetServiceTest.java      ← unitario con Mockito (Fase 1 auth)
│   └── PublicationServiceTest.java        ← unitario con Mockito (borrado automático de caducadas)
├── controller/
│   └── AuthControllerTest.java     ← capa web con MockMvc
└── notifications/
    ├── NotificationServiceTest.java     ← unitario con Mockito (orquestador email/in-app/push)
    ├── WebPushSenderTest.java           ← unitario puro (adaptador VAPID, sin red real)
    └── NotificationControllerTest.java  ← capa web con MockMvc
```

---

## HashServiceTest

**Tipo:** Unitario puro — sin Spring, sin mocks.
**Clase bajo test:** `emc.mapIt.service.HashService`

Verifica el comportamiento del servicio de hashing SHA-256.

| Método de test | Descripción |
|---|---|
| `sha256_devuelveHashDe64Caracteres` | El hash de cualquier entrada tiene exactamente 64 caracteres hexadecimales |
| `sha256_esIdempotente` | La misma entrada siempre produce el mismo hash |
| `sha256_entradasDistintasProducenHashesDistintos` | Entradas diferentes producen hashes diferentes |
| `sha256_valorConocido` | El hash de `"abc"` coincide con el valor SHA-256 estándar externo |

---

## JwtServiceTest

**Tipo:** Unitario puro — instancia directa con secret y expiración de prueba.
**Clase bajo test:** `emc.mapIt.service.JwtService`

Verifica la generación y validación de tokens JWT propios (HMAC-based, sin librería externa).

| Método de test | Descripción |
|---|---|
| `generateToken_devuelveStringConDosPartes` | El token generado tiene el formato `payload.firma` |
| `extractUserId_conTokenValido_devuelveElUserId` | Extrae correctamente el userId de un token válido |
| `extractUserId_conTokenExpirado_lanzaApiException` | Token con expiración negativa lanza `ApiException` con mensaje `"expirado"` |
| `extractUserId_sinHeaderBearer_lanzaApiException` | Cadena sin prefijo `Bearer ` lanza `ApiException` |
| `extractUserId_conNull_lanzaApiException` | Header `null` lanza `ApiException` |
| `extractUserId_conFirmaManipulada_lanzaApiException` | Token con firma alterada manualmente lanza `ApiException` |

---

## AuthServiceTest

**Tipo:** Unitario con Mockito — `@ExtendWith(MockitoExtension.class)`, sin Spring context.
**Clase bajo test:** `emc.mapIt.service.AuthService`

Dependencias mockeadas:

- `UserService`
- `PasswordEncoder`
- `JwtService`
- `AuthRegisterToUserMapper`
- `UserWithProfileToMapItUserMapper`
- `PasswordPolicyService`
- `EmailVerificationService`
- `PasswordResetService`

### Grupo: `register`

| Método de test | Descripción |
|---|---|
| `register_conDatosValidos_devuelveAuthResponse` | Happy path: devuelve `AuthResponse` con token y datos del usuario |
| `register_conRequestNull_lanzaApiException` | Request `null` lanza `ApiException` con mensaje `"requerida"` |
| `register_conEmailBlanco_lanzaApiException` | Email en blanco (espacios) lanza `ApiException` |
| `register_conPasswordBlanca_lanzaApiException` | Password vacía lanza `ApiException` |
| `register_conUserTypeNull_lanzaApiException` | `userType` nulo lanza `ApiException` |

### Grupo: `login`

| Método de test | Descripción |
|---|---|
| `login_conUsuarioVerificado_devuelveAuthResponse` | Happy path con email: credenciales correctas + email verificado devuelven `AuthResponse` con token |
| `login_conUsuarioNoVerificado_lanzaApiExceptionForbidden` | Email sin verificar lanza `ApiException` (`EMAIL_NOT_VERIFIED`, 403), tras validar la password |
| `login_conPasswordIncorrecta_lanzaApiException` | Password incorrecta lanza `ApiException` con mensaje `"invalidas"` |
| `login_conRequestNull_lanzaApiException` | Request `null` lanza `ApiException` |
| `login_conEmailBlanco_lanzaApiException` | Identifier vacío lanza `ApiException` |
| `login_conNickValido_devuelveAuthResponse` | `identifier` con prefijo `@` (p.ej. `@ana`) resuelve por nick vía `UserService.getByNickOrThrow`, sin tocar `getByEmailOrThrow` |
| `login_conNickInexistente_lanzaApiException` | `@nick` inexistente lanza `ApiException` (`UNAUTHORIZED`) — mismo mensaje genérico que password incorrecta |
| `login_conIdentifierSinFormatoValido_lanzaApiExceptionSinConsultarUsuario` | `identifier` que no es ni `@nick` ni email → `ApiException` (`BAD_REQUEST`) sin consultar `UserService` |

### Grupo: `forgotPassword` / `resetPassword` (Fase 1 auth)

| Método de test | Descripción |
|---|---|
| `forgotPassword_delegaEnPasswordResetService` | Delega en `PasswordResetService.requestReset` |
| `resetPassword_delegaEnPasswordResetService` | Delega en `PasswordResetService.resetPassword` |

---

## PasswordPolicyServiceTest (Fase 1 auth)

**Tipo:** Unitario puro — sin Spring, sin mocks.
**Clase bajo test:** `emc.mapIt.service.PasswordPolicyService`

Verifica el rechazo de contraseñas débiles según puntuación zxcvbn (`com.nulabinc.zxcvbn`) y el límite efectivo de 72 bytes de BCrypt.

| Método de test | Descripción |
|---|---|
| `validate_conPasswordFuerte_noLanzaExcepcion` | Password con score alto no lanza excepción |
| `validate_conPasswordDebil_lanzaApiException` | Password de diccionario (`"12345678"`) lanza `ApiException` (`WEAK_PASSWORD`) |
| `validate_conPasswordQueContieneNombreDelUsuario_lanzaApiException` | zxcvbn penaliza passwords derivadas del nombre/email del propio usuario |
| `validate_conPasswordSuperando72Bytes_lanzaApiException` | Password >72 bytes lanza `ApiException` (`PASSWORD_TOO_LONG`) en vez de dejar que BCrypt trunque en silencio |

---

## EmailVerificationServiceTest (Fase 1 auth)

**Tipo:** Unitario con Mockito.
**Clase bajo test:** `emc.mapIt.service.EmailVerificationService`

Dependencias mockeadas: `EmailVerificationTokenRepository`, `UserRepository`, `HashService`, `NotificationSender`.

| Método de test | Descripción |
|---|---|
| `issueAndSend_creaTokenYEnviaCorreo` | Genera token, lo guarda hasheado y llama a `NotificationSender` |
| `issueAndSend_invalidaTokenAnteriorAntesDeCrearUnoNuevo` | Borra cualquier token no consumido previo del usuario |
| `verify_conTokenValido_marcaUsuarioVerificado` | Token válido marca `emailVerified=true` y consume el token |
| `verify_conTokenExpirado_lanzaApiException` | Token expirado lanza `ApiException` (`INVALID_TOKEN`) |
| `verify_conTokenYaConsumido_lanzaApiException` | Token ya consumido lanza `ApiException` |
| `verify_conTokenInexistente_lanzaApiException` | Token desconocido lanza `ApiException` |
| `resend_conEmailNoRegistrado_noLanzaYNoEnviaCorreo` | Email no registrado no lanza ni envía correo (anti-enumeración) |
| `resend_conEmailYaVerificado_noEnviaCorreo` | Usuario ya verificado no recibe reenvío |
| `resend_dentroDeCooldown_noReenviaCorreo` | Reenvío reciente (dentro del cooldown server-side) no reenvía |
| `resend_fueraDeCooldown_reenviaCorreo` | Fuera del cooldown, reemite y envía |

---

## PasswordResetServiceTest (Fase 1 auth)

**Tipo:** Unitario con Mockito.
**Clase bajo test:** `emc.mapIt.service.PasswordResetService`

Dependencias mockeadas: `PasswordResetTokenRepository`, `UserRepository`, `HashService`, `NotificationSender`, `PasswordEncoder`, `PasswordPolicyService`.

| Método de test | Descripción |
|---|---|
| `requestReset_conEmailRegistrado_creaTokenYEnviaCorreo` | Genera token, lo guarda hasheado y llama a `NotificationSender` |
| `requestReset_conEmailNoRegistrado_lanzaApiExceptionNotFound` | Email no registrado lanza `ApiException` (`NOT_FOUND`) — a diferencia de `resend`, aquí sí se distingue |
| `requestReset_dentroDeCooldown_noReenviaCorreo` | Solicitud reciente (dentro del cooldown server-side) no reenvía |
| `requestReset_fueraDeCooldown_reenviaCorreo` | Fuera del cooldown, reemite y envía |
| `resetPassword_conTokenValido_actualizaPasswordYConsumeToken` | Token válido actualiza el hash de contraseña, consume el token y borra otros tokens pendientes del usuario |
| `resetPassword_conTokenExpirado_lanzaApiException` | Token expirado lanza `ApiException` (`INVALID_TOKEN`) |
| `resetPassword_conTokenYaConsumido_lanzaApiException` | Token ya consumido lanza `ApiException` |
| `resetPassword_conTokenInexistente_lanzaApiException` | Token desconocido lanza `ApiException` |
| `resetPassword_conPasswordDebil_lanzaApiExceptionYNoActualizaPassword` | Password débil lanza `ApiException` (`WEAK_PASSWORD`) sin tocar el usuario |

---

## PublicationServiceTest

**Tipo:** Unitario con Mockito.
**Clase bajo test:** `emc.mapIt.service.PublicationService`

Dependencias mockeadas: `PublicationRepository`, `PublicationEnrollmentRepository`, `UserRepository`, `LocationTypeRepository`, `PublicationMapper`.

Cubre el job programado (`@Scheduled`) que borra definitivamente las publicaciones caducadas hace más de 3 meses (según `endDate`, no fecha de creación; las promociones indefinidas con `endDate` null nunca se ven afectadas — ver `PublicationRepository.findExpiredSince`).

| Método de test | Descripción |
|---|---|
| `deleteExpiredPublications_conCaducadasHaceMasDeTresMeses_lasElimina` | Con publicaciones caducadas hace >3 meses, calcula el corte correcto y llama a `deleteAll` |
| `deleteExpiredPublications_sinCaducadas_noBorraNada` | Sin resultados de `findExpiredSince`, no invoca `deleteAll` |

---

## AuthControllerTest

**Tipo:** Capa web con `@WebMvcTest` y `MockMvc`.
**Clase bajo test:** `emc.mapIt.controller.AuthController`

Spring Security excluido (`SecurityAutoConfiguration`, `SecurityFilterAutoConfiguration`) para aislar la lógica HTTP de la autenticación.

Dependencias mockeadas con `@MockBean`:

- `AuthService`
- `UserService`

### Grupo: `POST /api/v1/auth/register`

Desde Fase 1, el registro ya no autentica (no devuelve token; el usuario debe verificar su email).

| Método de test | Descripción |
|---|---|
| `register_conBodyValido_devuelve201SinToken` | Body válido → HTTP 201, body JSON con `email`, sin `token` |
| `register_conEmailInvalido_devuelve400` | Email con formato inválido → HTTP 400 (Bean Validation `@Email` + `@Pattern`) |
| `register_sinBody_devuelve400` | Sin body → HTTP 400 |
| `register_conPasswordCortaMenosDe8_devuelve400` | Password <8 caracteres → HTTP 400 (Bean Validation `@Size`) |
| `register_conPasswordDebil_devuelve400` | `AuthService` lanza `WEAK_PASSWORD` → HTTP 400 con `error.code` |

### Grupo: `POST /api/v1/auth/login`

| Método de test | Descripción |
|---|---|
| `login_conCredencialesValidas_devuelve200ConToken` | Credenciales válidas (`identifier` con formato email) → HTTP 200, body JSON con `token` |
| `login_conIdentifierBlanco_devuelve400` | `identifier` vacío → HTTP 400 (Bean Validation `@NotBlank`; el formato email/nick ya no se valida en el DTO) |
| `login_conNick_devuelve200ConToken` | `identifier` con prefijo `@` (nick) → HTTP 200, body JSON con `token` (smoke test; la lógica de resolución vive en `AuthServiceTest`) |
| `login_conUsuarioNoVerificado_devuelve403` | `AuthService` lanza `EMAIL_NOT_VERIFIED` → HTTP 403 con `error.code` |

### Grupo: `POST /api/v1/auth/verify-email` (Fase 1 auth)

| Método de test | Descripción |
|---|---|
| `verifyEmail_conTokenValido_devuelve200` | Token válido → HTTP 200 |
| `verifyEmail_conTokenInvalido_devuelve400` | Token inválido/expirado → HTTP 400 con `error.code=INVALID_TOKEN` |

### Grupo: `POST /api/v1/auth/resend-verification` (Fase 1 auth)

| Método de test | Descripción |
|---|---|
| `resendVerification_siempreDevuelve200ConMensajeGenerico` | Siempre HTTP 200 con mensaje genérico, exista o no el email (anti-enumeración) |

### Grupo: `POST /api/v1/auth/forgot-password` (Fase 1 auth)

A diferencia de `resend-verification`, aquí sí se distingue si el email existe (404 si no).

| Método de test | Descripción |
|---|---|
| `forgotPassword_conEmailRegistrado_devuelve200ConMensaje` | Email registrado → HTTP 200 con mensaje de confirmación |
| `forgotPassword_conEmailNoRegistrado_devuelve404` | `AuthService` lanza `NOT_FOUND` → HTTP 404 con `error.code` |
| `forgotPassword_conEmailInvalido_devuelve400` | Email con formato inválido → HTTP 400 (Bean Validation `@Email`) |

### Grupo: `POST /api/v1/auth/reset-password` (Fase 1 auth)

| Método de test | Descripción |
|---|---|
| `resetPassword_conTokenValido_devuelve200` | Token válido → HTTP 200 |
| `resetPassword_conTokenInvalido_devuelve400` | Token inválido/expirado → HTTP 400 con `error.code=INVALID_TOKEN` |
| `resetPassword_conPasswordDebil_devuelve400` | `AuthService` lanza `WEAK_PASSWORD` → HTTP 400 con `error.code` |

### Grupo: `POST /api/v1/auth/logout`

| Método de test | Descripción |
|---|---|
| `logout_devuelve204SinCuerpo` | Logout stateless → HTTP 204, cuerpo vacío |

---

## NotificationServiceTest

**Tipo:** Unitario con Mockito.
**Clase bajo test:** `emc.mapIt.notifications.NotificationService`

Dependencias mockeadas: `NotificationSender`, `PushSender`, `NotificationRepository`, `PushSubscriptionRepository`.

| Método de test | Descripción |
|---|---|
| `notifyGroupInvitation_enviaEmailYPersisteNotificacionInApp` | Llama al email existente y persiste una `Notification` in-app |
| `notifyGroupInvitation_conSuscripcionesActivas_envíaPushACadaUna` | Hace fan-out del push a todas las `PushSubscription` del usuario |
| `notifyGroupInvitation_conSuscripcionCaducada_laBorraYSigueConLasDemas` | `PushSubscriptionExpiredException` borra solo esa suscripción, sin abortar el resto |
| `notifyGroupInvitation_conFalloDeEntregaPush_noPropagaLaExcepcion` | `PushDeliveryException` no rompe el flujo de negocio (push es best-effort) |
| `notifyGroupSignupInvitationEmail_soloEnviaEmail_sinPersistirNiPush` | Invitación por email sin cuenta: pass-through solo al email, sin `userId` al que asociar push/in-app |
| `notifyGroupOrganizerNotice_enviaEmailYPersisteNotificacion` | Aviso al organizador: email + notificación in-app |
| `notifyGroupBroadcast_enviaEmailYPersisteNotificacion` | Difusión a un miembro: email + notificación in-app |
| `markRead_conNotificacionPropiaNoLeida_laMarcaLeida` | Marca como leída y fija `readAt` |
| `markRead_yaLeida_noVuelveAGuardar` | No repite el `save` si ya estaba leída |
| `markAllRead_marcaTodasLasNoLeidas` | Marca en bloque todas las no leídas del usuario |
| `registerSubscription_nueva_laCreaConDatosDelDispositivo` | Registra una suscripción nueva con endpoint/claves/fecha |
| `unregisterSubscription_borraPorEndpoint` | Desregistra por `endpoint` |

---

## WebPushSenderTest

**Tipo:** Unitario puro — sin red real, con un par de claves VAPID generado en `@BeforeAll` vía `nl.martijndwars.webpush.Utils`.
**Clase bajo test:** `emc.mapIt.notifications.WebPushSender`

| Método de test | Descripción |
|---|---|
| `send_sinClavesVapidConfiguradas_lanzaPushDeliveryExceptionSinIntentarRed` | Sin `mapit.push.vapid.*` configurado, el adaptador queda inactivo y falla rápido (fail-soft) |
| `send_conClaveDeSuscriptorInvalida_envuelveElFalloEnPushDeliveryException` | Clave `p256dh` inválida del suscriptor → fallo de cifrado envuelto en `PushDeliveryException`, antes de cualquier llamada HTTP |

---

## NotificationControllerTest

**Tipo:** Capa web con `@WebMvcTest` y `MockMvc` (Security excluido, mismo criterio que `AuthControllerTest`).
**Clase bajo test:** `emc.mapIt.notifications.NotificationController`

Dependencias mockeadas con `@MockBean`: `NotificationService`, `NotificationMapper`, `AuthService`, `JwtService`.

| Método de test | Descripción |
|---|---|
| `list_devuelveNotificacionesMapeadas` | `GET /api/v1/notifications` devuelve el histórico mapeado a DTO |
| `unreadCount_devuelveContador` | `GET /unread-count` devuelve `{ "count": n }` |
| `markRead_marcaLaNotificacionComoLeida` | `PATCH /{id}/read` → HTTP 204 y delega en el servicio |
| `markAllRead_marcaTodasComoLeidas` | `POST /read-all` → HTTP 204 y delega en el servicio |
| `pushPublicKey_devuelveLaClaveConfigurada` | `GET /push/public-key` devuelve la clave VAPID configurada (única ruta pública del módulo) |
| `registerPushSubscription_conBodyValido_registraLaSuscripcion` | `POST /push/subscriptions` con body válido → HTTP 204 |
| `registerPushSubscription_sinEndpoint_devuelve400` | Body sin `endpoint` → HTTP 400 (Bean Validation) |
| `unregisterPushSubscription_borraPorEndpoint` | `DELETE /push/subscriptions` con body `{endpoint}` → HTTP 204 |

---

## Ejecución

```bash
# Todos los tests
./mvnw test

# Solo los tests de servicios
./mvnw test -Dtest="HashServiceTest,JwtServiceTest,AuthServiceTest,PasswordPolicyServiceTest,EmailVerificationServiceTest,PasswordResetServiceTest,PublicationServiceTest"

# Solo los tests de controladores
./mvnw test -Dtest="AuthControllerTest"

# Solo los tests del módulo de notificaciones (in-app + push VAPID)
./mvnw test -Dtest="NotificationServiceTest,WebPushSenderTest,NotificationControllerTest"
```
