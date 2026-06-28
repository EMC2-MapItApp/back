package emc.mapIt.service;

import emc.mapIt.dto.GeoIpResponse;
import emc.mapIt.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Servicio de aplicación para resolver ubicación aproximada por IP.
 * <p>
 * Responsabilidades:
 * </p>
 * <ul>
 * <li>Resolver IP cliente desde cabeceras proxy o remote address</li>
 * <li>Permitir simulación de IP en entorno de desarrollo</li>
 * <li>Consultar proveedor GeoIP externo y normalizar la respuesta</li>
 * <li>Aplicar fallback geográfico en caso de fallo externo</li>
 * </ul>
 *
 * @author MapIt Development Team
 * @version 1.0.0
 * @since 2026-06-27
 */
@Service
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);

    private final RestClient restClient;
    private final String providerUrl;
    private final boolean allowSimIp;
    private final double fallbackLat;
    private final double fallbackLng;

    /**
     * Constructor para inyección de dependencias y configuración.
     *
     * @param providerUrl URL base del proveedor GeoIP
     * @param timeoutMs   timeout en milisegundos para llamadas externas
     * @param allowSimIp  habilita simulación por query param en desarrollo
     * @param fallbackLat latitud de fallback
     * @param fallbackLng longitud de fallback
     */
    public GeoIpService(
            @Value("${mapit.geo.provider-url}") String providerUrl,
            @Value("${mapit.geo.timeout-ms:3000}") int timeoutMs,
            @Value("${mapit.geo.allow-sim-ip:false}") boolean allowSimIp,
            @Value("${mapit.geo.fallback-lat:40.4168}") double fallbackLat,
            @Value("${mapit.geo.fallback-lng:-3.7038}") double fallbackLng) {
        this.providerUrl = providerUrl;
        this.allowSimIp = allowSimIp;
        this.fallbackLat = fallbackLat;
        this.fallbackLng = fallbackLng;

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(timeoutMs);
        rf.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(rf)
                .build();
    }

    /**
     * Resuelve ubicación aproximada para la conexión actual.
     * <p>
     * Flujo:
     * </p>
     * <ul>
     * <li>Determina la IP efectiva (real o simulada)</li>
     * <li>Consulta proveedor GeoIP externo</li>
     * <li>Devuelve datos normalizados para el frontend</li>
     * </ul>
     *
     * @param request request HTTP entrante
     * @param simIp   IP simulada opcional (solo válida si allowSimIp=true)
     * @return ubicación normalizada con origen de datos
     * @throws ApiException con código BAD_REQUEST si request es nulo
     */
    public GeoIpResponse resolveLocation(HttpServletRequest request, String simIp) {
        if (request == null) {
            throw new ApiException("BAD_REQUEST", "Request HTTP requerido", HttpStatus.BAD_REQUEST);
        }

        String effectiveIp = resolveEffectiveIp(request, simIp);
        log.info("Resolviendo geo por ip={}", effectiveIp);

        try {
            Map<String, Object> body = restClient.get()
                    .uri(providerUrl + "/" + effectiveIp)
                    .retrieve()
                    .body(Map.class);

            if (body == null) {
                return fallback(effectiveIp, "fallback");
            }

            Object status = body.get("status");
            if (status != null && !"success".equalsIgnoreCase(String.valueOf(status))) {
                log.warn("Proveedor GeoIP devolvio estado no exitoso ip={} status={}", effectiveIp, status);
                return fallback(effectiveIp, "fallback");
            }

            Double lat = toDouble(body.get("lat"));
            Double lng = toDouble(body.get("lon"));
            if (lat == null || lng == null) {
                log.warn("Proveedor GeoIP sin coordenadas ip={}", effectiveIp);
                return fallback(effectiveIp, "fallback");
            }

            String city = stringOrNull(body.get("city"));
            String country = stringOrNull(body.get("countryCode"));

            return new GeoIpResponse(lat, lng, city, country, effectiveIp, isSimulated(simIp) ? "simulated" : "geoip");
        } catch (RestClientException ex) {
            log.warn("Fallo consultando proveedor GeoIP ip={}", effectiveIp, ex);
            return fallback(effectiveIp, "fallback");
        }
    }

    /**
     * Resuelve la IP efectiva a usar en la consulta.
     *
     * @param request request HTTP entrante
     * @param simIp   IP simulada solicitada por cliente
     * @return IP efectiva para geolocalizar
     */
    private String resolveEffectiveIp(HttpServletRequest request, String simIp) {
        if (isSimulated(simIp)) {
            return simIp.trim();
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isBlank())
                return first;
        }

        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }

        String remote = request.getRemoteAddr();
        return (remote == null || remote.isBlank()) ? "127.0.0.1" : remote.trim();
    }

    /**
     * Determina si se permite y aplica simulación de IP.
     *
     * @param simIp IP simulada recibida
     * @return true si debe usarse simulación
     */
    private boolean isSimulated(String simIp) {
        return allowSimIp && simIp != null && !simIp.trim().isEmpty();
    }

    /**
     * Construye respuesta fallback cuando no hay datos válidos de GeoIP.
     *
     * @param ip     IP usada en la resolución
     * @param source origen de la respuesta
     * @return respuesta con coordenadas por defecto
     */
    private GeoIpResponse fallback(String ip, String source) {
        return new GeoIpResponse(
                fallbackLat,
                fallbackLng,
                "Madrid",
                "ES",
                ip,
                source);
    }

    private Double toDouble(Object value) {
        if (value == null)
            return null;
        if (value instanceof Number n)
            return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringOrNull(Object value) {
        if (value == null)
            return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
