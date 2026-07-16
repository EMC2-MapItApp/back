package emc.mapIt.geo;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para resolución de ubicación geográfica por IP.
 * <p>
 * Expone un único endpoint público que permite al frontend obtener
 * coordenadas aproximadas basadas en la IP de la conexión, sin que
 * el cliente necesite conocer ni gestionar su propia IP.
 * </p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 * <li><code>GET /api/v1/geo/me</code>: resuelve ubicación de la conexión
 * actual</li>
 * </ul>
 *
 * <h3>Simulación en desarrollo</h3>
 * <p>
 * Cuando la propiedad {@code mapit.geo.allow-sim-ip=true}, el parámetro
 * opcional {@code simIp} permite forzar una IP concreta para probar distintas
 * ubicaciones sin salir del entorno local. En producción esta opción está
 * desactivada y el parámetro se ignora silenciosamente.
 * </p>
 *
 * <h3>Autenticación</h3>
 * <p>
 * El endpoint es público: el mapa debe ser accesible antes del login.
 * </p>
 *
 * @see GeoIpService
 * @see GeoIpResponse
 */
@RestController
@RequestMapping("/api/v1/geo")
public class GeoIpController {

    private static final Logger log = LoggerFactory.getLogger(GeoIpController.class);

    private final GeoIpService geoIpService;

    /**
     * Constructor para inyección de dependencias.
     *
     * @param geoIpService servicio de resolución de ubicación por IP
     */
    public GeoIpController(GeoIpService geoIpService) {
        this.geoIpService = geoIpService;
    }

    /**
     * Resuelve la ubicación geográfica aproximada de la conexión actual.
     * <p>
     * El servicio determina la IP efectiva en el siguiente orden de prioridad:
     * </p>
     * <ol>
     * <li>{@code simIp} — si está habilitada la simulación y se recibe el
     * parámetro</li>
     * <li>{@code X-Forwarded-For} — primera IP de la cadena (detrás de
     * proxy/balanceador)</li>
     * <li>{@code X-Real-IP} — cabecera alternativa de proxy</li>
     * <li>{@code remoteAddr} — dirección directa de la conexión TCP</li>
     * </ol>
     * <p>
     * Si el proveedor GeoIP externo no está disponible o no devuelve coordenadas
     * válidas, el servicio aplica un fallback geográfico configurado en las
     * propiedades
     * ({@code mapit.geo.fallback-lat} / {@code mapit.geo.fallback-lng}).
     * </p>
     *
     * @param request request HTTP entrante, usado para leer cabeceras y remote
     *                address
     * @param simIp   IP a simular en lugar de la real (solo activo si
     *                {@code mapit.geo.allow-sim-ip=true}); parámetro opcional
     * @return {@link GeoIpResponse} con coordenadas, ciudad, país, IP resuelta y
     *         origen
     *
     * @see GeoIpService#resolveLocation(HttpServletRequest, String)
     */
    @GetMapping("/me")
    public GeoIpResponse getMyLocation(
            HttpServletRequest request,
            @RequestParam(name = "simIp", required = false) String simIp) {

        log.debug("GET /geo/me simIp={}", simIp);
        GeoIpResponse response = geoIpService.resolveLocation(request, simIp);
        log.info("Geo resuelta ip={} lat={} lng={} source={}", response.resolvedIp(), response.lat(), response.lng(),
                response.source());
        return response;
    }
}
