package emc.mapIt.geo;

import emc.mapIt.config.ClientIpResolver;
import emc.mapIt.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Caso de uso para resolver ubicación aproximada por IP.
 * <p>
 * Responsabilidades:
 * </p>
 * <ul>
 * <li>Resolver IP cliente desde cabeceras proxy o remote address</li>
 * <li>Permitir simulación de IP en entorno de desarrollo</li>
 * <li>Pedir la ubicación al puerto {@link GeoLocationProvider} y aplicar fallback si falla</li>
 * </ul>
 * <p>
 * No sabe nada de HTTP saliente ni de qué proveedor externo hay detrás de
 * {@link GeoLocationProvider} — eso es responsabilidad exclusiva del adaptador
 * ({@link IpApiGeoLocationProvider} hoy). Esta separación es la que permite testear este caso de
 * uso con un mock del puerto, sin red real, y sustituir de proveedor sin tocar esta clase.
 * </p>
 */
@Service
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);

    private final GeoLocationProvider geoLocationProvider;
    private final boolean allowSimIp;
    private final double fallbackLat;
    private final double fallbackLng;

    /**
     * @param geoLocationProvider puerto de resolución de ubicación por IP
     * @param allowSimIp          habilita simulación por query param en desarrollo
     * @param fallbackLat         latitud de fallback
     * @param fallbackLng         longitud de fallback
     */
    public GeoIpService(
            GeoLocationProvider geoLocationProvider,
            @Value("${mapit.geo.allow-sim-ip:false}") boolean allowSimIp,
            @Value("${mapit.geo.fallback-lat:40.4168}") double fallbackLat,
            @Value("${mapit.geo.fallback-lng:-3.7038}") double fallbackLng) {
        this.geoLocationProvider = geoLocationProvider;
        this.allowSimIp = allowSimIp;
        this.fallbackLat = fallbackLat;
        this.fallbackLng = fallbackLng;
    }

    /**
     * Resuelve ubicación aproximada para la conexión actual.
     * <p>
     * Flujo:
     * </p>
     * <ul>
     * <li>Determina la IP efectiva (real o simulada)</li>
     * <li>Pide la ubicación al puerto {@link GeoLocationProvider}</li>
     * <li>Si el puerto no devuelve datos válidos, aplica el fallback configurado</li>
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

        return geoLocationProvider.locate(effectiveIp)
                .map(location -> new GeoIpResponse(
                        location.lat(),
                        location.lng(),
                        location.city(),
                        location.country(),
                        effectiveIp,
                        isSimulated(simIp) ? "simulated" : "geoip"))
                .orElseGet(() -> fallback(effectiveIp));
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
        return ClientIpResolver.resolve(request);
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
     * Construye respuesta fallback cuando el puerto no devuelve datos válidos de GeoIP.
     *
     * @param ip IP usada en la resolución
     * @return respuesta con coordenadas por defecto
     */
    private GeoIpResponse fallback(String ip) {
        return new GeoIpResponse(
                fallbackLat,
                fallbackLng,
                "Madrid",
                "ES",
                ip,
                "fallback");
    }
}
