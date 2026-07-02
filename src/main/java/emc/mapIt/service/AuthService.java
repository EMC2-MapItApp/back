package emc.mapIt.service;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.dto.AuthLoginRequest;
import emc.mapIt.dto.AuthRegisterRequest;
import emc.mapIt.dto.AuthResponse;
import emc.mapIt.entity.User;
import emc.mapIt.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import emc.mapIt.mapper.AuthRegisterToUserMapper;
import emc.mapIt.mapper.UserWithProfileToMapItUserMapper;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthRegisterToUserMapper authRegisterToUserMapper;
    private final UserWithProfileToMapItUserMapper userWithProfileToMapItUserMapper;

    public AuthService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthRegisterToUserMapper authRegisterToUserMapper,
            UserWithProfileToMapItUserMapper userWithProfileToMapItUserMapper
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authRegisterToUserMapper = authRegisterToUserMapper;
        this.userWithProfileToMapItUserMapper = userWithProfileToMapItUserMapper;
    }

    public AuthResponse register(AuthRegisterRequest request) {
        log.debug("Inicio register auth para email={}", request != null ? request.email() : null);

        if (request == null) {
            throw new ApiException("BAD_REQUEST", "Request de registro requerida", HttpStatus.BAD_REQUEST);
        }
        if (isBlank(request.name()) || isBlank(request.email()) || isBlank(request.password()) || request.userType() == null) {
            throw new ApiException("BAD_REQUEST", "Datos de registro invalidos", HttpStatus.BAD_REQUEST);
        }
        
        User candidate = authRegisterToUserMapper.toEntity(request);
        candidate.setPasswordHash(passwordEncoder.encode(request.password()));

        User savedUser = userService.create(candidate);

        log.info("Usuario registrado id={} email={}", savedUser.getId(), savedUser.getEmail());
        String token = jwtService.generateToken(savedUser.getId());

        MapItUser mapItUser = userWithProfileToMapItUserMapper.toDomain(
                savedUser,
                savedUser.getProfileDetails()
        );

        return new AuthResponse(token, userService.toResponse(mapItUser));
    }

    public AuthResponse login(AuthLoginRequest request) {
        log.info("Inicio login auth para email={}", request != null ? request.email() : null);

        if (request == null) {
            throw new ApiException("BAD_REQUEST", "Request de login requerida", HttpStatus.BAD_REQUEST);
        }
        if (isBlank(request.email()) || isBlank(request.password())) {
            throw new ApiException("BAD_REQUEST", "Credenciales invalidas", HttpStatus.BAD_REQUEST);
        }

        MapItUser user = userService.getByEmailOrThrow(request.email());
        
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Intento de login fallido para email={}", request.email());
            throw new ApiException("UNAUTHORIZED", "Credenciales invalidas", HttpStatus.UNAUTHORIZED);
        }

        String token = jwtService.generateToken(user.getId());
        log.info("Login exitoso userId={}", user.getId());

        return new AuthResponse(token, userService.toResponse(user));
    }

    public String requireUserId(String authHeader) {
        return jwtService.extractUserId(authHeader);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
