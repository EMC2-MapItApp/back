# Tests del Backend MapIt

Suite de tests unitarios y de capa web para el backend Spring Boot.
Tecnologías utilizadas: **JUnit 5**, **Mockito**, **AssertJ**, **MockMvc**.

---

## Estructura

```
src/test/java/emc/mapIt/
├── service/
│   ├── HashServiceTest.java        ← unitario puro
│   ├── JwtServiceTest.java         ← unitario puro
│   └── AuthServiceTest.java        ← unitario con Mockito
└── controller/
    └── AuthControllerTest.java     ← capa web con MockMvc
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
| `login_conCredencialesCorrectas_devuelveAuthResponse` | Happy path: credenciales correctas devuelven `AuthResponse` con token |
| `login_conPasswordIncorrecta_lanzaApiException` | Password incorrecta lanza `ApiException` con mensaje `"invalidas"` |
| `login_conRequestNull_lanzaApiException` | Request `null` lanza `ApiException` |
| `login_conEmailBlanco_lanzaApiException` | Email vacío lanza `ApiException` |

---

## AuthControllerTest

**Tipo:** Capa web con `@WebMvcTest` y `MockMvc`.
**Clase bajo test:** `emc.mapIt.controller.AuthController`

Spring Security excluido (`SecurityAutoConfiguration`, `SecurityFilterAutoConfiguration`) para aislar la lógica HTTP de la autenticación.

Dependencias mockeadas con `@MockBean`:

- `AuthService`
- `UserService`

### Grupo: `POST /api/v1/auth/register`

| Método de test | Descripción |
|---|---|
| `register_conBodyValido_devuelve201ConToken` | Body válido → HTTP 201, body JSON con `token` y `user.email` |
| `register_conEmailInvalido_devuelve400` | Email con formato inválido → HTTP 400 (Bean Validation `@Email`) |
| `register_sinBody_devuelve400` | Sin body → HTTP 400 |

### Grupo: `POST /api/v1/auth/login`

| Método de test | Descripción |
|---|---|
| `login_conCredencialesValidas_devuelve200ConToken` | Credenciales válidas → HTTP 200, body JSON con `token` |
| `login_conEmailInvalido_devuelve400` | Email con formato inválido → HTTP 400 (Bean Validation `@Email`) |

### Grupo: `POST /api/v1/auth/logout`

| Método de test | Descripción |
|---|---|
| `logout_devuelve204SinCuerpo` | Logout stateless → HTTP 204, cuerpo vacío |

---

## Ejecución

```bash
# Todos los tests
./mvnw test

# Solo los tests de servicios
./mvnw test -Dtest="HashServiceTest,JwtServiceTest,AuthServiceTest"

# Solo los tests de controladores
./mvnw test -Dtest="AuthControllerTest"
```
