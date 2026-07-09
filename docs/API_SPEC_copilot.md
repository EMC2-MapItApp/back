---

## VERSIÓN 2 — Instrucciones para Copilot (fichero `.github/copilot-instructions.md` o instrucción de sistema del IDE)

```markdown
# MapIt API — Copilot Instructions

## Project
REST API backend for the MapIt Angular frontend.
Base URL: `/api/v1`. Auth: JWT Bearer token.

## Core entities and their DB tables

### users
- id: UUID (PK)
- name: string
- email: string (unique)
- password_hash: string
- user_type: ENUM('individual', 'professional', 'entity')
- level: int (0–10, null for professional/entity)
- xp: int (null for professional/entity)
- unlocked_capabilities: JSONB (string[])
- favorite_location_type_ids: JSONB (string[], individual only)
- avatar_url: string nullable

### places
- id: SERIAL (PK)
- owner_id: UUID FK → users.id
- name: string
- description: string nullable
- location_type_id: string FK → location_types.id
- lat: decimal
- lng: decimal
- address: string nullable
- metadata: JSONB

### publications
- id: SERIAL (PK)
- author_id: UUID FK → users.id
- publication_type: ENUM('promotion', 'event')
- place_id: int nullable FK → places.id
- location_type_id: string
- title: string
- description: string nullable
- start_date: timestamptz
- end_date: timestamptz nullable
- lat: decimal nullable
- lng: decimal nullable
- required_level: int default 0
- metadata: JSONB

### categories / subcategories / location_types
- Hierarchical: MainCategory → SubCategory → LocationType
- All IDs are slugs (e.g. 'deportes', 'ciclismo', 'ciclismo-quedadas')
- location_types ending in '-profesional' are professional types
- location_types ending in '-quedadas' or '-visita' are individual/entity types

### capability_definitions
- id: string (e.g. 'max_publications_5')
- label, description: string
- unlocks_at_level: int nullable
- purchasable: boolean
- price_eur: decimal nullable

### level_definitions
- level: int (0–10, PK)
- label, perk_description: string
- required_xp: int → [0,100,250,450,700,1000,1400,1900,2500,3200,4000]

### milestone_definitions
- id: string
- description: string
- xp_reward: int
- condition_type: string
- condition_value: int nullable

### user_milestones
- user_id: UUID FK → users.id
- milestone_id: string FK → milestone_definitions.id
- completed_at: timestamptz
- PRIMARY KEY (user_id, milestone_id)

## Business rules (enforce in service layer)

- professional/entity: max 1 place per user → 409 on second attempt
- individual: cannot create a place → 403
- professional: publication_type must be 'promotion' → 422 otherwise
- individual: publication_type must be 'event', place_id must be null, lat/lng required
- entity: publication_type must be 'event', place_id required, lat/lng resolved from place
- end_date = null means indefinite promotion (only for promotion type)
- required_level = 0 means visible to all including anonymous users
- location_type_id ending in '-profesional' requires user level >= 10 to be added to favorites
- level is derived from xp using level_definitions table, never set directly
- validate unlocked_capabilities before creating publication:
- check max_publications_X (count active publications)
- check weekly_limit_X (count publications created this week)

## API endpoints to implement

### Auth
POST   /api/v1/auth/login        → { token, user: MapItUser }
POST   /api/v1/auth/register     → { token, user: MapItUser }
GET    /api/v1/auth/me           → MapItUser (from JWT)
POST   /api/v1/auth/logout

### Users
GET    /api/v1/users/:id
PATCH  /api/v1/users/:id         → partial update (name, avatarUrl, favoriteLocationTypeIds)
GET    /api/v1/users/:id/capabilities
POST   /api/v1/users/:id/capabilities/:capabilityId   → unlock via micropayment
GET    /api/v1/users/:id/milestones
GET    /api/v1/users/:id/place
GET    /api/v1/users/:id/publications

### Categories
GET    /api/v1/categories        → MainCategory[] with nested subcategories and locationTypes
GET    /api/v1/categories/:id
GET    /api/v1/location-types/:id

### Places
GET    /api/v1/places            → query: locationTypeId, lat, lng, radius(meters)
GET    /api/v1/places/:id
POST   /api/v1/places            → auth required, professional/entity only
PUT    /api/v1/places/:id        → auth required, owner only
DELETE /api/v1/places/:id        → auth required, owner only

### Publications
GET    /api/v1/publications      → query: publicationType, locationTypeId, placeId,
authorId, lat, lng, radius, active(bool), requiredLevel
GET    /api/v1/publications/:id
POST   /api/v1/publications      → auth required
PUT    /api/v1/publications/:id  → auth required, author only
DELETE /api/v1/publications/:id  → auth required, author only

### Gamification
GET    /api/v1/capabilities       → CapabilityDefinition[]
GET    /api/v1/levels             → LevelDefinition[]
GET    /api/v1/milestones         → MilestoneDefinition[]
GET    /api/v1/users/:id/milestones
POST   /api/v1/users/:id/milestones/:milestoneId/complete

## metadata JSONB fields by locationTypeId

### context: place
ciclismo-profesional     → phone, web, schedule, address, services
running-profesional      → phone, web, schedule, address
natacion-profesional     → phone, web, schedule, address, lanePrice(number €)
museos-visita            → phone, web, schedule, address, admissionFee(number €), free(boolean)
museos-profesional       → phone, web, schedule, address
musica-profesional       → phone, web, schedule, address
teatro-profesional       → phone, web, schedule, address
restaurantes-profesional → phone, web, schedule, address, avgPrice(number €), cuisine(string), booking(boolean)
bares-profesional        → phone, web, schedule, address
senderismo-profesional   → phone, web, schedule, address
playas-profesional       → phone, web, schedule, address
gaming-profesional       → phone, web, schedule, address
maker-profesional        → phone, web, schedule, address
idiomas-profesional      → phone, web, schedule, address
formacion-profesional    → phone, web, schedule, address

### context: promotion
*-profesional (all)      → discountCode(string), discountPercent(number %), conditions(string), maxUses(number, optional)

### context: event
ciclismo-quedadas        → distance(km), elevation(m), level('Fácil'|'Medio'|'Difícil'), slots(int), contact(phone)
running-quedadas         → distance(km), level('Fácil'|'Medio'|'Difícil'), slots(int), contact(phone)
futbol-quedadas          → slots(int, jugadores que faltan), contact(phone)
natacion-quedadas        → slots(int), contact(phone)
museos-visita            → slots(int), price(number €), registrationUrl(url)
museos-profesional       → slots(int), price(number €), registrationUrl(url)
musica-quedadas          → slots(int), price(number €), contact(phone)
teatro-quedadas          → slots(int), price(number €), contact(phone)
restaurantes-quedadas    → slots(int), avgPrice(number €/persona), contact(phone)
bares-quedadas           → slots(int), contact(phone)
senderismo-quedadas      → distance(km), elevation(m), level('Fácil'|'Medio'|'Difícil'), slots(int), contact(phone)
playas-quedadas          → slots(int), contact(phone)
parques-quedadas         → slots(int), contact(phone)
gaming-quedadas          → slots(int), price(number €), isOnline(boolean), contact(phone)
maker-quedadas           → slots(int), price(number €), isOnline(boolean), contact(phone)
idiomas-quedadas         → language(string), level('A1'|'A2'|'B1'|'B2'|'C1'|'C2'), slots(int), isOnline(boolean), contact(phone)
formacion-quedadas       → slots(int), price(number €), isOnline(boolean), contact(phone)

## Error response format
{
"error": {
"code": "string (e.g. UNAUTHORIZED, NOT_FOUND, CONFLICT, UNPROCESSABLE)",
"message": "string",
"status": 400|401|403|404|409|422
}
}

## Valid locationTypeId slugs
deportes: ciclismo-quedadas, ciclismo-profesional, running-quedadas, running-profesional,
futbol-quedadas, futbol-profesional, natacion-quedadas, natacion-profesional
cultura:  museos-visita, museos-profesional, musica-quedadas, musica-profesional,
teatro-quedadas, teatro-profesional
gastronomia: restaurantes-quedadas, restaurantes-profesional, bares-quedadas, bares-profesional,
mercados-visita, mercados-profesional
naturaleza: senderismo-quedadas, senderismo-profesional, playas-quedadas, playas-profesional,
parques-quedadas, parques-visita
tecnologia: gaming-quedadas, gaming-profesional, maker-quedadas, maker-profesional
educacion:  idiomas-quedadas, idiomas-profesional, formacion-quedadas, formacion-profesional