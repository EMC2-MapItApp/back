# MapIt API (primer corte)

Backend Spring Boot con endpoints base para autenticacion y usuario, siguiendo `API_SPEC_copilot.md`.

## Endpoints iniciales

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `GET /api/v1/users/{id}`
- `PATCH /api/v1/users/{id}`
- `GET /api/v1/users/{id}/capabilities`
- `POST /api/v1/users/{id}/capabilities/{capabilityId}`
- `GET /api/v1/users/{id}/milestones`
- `GET /api/v1/users/{id}/place`
- `GET /api/v1/users/{id}/publications`
- `GET /api/v1/categories`
- `GET /api/v1/levels`
- `GET /api/v1/capabilities`

## Ejecutar

```bash
./mvnw spring-boot:run
```

## Probar rapido

1. Registrar usuario en `POST /api/v1/auth/register`
2. Copiar el `token` de la respuesta
3. Usarlo como header `Authorization: Bearer <token>` para `GET /api/v1/auth/me`

## Nota

Este primer corte guarda usuarios en memoria (sin base de datos) para validar contrato REST antes de pasar a persistencia PostgreSQL + seguridad completa.
