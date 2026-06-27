# 📮 Guía de Pruebas - Registro de Usuarios (Postman)

**Fecha:** 2026-06-15  
**Versión:** 1.0.0  
**Proyecto:** MapIt API  

---

## 📋 Tabla de Contenidos

1. [Información General](#información-general)
2. [Configuración Inicial](#configuración-inicial)
3. [Casos de Uso - Registro](#casos-de-uso---registro)
4. [Casos de Error](#casos-de-error)
5. [Checklist de Verificación](#checklist-de-verificación)
6. [Próximos Pasos](#próximos-pasos)

---

## ℹ️ Información General

### Arquitectura de Registro
### Tipos de Usuario

| Tipo | Descripción | Gamificación | Capacidades | Puede Crear Lugares |
|------|-----------|:------------:|:-----------:|:------------------:|
| **INDIVIDUAL** | Usuario personal | ✅ Sí | Básicas (3) | ❌ No |
| **PROFESSIONAL** | Profesional | ❌ No | Ampliadas (6) | ✅ Sí |
| **ENTITY** | Entidad/Empresa | ❌ No | Ampliadas (6) | ✅ Sí |

### Capacidades por Tipo

**INDIVIDUAL (Básicas):**
- `max_publications_1` - máximo 1 publicación
- `weekly_limit_3` - límite 3 por semana
- `basic_search` - búsqueda básica

**PROFESSIONAL/ENTITY (Ampliadas):**
- `max_publications_10` - máximo 10 publicaciones
- `weekly_limit_unlimited` - sin límite semanal
- `advanced_search` - búsqueda avanzada
- `premium_locations` - ubicaciones premium
- `analytics_access` - acceso a analytics
- `priority_support` - soporte prioritario

---

## 🔧 Configuración Inicial

### Variables de Entorno (Postman)

Crear una **Postman Environment** con:

```json
{
  "baseUrl": "http://localhost:8080",
  "apiVersion": "v1",
  "contentType": "application/json"
}
