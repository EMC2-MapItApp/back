package emc.mapIt.dto;

/**
 * Resultado normalizado de geolocalización por IP.
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
