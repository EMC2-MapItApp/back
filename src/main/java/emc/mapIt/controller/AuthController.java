package emc.mapIt.controller;

import emc.mapIt.dto.AuthLoginRequest;
import emc.mapIt.dto.AuthRegisterRequest;
import emc.mapIt.dto.AuthResponse;
import emc.mapIt.dto.ForgotPasswordRequest;
import emc.mapIt.dto.ForgotPasswordResponse;
import emc.mapIt.dto.MapItUserResponse;
import emc.mapIt.dto.RegisterResponse;
import emc.mapIt.dto.ResendVerificationRequest;
import emc.mapIt.dto.ResendVerificationResponse;
import emc.mapIt.dto.ResetPasswordRequest;
import emc.mapIt.dto.VerifyEmailRequest;
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
 *   <li><code>POST /api/v1/auth/register</code>: registro de usuario (no autentica, ver verify-email)</li>
 *   <li><code>POST /api/v1/auth/verify-email</code>: confirma el email con el token recibido por correo</li>
 *   <li><code>POST /api/v1/auth/resend-verification</code>: reenvia el correo de verificacion</li>
 *   <li><code>POST /api/v1/auth/forgot-password</code>: solicita el restablecimiento de contraseña</li>
 *   <li><code>POST /api/v1/auth/reset-password</code>: fija una contraseña nueva con el token recibido por correo</li>
 *   <li><code>POST /api/v1/auth/login</code>: autenticación por email o @nick + password (requiere email verificado)</li>
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
     * Registra un usuario nuevo. No autentica: la cuenta queda sin verificar hasta
     * que el usuario confirme su email (ver {@code /verify-email}).
     * <p>
     * Retorna código HTTP 201 (Created) con el email registrado, sin token.
     * </p>
     *
     * @param request payload de registro validado
     * @return {@link ResponseEntity} con estado 201 y {@link RegisterResponse}
     *
     * @see AuthService#register(AuthRegisterRequest)
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody AuthRegisterRequest request) {
        log.info("Registro de usuario email={} userType={}", request.email(), request.userType());
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Confirma el email de un usuario a partir del token recibido por correo.
     *
     * @param request token de verificacion
     * @return 200 si el token era valido; {@link emc.mapIt.exception.GlobalExceptionHandler}
     *         traduce el error si no lo era
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        log.debug("Verificacion de email solicitada");
        authService.verifyEmail(request.token());
        return ResponseEntity.ok().build();
    }

    /**
     * Reenvia el correo de verificacion si procede (email registrado, no verificado,
     * fuera de cooldown). Responde siempre igual para no permitir enumeracion de usuarios.
     *
     * @param request email al que reenviar
     * @return mensaje generico, independiente del resultado real
     */
    @PostMapping("/resend-verification")
    public ResendVerificationResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        log.debug("Reenvio de verificacion solicitado");
        authService.resendVerification(request.email());
        return new ResendVerificationResponse("Si el correo existe y no esta verificado, se ha reenviado un enlace.");
    }

    /**
     * Solicita el restablecimiento de contraseña ("olvidé mi contraseña"). A diferencia de
     * {@code resend-verification}, aquí sí se distingue si el email existe (decisión de
     * producto: el registro ya lo revela vía 409 CONFLICT, así que no hay anti-enumeración
     * real que proteger).
     *
     * @param request email de la cuenta a restablecer
     * @return mensaje de confirmación; {@link emc.mapIt.exception.GlobalExceptionHandler}
     *         traduce a 404 si el email no existe
     */
    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Solicitud de restablecimiento de contraseña para email={}", request.email());
        authService.forgotPassword(request.email());
        return new ForgotPasswordResponse("Te hemos enviado un enlace para restablecer tu contraseña.");
    }

    /**
     * Fija una contraseña nueva a partir del token recibido por correo.
     *
     * @param request token de restablecimiento y contraseña nueva
     * @return 200 si el token era válido y la contraseña cumplía la política; el
     *         {@link emc.mapIt.exception.GlobalExceptionHandler} traduce el error si no
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.debug("Restablecimiento de contraseña solicitado");
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    /**
     * Autentica un usuario existente por email o {@code @nick} y contraseña.
     *
     * @param request credenciales de acceso validadas
     * @return {@link AuthResponse} con token JWT y datos del usuario autenticado
     *
     * @see AuthService#login(AuthLoginRequest)
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthLoginRequest request) {
        log.info("Login solicitado para identifier={}", request.identifier());
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