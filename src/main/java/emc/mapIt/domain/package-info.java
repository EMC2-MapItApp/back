/**
 * Modelo de dominio interno usado por la capa de servicio ({@link emc.mapIt.domain.MapItUser}),
 * distinto de los documentos de persistencia en {@link emc.mapIt.entity} — aplana
 * {@code User} + {@code UserProfileDetails} en una sola vista para simplificar la lógica de
 * negocio que no necesita distinguir ambos documentos de Mongo.
 */
package emc.mapIt.domain;
