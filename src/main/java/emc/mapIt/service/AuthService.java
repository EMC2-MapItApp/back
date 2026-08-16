package emc.mapIt.service;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.dto.AuthLoginRequest;
import emc.mapIt.dto.AuthRegisterRequest;
import emc.mapIt.dto.AuthResponse;
import emc.mapIt.dto.RegisterResponse;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import emc.mapIt.mapper.AuthRegisterToUserMapper;
import emc.mapIt.mapper.UserWithProfileToMapItUserMapper;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Mismo patrón que {@link emc.mapIt.dto.AuthRegisterRequest#email()}. */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthRegisterToUserMapper authRegisterToUserMapper;
    private final UserWithProfileToMapItUserMapper userWithProfileToMapItUserMapper;
    private final PasswordPolicyService passwordPolicyService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public AuthService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthRegisterToUserMapper authRegisterToUserMapper,
            UserWithProfileToMapItUserMapper userWithProfileToMapItUserMapper,
            PasswordPolicyService passwordPolicyService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authRegisterToUserMapper = authRegisterToUserMapper;
        this.userWithProfileToMapItUserMapper = userWithProfileToMapItUserMapper;
        this.passwordPolicyService = passwordPolicyService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * Registra un usuario nuevo. No devuelve token: la cuenta queda sin verificar
     * hasta que confirme el email (ver {@link EmailVerificationService}), y el login
     * la bloquea mientras tanto.
     */
    public RegisterResponse register(AuthRegisterRequest request) {
        log.debug("Inicio register auth para email={}", request != null ? request.email() : null);

        if (request == null) {
            throw new ApiException("BAD_REQUEST", "Request de registro requerida", HttpStatus.BAD_REQUEST);
        }
        if (isBlank(request.name()) || isBlank(request.email())
                || isBlank(request.password()) || request.userType() == null) {
            throw new ApiException("BAD_REQUEST", "Datos de registro invalidos", HttpStatus.BAD_REQUEST);
        }

        // Si el cliente no proporciona nick, se genera uno unico a partir del nombre.
        // El usuario puede cambiarlo despues desde su perfil.
        String resolvedNick = isBlank(request.nick())
                ? userService.generateUniqueNick(request.name())
                : request.nick().trim();

        passwordPolicyService.validate(request.password(), List.of(request.name(), resolvedNick, request.email()));

        User candidate = authRegisterToUserMapper.toEntity(request);
        candidate.setNick(resolvedNick); // sustituye el valor del mapper (puede ser null si no se envio)
        candidate.setPasswordHash(passwordEncoder.encode(request.password()));

        User savedUser = userService.create(candidate);
        log.info("Usuario registrado id={} email={}", savedUser.getId(), savedUser.getEmail());

        emailVerificationService.issueAndSend(savedUser);

        return new RegisterResponse(savedUser.getEmail());
    }

    public AuthResponse login(AuthLoginRequest request) {
        log.info("Inicio login auth para identifier={}", request != null ? request.identifier() : null);

        if (request == null) {
            throw new ApiException("BAD_REQUEST", "Request de login requerida", HttpStatus.BAD_REQUEST);
        }
        if (isBlank(request.identifier()) || isBlank(request.password())) {
            throw new ApiException("BAD_REQUEST", "Credenciales invalidas", HttpStatus.BAD_REQUEST);
        }

        MapItUser user = resolveUserByIdentifier(request.identifier().trim());

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Intento de login fallido para identifier={}", request.identifier());
            throw new ApiException("INVALID_CREDENTIALS", "Credenciales invalidas", HttpStatus.UNAUTHORIZED);
        }

        // Va despues de validar la contraseña: el usuario ya demostro que la conoce,
        // asi que decirle que falta verificar el email no filtra nada nuevo (no rompe
        // el 401 generico anti-enumeracion de arriba).
        if (!user.isEmailVerified()) {
            throw new ApiException(
                    "EMAIL_NOT_VERIFIED",
                    "Debes verificar tu correo electronico antes de iniciar sesion.",
                    HttpStatus.FORBIDDEN);
        }

        String token = jwtService.generateToken(user.getId());
        log.info("Login exitoso userId={}", user.getId());

        return new AuthResponse(token, userService.toResponse(user));
    }

    /** Delega en {@link EmailVerificationService}; AuthController mantiene una unica fachada de auth. */
    public void verifyEmail(String token) {
        emailVerificationService.verify(token);
    }

    /** Delega en {@link EmailVerificationService}; AuthController mantiene una unica fachada de auth. */
    public void resendVerification(String email) {
        emailVerificationService.resend(email);
    }

    /** Delega en {@link PasswordResetService}; AuthController mantiene una unica fachada de auth. */
    public void forgotPassword(String email) {
        passwordResetService.requestReset(email);
    }

    /** Delega en {@link PasswordResetService}; AuthController mantiene una unica fachada de auth. */
    public void resetPassword(String token, String newPassword) {
        passwordResetService.resetPassword(token, newPassword);
    }

    public String requireUserId(String authHeader) {
        return jwtService.extractUserId(authHeader);
    }

    /**
     * Como {@link #requireUserId}, pero además exige que el usuario autenticado sea
     * {@link UserType#ADMIN} — usado por operaciones administrativas (p. ej. el CRUD de
     * categorías) que no deben quedar abiertas a cualquier usuario autenticado.
     *
     * @throws ApiException con código FORBIDDEN si el usuario no es ADMIN
     */
    public String requireAdmin(String authHeader) {
        String userId = requireUserId(authHeader);
        MapItUser user = userService.getByIdOrThrow(userId);
        if (user.getUserType() != UserType.ADMIN) {
            throw new ApiException("FORBIDDEN", "Requiere permisos de administrador", HttpStatus.FORBIDDEN);
        }
        return userId;
    }

    /**
     * Como {@link #requireUserId}, pero devuelve {@code null} en vez de lanzar cuando no hay
     * cabecera o el token no es válido — para endpoints públicos (p. ej. el listado de
     * publicaciones) que deben seguir siendo accesibles sin sesión, pero que quieren saber quién
     * pregunta cuando sí la hay (para calcular pertenencia a grupo, ver {@code PublicationService}).
     */
    public String resolveUserIdOrNull(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        try {
            return jwtService.extractUserId(authHeader);
        } catch (ApiException ex) {
            return null;
        }
    }

    /**
     * Resuelve el usuario a partir del identificador de login: un nick prefijado con
     * {@code @} (p.ej. {@code @ana}) o un email. Mismo regex que {@link emc.mapIt.dto.AuthRegisterRequest}
     * para que "qué cuenta como email" sea consistente entre registro y login.
     */
    private MapItUser resolveUserByIdentifier(String identifier) {
        if (identifier.startsWith("@")) {
            String nick = identifier.substring(1);
            if (isBlank(nick)) {
                throw new ApiException("BAD_REQUEST", "Credenciales invalidas", HttpStatus.BAD_REQUEST);
            }
            return userService.getByNickOrThrow(nick);
        }
        if (isEmailFormat(identifier)) {
            return userService.getByEmailOrThrow(identifier);
        }
        throw new ApiException("BAD_REQUEST", "Credenciales invalidas", HttpStatus.BAD_REQUEST);
    }

    private boolean isEmailFormat(String value) {
        return EMAIL_PATTERN.matcher(value).matches();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
