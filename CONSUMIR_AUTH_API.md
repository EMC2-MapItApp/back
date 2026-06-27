# 🔑 Guía de Consumo - AuthController API

**Versión:** 1.0.0  
**Fecha:** 2026-06-15  
**Proyecto:** MapIt API  
**Base Path:** `/api/v1/auth`

---

## 📋 Tabla de Contenidos

1. [Introducción](#introducción)
2. [Autenticación](#autenticación)
3. [Endpoints](#endpoints)
4. [Modelos de Datos](#modelos-de-datos)
5. [Ejemplos de Integración](#ejemplos-de-integración)
6. [Códigos de Respuesta](#códigos-de-respuesta)
7. [Errores Comunes](#errores-comunes)
8. [Flujos Típicos](#flujos-típicos)

---

## 🎯 Introducción

El controlador `AuthController` expone 4 endpoints para gestionar:
- **Registro** de nuevos usuarios
- **Login** (autenticación)
- **Consulta de perfil autenticado**
- **Logout** (cierre de sesión lógico)

Todos los endpoints responden en JSON y utilizan **JWT (JSON Web Tokens)** para autenticación.

### Características Clave

- ✅ Tokens JWT sin expiración configurable
- ✅ Normalización automática de email (trim + lowercase)
- ✅ Hash de contraseña SHA-256
- ✅ Validación de datos en capas (DTO + Service)
- ✅ Mensajes de error descriptivos

---

## 🔐 Autenticación

### Esquema JWT Bearer

La mayoría de endpoints requieren autenticación mediante **Bearer Token**.

**Header Requerido:**
```
Authorization: Bearer <tu_token_jwt>
```

**Ejemplo:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJpYXQiOjE2NDY4NDcwMDB9.signature...
```

### Obtener Token

1. Registrate con `POST /auth/register` o
2. Ingresa con `POST /auth/login`

Ambos devuelven un token JWT en la respuesta.

### Validar Token

Decodifica el JWT en [jwt.io](https://jwt.io) para ver:
- `sub`: ID del usuario (UUID)
- `iat`: Timestamp de emisión
- `exp`: Timestamp de expiración (si aplica)

---

## 📡 Endpoints

### 1. Registrar Usuario

Crear una nueva cuenta de usuario en la plataforma.

#### Request

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "name": "Juan García",
  "email": "juan@example.com",
  "password": "SecurePass123",
  "userType": "INDIVIDUAL"
}
```

#### Parámetros

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|:-------:|:----------:|-------------|
| `name` | string | ✅ | max 120 caracteres, no en blanco | Nombre del usuario |
| `email` | string | ✅ | válido, no en blanco, único | Email (case-insensitive en BD) |
| `password` | string | ✅ | 6-120 caracteres | Contraseña (hasheada en BD) |
| `userType` | enum | ✅ | INDIVIDUAL, PROFESSIONAL, ENTITY | Tipo de usuario |

#### Response (201 Created)

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Juan García",
    "email": "juan@example.com",
    "userType": "INDIVIDUAL",
    "level": 0,
    "xp": 0,
    "unlockedCapabilities": [
      "max_publications_1",
      "weekly_limit_3",
      "basic_search"
    ],
    "favoriteLocationTypeIds": [],
    "avatarUrl": null
  }
}
```

#### Posibles Códigos de Error

| Status | Código | Mensaje | Causa |
|--------|--------|---------|-------|
| `400` | `VALIDATION_ERROR` | Error en validación de campos | Email inválido, password corta, etc. |
| `409` | `CONFLICT` | Ya existe un usuario con ese email | Email duplicado |

---

### 2. Login

Autenticar un usuario existente y obtener token JWT.

#### Request

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "juan@example.com",
  "password": "SecurePass123"
}
```

#### Parámetros

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|:-------:|:----------:|-------------|
| `email` | string | ✅ | válido, no en blanco | Email del usuario |
| `password` | string | ✅ | no en blanco | Contraseña en texto plano |

#### Response (200 OK)

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Juan García",
    "email": "juan@example.com",
    "userType": "INDIVIDUAL",
    "level": 0,
    "xp": 0,
    "unlockedCapabilities": [
      "max_publications_1",
      "weekly_limit_3",
      "basic_search"
    ],
    "favoriteLocationTypeIds": [],
    "avatarUrl": null
  }
}
```

#### Posibles Códigos de Error

| Status | Código | Mensaje | Causa |
|--------|--------|---------|-------|
| `400` | `VALIDATION_ERROR` | Error en validación | Email/password vacíos |
| `401` | `UNAUTHORIZED` | Credenciales inválidas | Email no existe o password incorrecta |

---

### 3. Obtener Perfil Actual

Consultar los datos del usuario autenticado.

#### Request

```http
GET /api/v1/auth/me
Authorization: Bearer <token>
```

#### Parámetros

**Headers:**
| Header | Requerido | Valor |
|--------|:-------:|--------|
| `Authorization` | ✅ | `Bearer <tu_token_jwt>` |

#### Response (200 OK)

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Juan García",
  "email": "juan@example.com",
  "userType": "INDIVIDUAL",
  "level": 0,
  "xp": 0,
  "unlockedCapabilities": [
    "max_publications_1",
    "weekly_limit_3",
    "basic_search"
  ],
  "favoriteLocationTypeIds": [],
  "avatarUrl": null
}
```

#### Posibles Códigos de Error

| Status | Código | Mensaje | Causa |
|--------|--------|---------|-------|
| `401` | `UNAUTHORIZED` | Token inválido/expirado | Token ausente, inválido o expirado |
| `404` | `NOT_FOUND` | Usuario no encontrado | Usuari ID en token no existe en BD |

---

### 4. Logout

Cierra sesión (operación lógica en arquitectura stateless).

#### Request

```http
POST /api/v1/auth/logout
```

#### Response (204 No Content)

```
(respuesta vacía)
```

**Nota:** En arquitectura JWT stateless, simplemente descartas el token en el cliente. El servidor no invalida nada.

---

## 📦 Modelos de Datos

### Request: AuthRegisterRequest

```typescript
interface AuthRegisterRequest {
  name: string;           // 1-120 caracteres, @NotBlank
  email: string;          // válido, @Email, único
  password: string;       // 6-120 caracteres
  userType: UserType;     // INDIVIDUAL | PROFESSIONAL | ENTITY
}
```

### Request: AuthLoginRequest

```typescript
interface AuthLoginRequest {
  email: string;          // válido, @Email
  password: string;       // no en blanco
}
```

### Response: AuthResponse

```typescript
interface AuthResponse {
  token: string;          // JWT Bearer Token
  user: MapItUserResponse; // Datos del usuario
}
```

### Response: MapItUserResponse

```typescript
interface MapItUserResponse {
  id: string;                           // UUID
  name: string;                         // Nombre del usuario
  email: string;                        // Email normalizado (lowercase)
  userType: "INDIVIDUAL" | "PROFESSIONAL" | "ENTITY";
  level: number | null;                 // 0-10 para INDIVIDUAL, null para otros
  xp: number | null;                    // para INDIVIDUAL, null para otros
  unlockedCapabilities: string[];       // Array de capability IDs
  favoriteLocationTypeIds: string[];    // Solo para INDIVIDUAL
  avatarUrl: string | null;             // URL de avatar o null
}
```

### UserType Enum

```typescript
enum UserType {
  INDIVIDUAL = "INDIVIDUAL",        // Usuario personal (con gamificación)
  PROFESSIONAL = "PROFESSIONAL",    // Profesional (sin gamificación)
  ENTITY = "ENTITY"                 // Entidad/empresa (sin gamificación)
}
```

---

## 💻 Ejemplos de Integración

### Postman

#### Importar colección (v2.1)

Guarda este JSON como `MapIt-Auth.postman_collection.json` e impórtalo en Postman:

```json
{
  "info": {
    "name": "MapIt Auth API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Register",
      "request": {
        "method": "POST",
        "header": [
          { "key": "Content-Type", "value": "application/json" }
        ],
        "url": "{{baseUrl}}/api/v1/auth/register",
        "body": {
          "mode": "raw",
          "raw": "{\n  \"name\": \"Juan Garcia\",\n  \"email\": \"juan@example.com\",\n  \"password\": \"SecurePass123\",\n  \"userType\": \"INDIVIDUAL\"\n}"
        }
      },
      "event": [
        {
          "listen": "test",
          "script": {
            "exec": [
              "pm.test('Status 201', function () { pm.response.to.have.status(201); });",
              "const json = pm.response.json();",
              "pm.environment.set('authToken', json.token);",
              "pm.environment.set('userId', json.user.id);"
            ],
            "type": "text/javascript"
          }
        }
      ]
    },
    {
      "name": "Login",
      "request": {
        "method": "POST",
        "header": [
          { "key": "Content-Type", "value": "application/json" }
        ],
        "url": "{{baseUrl}}/api/v1/auth/login",
        "body": {
          "mode": "raw",
          "raw": "{\n  \"email\": \"juan@example.com\",\n  \"password\": \"SecurePass123\"\n}"
        }
      },
      "event": [
        {
          "listen": "test",
          "script": {
            "exec": [
              "pm.test('Status 200', function () { pm.response.to.have.status(200); });",
              "const json = pm.response.json();",
              "pm.environment.set('authToken', json.token);",
              "pm.environment.set('userId', json.user.id);"
            ],
            "type": "text/javascript"
          }
        }
      ]
    },
    {
      "name": "Me",
      "request": {
        "method": "GET",
        "header": [
          { "key": "Authorization", "value": "Bearer {{authToken}}" }
        ],
        "url": "{{baseUrl}}/api/v1/auth/me"
      }
    },
    {
      "name": "Logout",
      "request": {
        "method": "POST",
        "url": "{{baseUrl}}/api/v1/auth/logout"
      }
    }
  ]
}
```

#### Environment recomendado

```json
{
  "baseUrl": "http://localhost:8080",
  "authToken": "",
  "userId": ""
}
```

#### Flujo rápido en Postman

1. Ejecuta `Register` o `Login`.
2. Verifica que el script guarde `authToken` en environment.
3. Ejecuta `Me` para validar token.
4. Ejecuta `Logout` y limpia `authToken` manualmente si tu app lo requiere.

---

### Angular 22

> Ejemplo con standalone APIs, `HttpClient`, señales e interceptor funcional (`HttpInterceptorFn`).

#### 1) Modelos (`src/app/core/auth/auth.models.ts`)

```typescript
export type UserType = 'INDIVIDUAL' | 'PROFESSIONAL' | 'ENTITY';

export interface AuthRegisterRequest {
  name: string;
  email: string;
  password: string;
  userType: UserType;
}

export interface AuthLoginRequest {
  email: string;
  password: string;
}

export interface MapItUserResponse {
  id: string;
  name: string;
  email: string;
  userType: UserType;
  level: number | null;
  xp: number | null;
  unlockedCapabilities: string[];
  favoriteLocationTypeIds: string[];
  avatarUrl: string | null;
}

export interface AuthResponse {
  token: string;
  user: MapItUserResponse;
}
```

#### 2) Config API (`src/environments/environment.ts`)

```typescript
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api/v1'
};
```

#### 3) Servicio (`src/app/core/auth/auth.service.ts`)

```typescript
import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthLoginRequest,
  AuthRegisterRequest,
  AuthResponse,
  MapItUserResponse
} from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;
  readonly token = signal<string | null>(localStorage.getItem('authToken'));
  readonly currentUser = signal<MapItUserResponse | null>(null);

  constructor(private readonly http: HttpClient) {}

  register(payload: AuthRegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/register`, payload).pipe(
      tap((res) => this.setSession(res))
    );
  }

  login(payload: AuthLoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, payload).pipe(
      tap((res) => this.setSession(res))
    );
  }

  me(): Observable<MapItUserResponse> {
    return this.http.get<MapItUserResponse>(`${this.baseUrl}/me`).pipe(
      tap((user) => this.currentUser.set(user))
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, {}).pipe(
      tap(() => this.clearSession())
    );
  }

  clearSession(): void {
    localStorage.removeItem('authToken');
    this.token.set(null);
    this.currentUser.set(null);
  }

  private setSession(res: AuthResponse): void {
    localStorage.setItem('authToken', res.token);
    this.token.set(res.token);
    this.currentUser.set(res.user);
  }
}
```

#### 4) Interceptor JWT (`src/app/core/auth/auth.interceptor.ts`)

```typescript
import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('authToken');

  if (!token) {
    return next(req);
  }

  const cloned = req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`
    }
  });

  return next(cloned);
};
```

