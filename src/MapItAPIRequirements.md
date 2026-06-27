# MapIt API - Documento de Requisitos

## 📋 Información General

### Descripción del Proyecto
**MapIt** es una API REST backend para una aplicación web de gamificación que conecta usuarios con lugares y eventos locales. El sistema permite tres tipos de usuarios (individuales, profesionales y entidades) interactuar a través de publicaciones gelocalizadas.

### Tecnologías Principales
- **Framework**: Spring Boot 3.3.12
- **Java**: JDK 21
- **Base de Datos**: PostgreSQL con extensión PostGIS
- **ORM**: Hibernate + Spring Data JPA
- **Autenticación**: JWT Bearer Tokens
- **Build Tool**: Maven
- **Packaging**: WAR

---

## 🎯 Objetivos del Sistema

### Funcionalidades Principales
1. **Gestión de Usuarios** con sistema de gamificación
2. **Gestión de Lugares** geolocalizados
3. **Publicación de Eventos y Promociones**
4. **Sistema de Capacidades Desbloqueables**
5. **Hitos y Recompensas XP**
6. **Búsquedas Geoespaciales**

### Usuarios Objetivo
- **Individuos**: Buscan eventos y actividades locales
- **Profesionales**: Promocionan sus servicios y lugares
- **Entidades**: Organizan eventos en lugares específicos

---

## 🏗️ Arquitectura del Sistema

### Estructura del Proyecto

### Base de Datos

#### Entidades Principales
- **users** - Usuarios del sistema
- **places** - Lugares geolocalizados
- **publications** - Eventos y promociones
- **user_milestones** - Hitos completados por usuarios

#### Entidades de Configuración
- **capability_definitions** - Definiciones de capacidades
- **level_definitions** - Definiciones de niveles (0-10)
- **milestone_definitions** - Definiciones de hitos

---

## 👤 Tipos de Usuario y Permisos

### Individual
- **Nivel**: 0-10 (sistema de gamificación)
- **XP**: Puntos de experiencia acumulados
- **Capacidades**: Desbloqueables por nivel o compra
- **Lugares**: No puede crear lugares
- **Publicaciones**: Solo eventos sin lugar fijo
- **Ubicaciones Favoritas**: Lista personalizable

### Profesional
- **Nivel**: No aplica
- **XP**: No aplica
- **Lugares**: Máximo 1 lugar
- **Publicaciones**: Solo promociones vinculadas a su lugar
- **Capacidades**: Todas desbloqueadas por defecto

### Entidad
- **Nivel**: No aplica
- **XP**: No aplica
- **Lugares**: Sin límite
- **Publicaciones**: Solo eventos vinculados a lugares
- **Capacidades**: Todas desbloqueadas por defecto

---

## 🎮 Sistema de Gamificación

### Niveles

### Tipos de Ubicación
- **Sufijo "-profesional"**: Lugares de negocio
- **Sufijo "-quedadas"**: Eventos organizados por usuarios
- **Sufijo "-visita"**: Lugares de interés público

### Metadatos por Tipo
Cada tipo de ubicación tiene campos específicos en formato JSON:
- **Lugares profesionales**: teléfono, web, horarios, servicios
- **Eventos deportivos**: distancia, nivel, participantes
- **Eventos culturales**: precio, url inscripción, aforo

---

## 🔒 Seguridad y Autenticación

### JWT Authentication
- **Login**: POST `/api/v1/auth/login`
- **Registro**: POST `/api/v1/auth/register`
- **Token Validation**: Middleware para rutas protegidas
- **Logout**: POST `/api/v1/auth/logout`

### Autorización por Roles
- **Endpoints públicos**: Categorías, lugares (lectura), publicaciones (lectura)
- **Endpoints autenticados**: Gestión de perfil, creación de contenido
- **Endpoints por rol**: Creación de lugares (solo profesional/entidad)

---

## 🛣️ Endpoints API

### Base URL
### Autenticación
```http
POST   /auth/login
POST   /auth/register
GET    /auth/me
POST   /auth/logout
```
### Usuarios
```http
GET    /users/:id
PATCH  /users/:id
GET    /users/:id/capabilities
POST   /users/:id/capabilities/:capabilityId
GET    /users/:id/milestones
GET    /users/:id/place
GET    /users/:id/publications
```
### Lugares
```http
GET    /places?locationTypeId&lat&lng&radius
GET    /places/:id
POST   /places
PUT    /places/:id
DELETE /places/:id
```
### Publicaciones
```http
GET    /publications?publicationType&locationTypeId&lat&lng&radius
GET    /publications/:id
POST   /publications
PUT    /publications/:id
DELETE /publications/:id
```

### Capacidades
```http
GET    /capabilities
GET    /levels
GET    /milestones
POST   /users/:id/milestones/:milestoneId/complete
```

### Categorías
```http
GET    /categories
GET    /categories/:id
GET    /location-types/:id
```