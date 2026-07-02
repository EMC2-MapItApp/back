package emc.mapIt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                // Preflight CORS - siempre libre
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Auth - endpoints públicos
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/logout").permitAll()

                // Geo - público (mapa accesible sin login)
                .requestMatchers(HttpMethod.GET, "/api/v1/geo/**").permitAll()

                // Categorías - lecturas públicas, escrituras protegidas
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()

                // Publicaciones - lecturas públicas (enrollments NO)
                .requestMatchers(HttpMethod.GET, "/api/v1/publications").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/publications/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/publications/author/{authorId}").permitAll()

                // Perfiles de usuario - lectura pública
                .requestMatchers(HttpMethod.GET, "/api/v1/users/**").permitAll()

                // Test - endpoint de prueba
                .requestMatchers(HttpMethod.GET, "/api/test/**").permitAll()

                // Todo lo demás requiere token válido
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                        "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Autenticacion requerida\",\"status\":401}}"
                    );
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}