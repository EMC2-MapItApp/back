package emc.mapIt.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resuelve la IP real del cliente detrás del proxy/balanceador de Cloud Run. Compartido por
 * {@code emc.mapIt.geo.GeoIpService} (geolocalización) y {@link RateLimitFilter} (contador por
 * IP) — antes duplicado en {@code GeoIpService}.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * @param request request HTTP entrante
     * @return {@code X-Forwarded-For} (primer salto de la cadena, el cliente original) si está
     *         presente; si no, {@code X-Real-IP}; si no, {@code request.getRemoteAddr()}.
     *         {@code "127.0.0.1"} como último recurso si ninguna fuente da un valor usable.
     */
    public static String resolve(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }

        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }

        String remote = request.getRemoteAddr();
        return (remote == null || remote.isBlank()) ? "127.0.0.1" : remote.trim();
    }
}