#### 5) Registro en `app.config.ts`

```typescript
import { ApplicationConfig } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './app/core/auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
};
```

#### 6) Uso desde componente standalone

```typescript
import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()">
      <input type="email" formControlName="email" placeholder="Email" />
      <input type="password" formControlName="password" placeholder="Password" />
      <button type="submit">Entrar</button>
    </form>
  `
})
export class LoginPageComponent {
  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService
  ) {}

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.authService.login(this.form.getRawValue() as { email: string; password: string }).subscribe({
      next: () => this.authService.me().subscribe(),
      error: (err) => console.error('Login error', err)
    });
  }
}
```

---

## 🔄 Flujos Típicos

### Flujo 1: Registro desde Postman

1. Ejecuta `POST /auth/register` con body JSON.
2. Guarda `token` en `authToken` (script de test).
3. Ejecuta `GET /auth/me` con `Bearer {{authToken}}`.
4. Verifica `id`, `userType`, `capabilities`.

---

### Flujo 2: Login en Angular 22

```typescript
this.authService.login({
  email: 'juan@example.com',
  password: 'SecurePass123'
}).subscribe({
  next: () => {
    this.authService.me().subscribe();
  },
  error: (err) => {
    // manejar 401/400
    console.error(err);
  }
});
```

---

### Flujo 3: Consumo protegido con interceptor Angular

```typescript
// Con interceptor activo, no necesitas setear Authorization manualmente.
this.authService.me().subscribe({
  next: (profile) => console.log(profile),
  error: (err) => {
    if (err.status === 401) {
      this.authService.clearSession();
    }
  }
});
```

---

### Flujo 4: Logout en Angular 22

```typescript
this.authService.logout().subscribe({
  next: () => {
    // Redirigir a login si aplica
  },
  error: () => {
    // Fallback: limpiar sesion local
    this.authService.clearSession();
  }
});
```

---

## 🔗 Referencias

### Links Útiles

- **JWT Decoder:** [jwt.io](https://jwt.io)
- **RFC 7519 (JWT):** [tools.ietf.org/html/rfc7519](https://tools.ietf.org/html/rfc7519)
- **Spring Security:** [spring.io/projects/spring-security](https://spring.io/projects/spring-security)

### Archivos Relacionados

- `REGISTRO_USUARIOS_POSTMAN.md` - Pruebas detalladas con Postman
- `API_SPEC_copilot.md` - Especificación completa de API
- `AuthService.java` - Lógica de autenticación
- `AuthController.java` - Controlador REST

---

**Versión:** 1.0.0  
**Última actualización:** 2026-06-15  
**Autor:** MapIt Development Team
