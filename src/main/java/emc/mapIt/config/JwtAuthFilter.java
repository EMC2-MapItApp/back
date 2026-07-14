package emc.mapIt.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import emc.mapIt.exception.ApiException;
import emc.mapIt.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Filtro de autenticación JWT ejecutado una vez por request. Si hay cabecera
 * {@code Authorization: Bearer}, valida el token y rellena el {@code SecurityContextHolder}; si
 * el token es inválido/expirado, responde el error inmediatamente. Si no hay cabecera, deja
 * pasar la request sin autenticar — es {@link SecurityConfig} quien decide, por ruta, si eso
 * basta o hace falta autenticación.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    /**
     * Intenta autenticar la request a partir de la cabecera {@code Authorization}. Sin cabecera
     * Bearer, continúa la cadena de filtros sin más (rutas públicas); con un token inválido,
     * corta la cadena y devuelve el error JSON estándar del proyecto.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Sin token: continúa; Spring Security aplica las reglas por ruta
            filterChain.doFilter(request, response);
            return;
        }

        try {
            var userId = jwtService.extractUserId(authHeader);
            var auth = new UsernamePasswordAuthenticationToken(
                    userId.toString(), null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        } catch (ApiException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, ex.getStatus(), ex.getCode(), ex.getMessage());
        }
    }

    private void writeError(HttpServletResponse response,
                            HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Map.of("error", Map.of("code", code, "message", message, "status", status.value())));
    }
}
