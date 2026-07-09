# MapIt — Especificación completa de API REST y Base de Datos

> Generado desde el código fuente del frontend Angular.
> Versión: 1.5 | Fecha: 2026-06-25

---

## Índice

1. [Visión general](#1-visión-general)
2. [Stack recomendado](#2-stack-recomendado)
3. [Esquema de base de datos](#3-esquema-de-base-de-datos)
4. [Autenticación y JWT](#4-autenticación-y-jwt)
5. [Endpoints — Auth](#5-endpoints--auth)
6. [Endpoints — Users](#6-endpoints--users)
7. [Endpoints — Categories](#7-endpoints--categories)
8. [Endpoints — Places](#8-endpoints--places)
9. [Endpoints — Publications](#9-endpoints--publications)
10. [Endpoints — Gamificación](#10-endpoints--gamificación)
11. [Reglas de negocio](#11-reglas-de-negocio)
12. [Schemas de metadata JSONB](#12-schemas-de-metadata-jsonb)
13. [Seed data de categorías](#13-seed-data-de-categorías)
14. [Ejemplos con Postman](#14-ejemplos-con-postman)

---

## 1. Visión general

MapIt es una plataforma de mapa social donde los usuarios publican **lugares permanentes** y **eventos/promociones temporales** geolocalizados. Existen cuatro tipos de usuario:

| Tipo | Backend enum | Descripción |
|------|-------------|-------------|
| `individual` | `PARTICULAR` | Usuario gamificado. Publica eventos sin sede fija. |
| `professional` | `PROFESSIONAL` | Negocio o autónomo. 1 sede (Place) + N promociones sobre ella. |
| `entity` | `ENTITY` | Entidad/asociación. 1 sede (Place) + N eventos sobre ella. |
| `admin` | `ADMIN` | Administrador del sistema con permisos totales. |

> **Nota de mapeo:** El frontend envía `PARTICULAR`, `PROFESSIONAL`, `ENTITY` al registrar. La API almacena internamente en minúsculas: `individual`, `professional`, `entity`, `admin`.

---

## 2. Stack recomendado
... (contenido sin cambios) ...

---

## 3. Esquema de base de datos

### 3.1 `users`

```sql
CREATE TABLE users (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name                       VARCHAR(100)  NOT NULL,
  email                      VARCHAR(255)  NOT NULL UNIQUE,
  password_hash              VARCHAR(255)  NOT NULL,
  user_type                  VARCHAR(20)   NOT NULL CHECK (user_type IN ('individual','professional','entity','admin')),
  level                      SMALLINT      DEFAULT 0,       -- solo individual (0-10)
  xp                         INTEGER       DEFAULT 0,       -- solo individual
  unlocked_capabilities      TEXT[]        NOT NULL DEFAULT '{}',
  avatar_url                 TEXT,
  phone                      VARCHAR(20),
  city                       VARCHAR(100),
  province                   VARCHAR(100),
  bio                        TEXT,
  birth_date                 DATE,
  favorite_location_type_ids TEXT[]        NOT NULL DEFAULT '{}', -- solo individual
  created_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
```
... (resto del documento sin cambios) ...
