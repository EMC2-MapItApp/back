package emc.mapIt.geo;

/**
 * Modelo de dominio de una ubicación geográfica aproximada.
 * <p>
 * Deliberadamente no depende de la forma de respuesta de ningún proveedor externo concreto
 * (ver {@link GeoLocationProvider}) ni del DTO expuesto por la API ({@link GeoIpResponse}) —
 * {@link GeoIpService} traduce entre ambos. Esto es lo que permite sustituir el proveedor de
 * geolocalización sin que el cambio se filtre ni al caso de uso ni al contrato de la API.
 * </p>
 *
 * @param lat     latitud en grados decimales
 * @param lng     longitud en grados decimales
 * @param city    ciudad aproximada asociada a la IP, si el proveedor la conoce
 * @param country país o código ISO del país, si el proveedor lo conoce
 */
public record GeoLocation(double lat, double lng, String city, String country) {
}
