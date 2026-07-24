# Arquitectura del backend

> Documento vivo. Recoge la decisión de arquitectura para evolucionar el backend a medida que
> crece (nuevos dominios, cliente Android) sin comprometer mantenibilidad. Actualízalo cuando la
> decisión cambie o se ejecute alguno de los "próximos pasos".

## Resumen

**Decisión:** reorganizar el backend como **monolito modular por dominio** (un único
desplegable, módulos con límites claros) en lugar de mantener la organización actual por capa
técnica. **No** dividir en microservicios todavía — el documento fija también los criterios
objetivos para hacerlo el día que aplique.

## Contexto

- Objetivo del proyecto (ver `STACK.md`): aplicación real, mínima y desplegada, que sirva de
  **portfolio** ante reclutadores. Prioriza código idiomático y buenas prácticas de la industria
  sobre sofisticación innecesaria — ya se descartó Kubernetes por la misma razón ("sobrecarga
  operativa" sin necesidad real de escalado).
- Equipo: un desarrollador. Tráfico: el propio de un proyecto personal, sobre infraestructura
  serverless de coste ~0 (Cloud Run + MongoDB Atlas free tier).
- Cliente actual: Angular. Cliente futuro: app nativa Android, que consumirá la misma API REST.
  El uso real de la app es casi siempre desde **dispositivos móviles** (ver regla mobile-first en
  `../../WEB/CLAUDE.md`, sección Arquitectura) — esto no cambia la arquitectura del backend hoy,
  pero es un criterio a tener en cuenta al diseñar payloads y paginación (preferir respuestas
  ligeras, evitar sobre-fetching) si en el futuro se detecta una necesidad real de optimizar para
  redes móviles.
- Estado actual del código (`emc.mapIt`): organización **por capa técnica**
  (`controller/`, `service/`, `repository/`, `entity/`, `dto/`, `mapper/`), sin separación por
  dominio. Funciona bien con el tamaño actual, pero `IDEAS.md` ya anticipa nuevos dominios
  (grupos, notificaciones, visibilidad de publicaciones) que aumentarán el número de clases por
  paquete y el acoplamiento accidental entre dominios no relacionados (p. ej. nada impide hoy que
  un servicio de `publications` llame directamente al repositorio de `User`).

## Decisión: monolito modular por dominio

Mantener un único desplegable (como ahora), pero reorganizar `emc.mapIt` en paquetes por
**dominio de negocio** en vez de por capa técnica. Cada módulo agrupa sus propias
capas (`controller/service/repository/dto/entity/mapper`) y solo expone públicamente lo que
otros módulos necesitan consumir; el resto queda con visibilidad de paquete.

Esto es directamente una aplicación de **SRP** (cada módulo tiene una única razón de negocio
para cambiar) y **DIP** (los módulos se comunican a través de interfaces/servicios públicos, no
de sus repositorios o entidades internas) — coherente con la convención de SOLID que ya fija
`CLAUDE.md`.

### Estructura de paquetes propuesta

Mapeo directo desde las clases existentes, sin cambios de lógica — es un refactor mecánico:

```
emc.mapIt
├── auth/            AuthController, AuthService, JwtService, HashService,
│                     PasswordPolicyService, PasswordResetService, EmailVerificationService,
│                     EmailVerificationToken, PasswordResetToken + sus repos y DTOs,
│                     JwtAuthFilter
├── users/            UserController, UserService, User, UserType, UserProfileDetails,
│                     UserMilestone + repos y DTOs de usuario
├── publications/     PublicationController, PublicationService, Publication, PublicationType,
│                     PublicationEnrollment, Place + repos y DTOs
├── masterdata/        CategoryController, MasterDataController, CategoryCrudService,
│                     MasterDataService, MainCategory, SubCategory, LocationType,
│                     CapabilityDefinition, LevelDefinition, MilestoneDefinition + repos, DTOs
│                     y CategorySeeder
├── geo/              GeoIpController, GeoIpService, GeoIpResponse, puerto GeoLocationProvider +
│                     adaptador IpApiGeoLocationProvider (hexagonal, ver sección siguiente)
├── notifications/    dos puertos hexagonales: NotificationSender (email, adaptador
│                     EmailNotificationSender) y PushSender (push nativo del SO vía VAPID,
│                     adaptador WebPushSender) + NotificationService (orquestador que persiste
│                     el centro in-app y hace fan-out a ambos canales) + Notification/
│                     PushSubscription y sus repos
└── shared/           ApiException, GlobalExceptionHandler, ErrorResponse, CorsConfig,
                      JacksonConfig, MongoConfig, SecurityConfig, StartupLogger, AdminUserSeeder
```

Cada módulo es candidato natural a convertirse en servicio independiente el día que haga falta,
porque ya es dueño exclusivo de sus colecciones Mongo y no comparte entidades con otros módulos.

### API para Angular + Android

No hace falta un BFF ni un API Gateway todavía:

- La API ya está versionada (`/api/v1/...`, ver `CLAUDE.md`) y los DTOs ya están desacoplados de
  las entidades — ambos requisitos para que Android consuma la misma API sin cambios.
- JWT stateless por cabecera `Authorization: Bearer` funciona igual para app nativa que para SPA;
  no depende de cookies de navegador.
- Introducir un BFF solo tendría sentido si Android necesitara agregaciones o formas de
  respuesta distintas de las de Angular (paginación distinta, payloads reducidos para móvil,
  etc.). No lo hay hoy — se puede añadir después sin rediseñar el resto si se detecta esa
  necesidad real.

## Arquitectura hexagonal dentro de los módulos

El monolito modular fija límites *entre* módulos (auth, users, publications...). La
**arquitectura hexagonal** (puertos y adaptadores) es ortogonal a esa decisión, no una
alternativa: fija límites *dentro* de un módulo, entre su lógica de negocio y los detalles de
infraestructura (framework, base de datos, proveedores externos). Se pueden combinar sin
conflicto — cada módulo puede organizarse internamente en hexágono si le aporta algo.

La idea central: el caso de uso no depende de nada externo, solo define interfaces (**puertos**)
que expresan lo que necesita — "resolver una ubicación por IP", "notificar al usuario". Quien
implementa esas interfaces (**adaptadores**) sabe de HTTP, de SMTP, de un proveedor externo
concreto; el caso de uso no.

Llevarlo a todo el proyecto (modelo de dominio separado del documento Mongo, con mappers, para
cada entidad) sería mucho boilerplate para poco beneficio: una sola tecnología de persistencia
sin plan de cambiarla, y dominio mayormente CRUD (categorías, tipos de ubicación, etc.). Sería
la misma sobre-ingeniería que ya se descartó con Kubernetes o los microservicios prematuros.

Se aplica de forma selectiva, donde ya hay o va a haber más de una forma de hacer lo mismo:

- **`geo/`** (implementado) — `GeoIpService` (caso de uso) depende del puerto
  `GeoLocationProvider`; `IpApiGeoLocationProvider` es el adaptador que sabe que el proveedor es
  ip-api.com, su URL y el formato de su respuesta JSON. Cambiar de proveedor, o añadir uno de
  fallback, es sustituir el adaptador sin tocar el caso de uso ni el controller.
- **`notifications/`** (implementado, dos puertos) — `EmailVerificationService` y
  `PasswordResetService` dependen del puerto `NotificationSender` (adaptador
  `EmailNotificationSender`, SMTP), sin cambios desde la Fase 1 de auth. El dominio Grupos añade
  un segundo puerto, `PushSender` (adaptador `WebPushSender`, protocolo Web Push/VAPID) —
  `NotificationService` es el orquestador que, por cada evento de grupo, llama al email existente,
  persiste una `Notification` para el centro in-app y hace fan-out del push a las
  `PushSubscription` del usuario; `GroupService` solo conoce este orquestador, no los canales
  concretos. Camino a Capacitor/FCM/APNs cuando se empaquete la app: un adaptador `PushSender`
  más, no un reemplazo (ver `IDEAS.md`).
- Si algún día se monetiza (no descartado en el contexto del proyecto), una pasarela de pago es
  otro punto natural para aislar el proveedor concreto detrás de un puerto.

Y donde no compensa: entidades simples de solo CRUD (categorías, niveles, capacidades) — ahí el
documento Mongo como modelo de dominio directo es más legible y no hay nada que vaya a cambiar
de infraestructura.

## Cuándo pasar a microservicios (y cuándo no)

Ninguno de estos disparadores aplica hoy a MapIt. Dividir antes de que aparezcan produce más
complejidad operativa (red, despliegues, consistencia eventual) que la que resuelve — y para un
proyecto de portfolio con un único desarrollador, esa complejidad no aporta valor real, incluso
puede jugar en contra si un revisor técnico la lee como sobre-ingeniería no justificada.

Señales concretas de que sí compensa extraer un módulo como servicio propio:

1. **Escalado independiente real** — un módulo tiene un patrón de carga muy distinto al resto y
   escalar todo el monolito para cubrirlo es ineficiente.
2. **Despliegue independiente necesario** — necesitas desplegar un módulo con más frecuencia o
   más riesgo que el resto sin arriesgar dominios estables (p. ej. iterar rápido en
   `publications` sin tocar `auth`).
3. **Aislamiento de fallos** — un módulo depende de un servicio externo lento/inestable y no debe
   poder arrastrar al resto de la aplicación si falla.
4. **Más de un equipo** — varios desarrolladores necesitan desplegar de forma autónoma sin
   bloquearse entre sí. Con un único desarrollador esto no aplica.
5. **Heterogeneidad tecnológica real** — un dominio se beneficia claramente de otro
   lenguaje/runtime/base de datos que Spring + Mongo no cubre bien.
6. **Límites de dominio ya maduros y estables** — llevas tiempo sin mover responsabilidades entre
   módulos. Extraer límites que todavía se mueven produce un "monolito distribuido" (peor que el
   monolito: toda la complejidad de red sin la independencia real).
7. **El propio monolito empieza a doler** — build, tests o despliegue se vuelven lentos por
   tamaño, no por diseño.

Mientras ninguno de estos puntos sea cierto, el monolito modular es la opción correcta — y sigue
siendo un buen argumento de entrevista: "diseñé los límites de dominio para que la extracción a
servicios fuera mecánica el día que hiciera falta, documenté los criterios, y decidí
deliberadamente no hacerlo antes de tiempo."

## Opciones consideradas

| Dimensión | A. Monolito modular (elegida) | B. Microservicios ya | C. Mantener capas técnicas actuales |
|---|---|---|---|
| Complejidad operativa | Baja — un despliegue, como hoy | Alta — red, service discovery, observabilidad distribuida | Baja |
| Coste | Igual que hoy (Cloud Run free tier) | Sube (múltiples servicios/DBs) | Igual que hoy |
| Preparación para Android | Alta — misma API, sin cambios | Alta, pero no es la razón para dividir | Alta, pero degradará con el tiempo |
| Mantenibilidad a 6-12 meses | Alta — límites claros, fácil de testear por módulo | Alta en teoría, pero prematura para el tamaño actual | Baja — paquetes técnicos crecen sin límite y se acoplan |
| Encaje con 1 desarrollador | Bueno | Malo — coordinación/operación que no hace falta | Bueno a corto plazo, se degrada |
| Señal de portfolio | Buena — demuestra criterio, no solo conocimiento de patrones | Puede leerse como sobre-ingeniería sin justificar | Neutra, no demuestra evolución del diseño |

## Consecuencias

- **Más fácil:** añadir funcionalidad dentro de límites claros (grupos, notificaciones,
  visibilidad de publicaciones de `IDEAS.md` encajan directamente como módulos nuevos o
  extensiones de los existentes); testear por módulo; extraer un módulo a servicio propio el día
  que un criterio de la sección anterior se cumpla.
- **Más difícil al principio:** requiere mover clases de paquete (sin tocar lógica) y disciplina
  para no dejar que un módulo llame directamente al repositorio/entidad interna de otro — un
  test de arquitectura (ArchUnit) puede automatizar esa regla si se quiere reforzar.
- **Revisar esta decisión cuando:** se cumpla alguno de los siete criterios de la sección
  anterior, o cuando la integración de Android revele una necesidad real de agregación de
  respuestas (candidato a introducir un BFF).

## Próximos pasos

1. ~~Extraer `geo/` y `notifications/` como primer piloto (puerto + adaptador,
   arquitectura hexagonal).~~ Hecho — ver `emc.mapIt.geo` y `emc.mapIt.notifications`.
2. Reorganizar el resto de `emc.mapIt` según la estructura propuesta (auth, users,
   publications, masterdata, shared) — refactor mecánico, sin cambio de comportamiento; cubierto
   por los tests existentes.
3. Revisar qué queda expuesto como API pública de cada módulo frente a lo que debería quedar de
   visibilidad de paquete.
4. (Opcional) Añadir un test de arquitectura que falle si aparecen dependencias cruzadas
   indebidas entre módulos.
5. (Opcional) Añadir tests unitarios dedicados para `GeoIpService` (mockeando
   `GeoLocationProvider`) y `IpApiGeoLocationProvider` — hoy sin cobertura propia, y el punto
   donde el puerto/adaptador demuestra mejor su valor: testear el caso de uso sin red real.
6. Mantener la API versionada (`/api/v1`) como contrato estable antes de arrancar el cliente
   Android.
