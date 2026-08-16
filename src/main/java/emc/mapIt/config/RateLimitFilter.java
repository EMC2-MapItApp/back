package emc.mapIt.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los intentos por IP a {@code POST /api/v1/auth/login} y
 * {@code POST /api/v1/auth/forgot-password} — sin esto, nada impedía fuerza bruta de contraseñas
 * ni enumeración masiva de cuentas vía reset de contraseña (el cooldown de
 * {@code PasswordResetService} es por email destino, no por origen).
 * <p>
 * En memoria, un {@link Bucket} de bucket4j por IP y endpoint — suficiente mientras Cloud Run
 * corra con una sola instancia (hoy es el caso). Si se escala a varias réplicas, cada una lleva
 * su propio contador y el límite efectivo pasa a ser {@code N × réplicas}; en ese momento hace
 * falta un backend compartido (Redis/Hazelcast) en vez de este mapa local.
 * </p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String FORGOT_PASSWORD_PATH = "/api/v1/auth/forgot-password";

    /** 10 intentos/minuto por IP: cubre errores de tecleo legítimos sin dejar hueco a fuerza bruta. */
    private static final int LOGIN_CAPACITY = 10;

    /** 5 solicitudes/minuto por IP: complementa el cooldown por email ya existente en el servicio. */
    private static final int FORGOT_PASSWORD_CAPACITY = 5;

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> forgotPasswordBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Map<String, Bucket> buckets = bucketsFor(request);
        if (buckets == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = ClientIpResolver.resolve(request);
        Bucket bucket = buckets.computeIfAbsent(ip, key -> newBucket(buckets == loginBuckets ? LOGIN_CAPACITY : FORGOT_PASSWORD_CAPACITY));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit superado path={} ip={}", request.getRequestURI(), ip);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Demasiados intentos. Espera un momento antes de volver a intentarlo.\",\"status\":429}}"
        );
    }

    private Map<String, Bucket> bucketsFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if (LOGIN_PATH.equals(path)) {
            return loginBuckets;
        }
        if (FORGOT_PASSWORD_PATH.equals(path)) {
            return forgotPasswordBuckets;
        }
        return null;
    }

    private Bucket newBucket(int capacityPerMinute) {
        Bandwidth limit = Bandwidth.classic(capacityPerMinute,
                io.github.bucket4j.Refill.intervally(capacityPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
