package emc.mapIt.config;

import emc.mapIt.exception.ApiException;
import emc.mapIt.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Filtro de autenticación JWT ejecutado una vez por request. Si hay cabecera
 * {@code Authorization: Bearer} con un token válido, rellena el {@code SecurityContextHolder}.
 * Sin cabecera, o con un token inválido/expirado, deja pasar la request sin autenticar — es
 * {@link SecurityConfig} quien decide, por ruta, si eso basta (pública) o hace falta
 * autenticación (en cuyo caso el {@code authenticationEntryPoint} de {@link SecurityConfig}
 * responde el 401).
 * <p>
 * Importante no cortar la cadena aquí ante un token inválido: el navegador puede llevar en
 * {@code localStorage} un token caducado de una sesión anterior y seguir enviándolo en
 * peticiones a rutas públicas (p. ej. {@code /api/v1/auth/reset-password}, vía el interceptor
 * del frontend que añade el header si existe token, sin distinguir ruta). Cortar aquí rompía
 * esas rutas públicas con un 401 solo en el navegador con el token caducado — bug real
 * observado, no solo un caso hipotético.
 * </p>
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Intenta autenticar la request a partir de la cabecera {@code Authorization}. Sin cabecera
     * Bearer o con un token inválido, continúa la cadena sin autenticar — nunca corta la request
     * ni escribe una respuesta de error por sí mismo (eso es responsabilidad exclusiva de
     * {@link SecurityConfig} para las rutas que sí requieren sesión).
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                var userId = jwtService.extractUserId(authHeader);
                var auth = new UsernamePasswordAuthenticationToken(
                        userId.toString(), null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ApiException ex) {
                // Token ausente/invalido/expirado: no autenticamos, pero seguimos la cadena.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
