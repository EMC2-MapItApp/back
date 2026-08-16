package emc.mapIt.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Cubre {@link RateLimitFilter}: límite de intentos por IP en {@code /auth/login} (10/min) y
 * {@code /auth/forgot-password} (5/min), sin afectar al resto de rutas.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private RateLimitFilter filter;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        filter = new RateLimitFilter();
        responseBody = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    private HttpServletRequest request(String method, String path, String ip) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getMethod()).thenReturn(method);
        lenient().when(req.getRequestURI()).thenReturn(path);
        lenient().when(req.getRemoteAddr()).thenReturn(ip);
        return req;
    }

    @Test
    void login_dentroDelLimite_dejaPasarLasDiezPeticiones() throws Exception {
        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(request("POST", "/api/v1/auth/login", "1.1.1.1"), response, filterChain);
        }

        verify(filterChain, times(10)).doFilter(any(), any());
        verify(response, never()).setStatus(429);
    }

    @Test
    void login_superaElLimite_laOnceavaDevuelve429YNoLlegaAlChain() throws Exception {
        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(request("POST", "/api/v1/auth/login", "2.2.2.2"), response, filterChain);
        }

        filter.doFilterInternal(request("POST", "/api/v1/auth/login", "2.2.2.2"), response, filterChain);

        verify(filterChain, times(10)).doFilter(any(), any());
        verify(response).setStatus(429);
        assertThat(responseBody.toString()).contains("RATE_LIMITED");
    }

    @Test
    void forgotPassword_tieneLimiteIndependienteDeLogin() throws Exception {
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request("POST", "/api/v1/auth/forgot-password", "3.3.3.3"), response, filterChain);
        }
        filter.doFilterInternal(request("POST", "/api/v1/auth/forgot-password", "3.3.3.3"), response, filterChain);

        verify(filterChain, times(5)).doFilter(any(), any());
        verify(response).setStatus(429);
    }

    @Test
    void ipsDistintas_tienenContadoresIndependientes() throws Exception {
        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(request("POST", "/api/v1/auth/login", "4.4.4.4"), response, filterChain);
        }
        // IP distinta: su propio cupo, no afectada por 4.4.4.4 ya agotada.
        filter.doFilterInternal(request("POST", "/api/v1/auth/login", "5.5.5.5"), response, filterChain);

        verify(filterChain, times(11)).doFilter(any(), any());
        verify(response, never()).setStatus(429);
    }

    @Test
    void rutaNoRelacionada_dejaPasarSinLimitar() throws Exception {
        for (int i = 0; i < 50; i++) {
            filter.doFilterInternal(request("GET", "/api/v1/publications", "6.6.6.6"), response, filterChain);
        }

        verify(filterChain, times(50)).doFilter(any(), any());
        verify(response, never()).setStatus(429);
    }
}
