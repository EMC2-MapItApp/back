package emc.mapIt.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;

/**
 * Adaptador de salida (arquitectura hexagonal) del puerto {@link GeoLocationProvider} contra
 * ip-api.com. Es el único punto del módulo que sabe que existe un proveedor HTTP concreto, su
 * URL, su timeout y la forma exacta de su respuesta JSON — nada de eso se filtra a
 * {@link GeoIpService}.
 */
@Component
public class IpApiGeoLocationProvider implements GeoLocationProvider {

    private static final Logger log = LoggerFactory.getLogger(IpApiGeoLocationProvider.class);

    private final RestClient restClient;
    private final String providerUrl;

    /**
     * @param providerUrl URL base del proveedor GeoIP (p. ej. {@code http://ip-api.com/json})
     * @param timeoutMs   timeout en milisegundos para la llamada externa
     */
    public IpApiGeoLocationProvider(
            @Value("${mapit.geo.provider-url}") String providerUrl,
            @Value("${mapit.geo.timeout-ms:3000}") int timeoutMs) {
        this.providerUrl = providerUrl;

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(timeoutMs);
        rf.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(rf)
                .build();
    }

    @Override
    public Optional<GeoLocation> locate(String ip) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(providerUrl + "/" + ip)
                    .retrieve()
                    .body(Map.class);

            if (body == null) {
                return Optional.empty();
            }

            Object status = body.get("status");
            if (status != null && !"success".equalsIgnoreCase(String.valueOf(status))) {
                log.warn("Proveedor GeoIP devolvio estado no exitoso ip={} status={}", ip, status);
                return Optional.empty();
            }

            Double lat = toDouble(body.get("lat"));
            Double lng = toDouble(body.get("lon"));
            if (lat == null || lng == null) {
                log.warn("Proveedor GeoIP sin coordenadas ip={}", ip);
                return Optional.empty();
            }

            String city = stringOrNull(body.get("city"));
            String country = stringOrNull(body.get("countryCode"));

            return Optional.of(new GeoLocation(lat, lng, city, country));
        } catch (RestClientException ex) {
            log.warn("Fallo consultando proveedor GeoIP ip={}", ip, ex);
            return Optional.empty();
        }
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
