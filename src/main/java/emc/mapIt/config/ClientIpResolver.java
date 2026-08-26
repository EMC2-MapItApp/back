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
     * @return {@code X-Forwarded-For} (último salto de la cadena) si está presente; si no,
     *         {@code X-Real-IP}; si no, {@code request.getRemoteAddr()}. {@code "127.0.0.1"}
     *         como último recurso si ninguna fuente da un valor usable.
     *         <p>
     *         Se toma el <b>último</b> valor, no el primero: este backend se despliega directo a
     *         Cloud Run sin balanceador/CDN adicional delante (ver {@code deploy.yml}), así que el
     *         único salto de confianza es la GFE de Google, que añade su propia observación de la
     *         IP conectante al final de la cabecera. Los valores anteriores (incluido el primero,
     *         que es el que enviaba el propio cliente) los puede fijar libremente quien hace la
     *         petición — tomarlos permitía saltarse {@link RateLimitFilter} rotando la cabecera.
     */
    public static String resolve(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (!hop.isBlank()) {
                    return hop;
                }
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
