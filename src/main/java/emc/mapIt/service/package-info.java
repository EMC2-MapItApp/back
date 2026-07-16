/**
 * Lógica de negocio de MapIt: auth y JWT propio, gestión de usuarios/perfil, publicaciones y
 * categorías. Los controllers delegan aquí; los servicios lanzan
 * {@link emc.mapIt.exception.ApiException} para errores de dominio.
 * <p>
 * La geolocalización por IP y el envío de notificaciones viven en módulos propios organizados
 * como arquitectura hexagonal — ver {@link emc.mapIt.geo} y {@link emc.mapIt.notifications}.
 * </p>
 */
package emc.mapIt.service;
