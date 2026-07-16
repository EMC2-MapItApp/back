/**
 * Módulo de geolocalización aproximada por IP, organizado como arquitectura hexagonal
 * (puertos y adaptadores): el caso de uso {@link emc.mapIt.geo.GeoIpService} depende
 * únicamente del puerto {@link emc.mapIt.geo.GeoLocationProvider}, no del proveedor externo
 * concreto. Hoy ese puerto lo implementa {@link emc.mapIt.geo.IpApiGeoLocationProvider}
 * (ip-api.com); cambiar de proveedor, o añadir uno de fallback, es sustituir el adaptador sin
 * tocar el caso de uso ni el controller. Ver {@code docs/ARQUITECTURA.md} para el porqué de
 * este límite.
 */
package emc.mapIt.geo;
