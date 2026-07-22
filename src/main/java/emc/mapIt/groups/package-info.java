/**
 * Módulo de Grupos (ver {@code docs/back/IDEAS.md} → sección Grupos): usuarios agrupados en
 * torno a un tema (una {@link emc.mapIt.entity.MainCategory} del árbol de categorías del mapa,
 * reutilizada aquí para no duplicar vocabulario), con invitaciones por email a usuarios ya
 * existentes en MapIt.
 * <p>
 * Primer piloto del "monolito modular por dominio" descrito en {@code docs/ARQUITECTURA.md}:
 * paquete plano (sin subpaquetes por capa técnica), igual que {@link emc.mapIt.geo} y
 * {@link emc.mapIt.notifications}. Depende de {@link emc.mapIt.service.UserService} (API
 * pública del dominio de usuarios) para resolver/validar usuarios — nunca de
 * {@link emc.mapIt.repository.UserRepository} directamente — y del puerto
 * {@link emc.mapIt.notifications.NotificationSender} para el envío de emails de invitación y
 * aviso al organizador, sin conocer el canal de entrega concreto.
 * </p>
 */
package emc.mapIt.groups;
