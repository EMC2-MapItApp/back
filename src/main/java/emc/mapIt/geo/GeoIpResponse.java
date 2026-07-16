package emc.mapIt.geo;

/**
 * Resultado normalizado de geolocalización por IP, expuesto por la API. Se construye a partir
 * del modelo de dominio {@link GeoLocation} más metadatos propios del caso de uso (IP resuelta,
 * origen del dato) que el puerto {@link GeoLocationProvider} no conoce.
 *
 * @param lat        latitud en grados decimales
 * @param lng        longitud en grados decimales
 * @param city       ciudad aproximada asociada a la IP
 * @param country    país o código ISO del país
 * @param resolvedIp IP finalmente usada en la consulta
 * @param source     origen del resultado: geoip, simulated o fallback
 */
public record GeoIpResponse(
        double lat,
        double lng,
        String city,
        String country,
        String resolvedIp,
        String source) {
}
