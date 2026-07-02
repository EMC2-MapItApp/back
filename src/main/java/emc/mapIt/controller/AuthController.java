package emc.mapIt.controller;

import emc.mapIt.dto.AuthLoginRequest;
import emc.mapIt.dto.AuthRegisterRequest;
import emc.mapIt.dto.AuthResponse;
import emc.mapIt.dto.MapItUserResponse;
import emc.mapIt.service.AuthService;
import emc.mapIt.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para autenticación y sesión de usuarios.
 * <p>
 * Expone endpoints públicos para registrar cuentas y autenticar usuarios,
 * además de endpoints de sesión para recuperar el usuario actual y simular cierre de sesión.
 * </p>
 *
 * <h3>Endpoints principales</h3>
 * <ul>
 *   <li><code>POST /api/v1/auth/register</code>: registro de usuario</li>
 *   <li><code>POST /api/v1/auth/login</code>: autenticación por email/password</li>
 *   <li><code>GET /api/v1/auth/me</code>: perfil del usuario autenticado</li>
 *   <li><code>POST /api/v1/auth/logout</code>: cierre de sesión lógico (stateless)</li>
 * </ul>
 *
 * <h3>Autenticación</h3>
 * <p>
 * El endpoint <code>/me</code> requiere header <code>Authorization</code> con token JWT válido.
 * </p>
 *
 * @author MapIt Development Team
 * @version 1.0.0
 * @since 2026-06-15
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserService userService;

    /**
     * Constructor para inyección de dependencias del controlador de autenticación.
     *
     * @param authService servicio de registro/login y validación de identidad
     * @param userService servicio de lectura y serialización de usuario
     */
    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /**
     * Registra un usuario nuevo y devuelve sesión autenticada.
     * <p>
     * Retorna código HTTP 201 (Created) junto con token JWT y datos del usuario persistido.
     * </p>
     *
     * @param request payload de registro validado
     * @return {@link ResponseEntity} con estado 201 y {@link AuthResponse}
     *
     * @see AuthService#register(AuthRegisterRequest)
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRegisterRequest request) {
        log.info("Registro de usuario {}", request);
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Autentica un usuario existente por email y contraseña.
     *
     * @param request credenciales de acceso validadas
     * @return {@link AuthResponse} con token JWT y datos del usuario autenticado
     *
     * @see AuthService#login(AuthLoginRequest)
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthLoginRequest request) {
        log.info("Login solicitado para email={}", request.email());
        return authService.login(request);
    }

    /**
     * Recupera la información del usuario autenticado actual.
     * <p>
     * Extrae el userId desde el token JWT del header Authorization y devuelve
     * la vista serializable del usuario.
     * </p>
     *
     * @param authorization header Authorization con token Bearer
     * @return perfil serializable del usuario autenticado
     */
    @GetMapping("/me")
    public MapItUserResponse me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Consulta de perfil actual");
        return userService.toResponse(userService.getByIdOrThrow(authService.requireUserId(authorization)));
    }

    /**
     * Simula cierre de sesión en arquitectura stateless.
     * <p>
     * Como JWT es stateless, el backend no invalida sesión en servidor.
     * El cliente debe descartar el token localmente.
     * </p>
     *
     * @return {@link ResponseEntity} vacío con estado 204 (No Content)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        log.debug("Logout solicitado");
        return ResponseEntity.noContent().build();
    }
}