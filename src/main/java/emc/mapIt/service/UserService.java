package emc.mapIt.service;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.dto.MapItUserResponse;
import emc.mapIt.dto.UserPatchRequest;
import emc.mapIt.dto.UserSearchResultResponse;
import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserProfileDetails;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.repository.LocationTypeRepository;
import emc.mapIt.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Servicio de aplicación para gestión de usuarios con persistencia JPA.
 * <p>
 * Responsabilidades:
 * </p>
 * <ul>
 * <li>Crear usuarios validando unicidad de email</li>
 * <li>Consultar usuarios por id/email con errores de dominio</li>
 * <li>Actualizar campos editables del perfil</li>
 * <li>Desbloquear capacidades</li>
 * <li>Convertir entre modelo de dominio, entidad y DTO de respuesta</li>
 * </ul>
 *
 * @author MapIt Development Team
 * @version 1.0.0
 * @since 2026-06-15
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** Máximo de resultados devueltos por {@link #searchUsers(String, String)}. */
    private static final int USER_SEARCH_LIMIT = 20;

    private final UserRepository userRepository;
    private final LocationTypeRepository locationTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;

    /**
     * Constructor para inyección de dependencias.
     *
     * @param userRepository         repositorio de usuarios
     * @param locationTypeRepository repositorio de tipos de ubicación
     * @param passwordEncoder        codificador de contraseñas BCrypt
     * @param passwordPolicyService  validación de política de contraseñas
     */
    public UserService(UserRepository userRepository, LocationTypeRepository locationTypeRepository,
            PasswordEncoder passwordEncoder, PasswordPolicyService passwordPolicyService) {
        this.userRepository = userRepository;
        this.locationTypeRepository = locationTypeRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
    }

    /**
     * Crea un usuario nuevo persistiéndolo en base de datos.
     * <p>
     * Reglas de configuración inicial por tipo:
     * </p>
     * <ul>
     * <li>INDIVIDUAL: nivel/XP iniciales en 0, capacidades básicas, sin
     * favoritos</li>
     * <li>PROFESSIONAL/ENTITY: nivel/XP nulos, capacidades ampliadas, sin
     * favoritos</li>
     * </ul>
     * <p>
     * Email se normaliza (trim + lowercase) antes de persistir.
     * </p>
     *
     * @param candidate entidad de usuario a crear
     * @return usuario creado desde estado persistido
     * @throws ApiException con código BAD_REQUEST si datos incompletos
     * @throws ApiException con código CONFLICT si el email ya existe
     * @see #getBasicCapabilities()
     * @see #getAllCapabilities()
     */
    public User create(User candidate) {
        log.debug("Inicio create user email={} userType={}",
                candidate != null ? candidate.getEmail() : null,
                candidate != null ? candidate.getUserType() : null);

        if (candidate == null) {
            throw new ApiException("BAD_REQUEST", "El usuario es requerido", HttpStatus.BAD_REQUEST);
        }
        if (isBlank(candidate.getName()) || isBlank(candidate.getEmail())
                || isBlank(candidate.getPasswordHash()) || candidate.getUserType() == null) {
            throw new ApiException("BAD_REQUEST", "Datos incompletos para crear usuario", HttpStatus.BAD_REQUEST);
        }

        String normalizedEmail = normalizeEmail(candidate.getEmail());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException("CONFLICT", "Ya existe un usuario con ese email", HttpStatus.CONFLICT);
        }

        String normalizedNick;
        if (isBlank(candidate.getNick())) {
            normalizedNick = generateUniqueNick(candidate.getName(), normalizedEmail);
        } else {
            normalizedNick = normalizeNick(candidate.getNick());
            if (userRepository.existsByNick(normalizedNick)) {
                throw new ApiException("CONFLICT", "Ya existe un usuario con ese nick", HttpStatus.CONFLICT);
            }
        }

        // Asegurar normalización consistente antes de persistir
        candidate.setName(candidate.getName().trim());
        candidate.setNick(normalizedNick);
        candidate.setEmail(normalizedEmail);
        if (candidate.getProfileDetails() == null) {
            log.debug("Inicializando UserProfileDetails para email={}", normalizedEmail);
            candidate.attachProfileDetails(new UserProfileDetails());
        } else if (candidate.getProfileDetails() != null) {
            log.debug("Reasociando UserProfileDetails desanclado para email={}", normalizedEmail);
            candidate.attachProfileDetails(candidate.getProfileDetails());
        }

        UserProfileDetails profileDetails = candidate.getProfileDetails();
        if (candidate.getUserType() == UserType.PARTICULAR) {
            profileDetails.setLevel(0);
            profileDetails.setXp(0);
        } else {
            profileDetails.setLevel(null);
            profileDetails.setXp(null);
        }

        User savedUser = userRepository.save(candidate);
        log.info("Usuario persistido en BD id={} email={}", savedUser.getId(), savedUser.getEmail());
        return savedUser;
    }

    /**
     * Busca un usuario por email normalizado para autenticación.
     * <p>
     * Devuelve UNAUTHORIZED si el email no existe (patrón de seguridad:
     * no se distingue entre "email inexistente" y "password inválida").
     * </p>
     *
     * @param email email a buscar (case-insensitive)
     * @return usuario encontrado
     * @throws ApiException con código UNAUTHORIZED si no existe
     */
    @Transactional(readOnly = true)
    public MapItUser getByEmailOrThrow(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ApiException("UNAUTHORIZED", "Credenciales invalidas", HttpStatus.UNAUTHORIZED));
        return convertToDomain(user);
    }

    /**
     * Busca un usuario por nick normalizado para autenticación.
     * <p>
     * Devuelve UNAUTHORIZED si el nick no existe (mismo patrón anti-enumeración que
     * {@link #getByEmailOrThrow(String)}: no se distingue de una password inválida).
     * </p>
     *
     * @param nick nick a buscar (case-sensitive, sin el prefijo {@code @})
     * @return usuario encontrado
     * @throws ApiException con código UNAUTHORIZED si no existe
     */
    @Transactional(readOnly = true)
    public MapItUser getByNickOrThrow(String nick) {
        String normalizedNick = normalizeNick(nick);
        User user = userRepository.findByNick(normalizedNick)
                .orElseThrow(() -> new ApiException("UNAUTHORIZED", "Credenciales invalidas", HttpStatus.UNAUTHORIZED));
        return convertToDomain(user);
    }

    /**
     * Busca un usuario por UUID.
     *
     * @param id identificador único del usuario
     * @return usuario encontrado
     * @throws ApiException con código NOT_FOUND si no existe
     */
    @Transactional(readOnly = true)
    public MapItUser getByIdOrThrow(String id) {
        if (id == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));
        return convertToDomain(user);
    }

    /**
     * Busca usuarios por coincidencia parcial de nick o email (buscador de invitación a
     * grupos). Excluye al propio usuario que busca y limita el resultado para no exponer el
     * listado completo con una query corta.
     *
     * @param query           texto de búsqueda; menos de 2 caracteres devuelve lista vacía
     * @param excludingUserId id del usuario autenticado, excluido de los resultados
     * @return coincidencias, máximo {@value #USER_SEARCH_LIMIT}
     */
    @Transactional(readOnly = true)
    public List<UserSearchResultResponse> searchUsers(String query, String excludingUserId) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        String trimmed = query.trim();
        return userRepository.findByNickContainingIgnoreCaseOrEmailContainingIgnoreCase(trimmed, trimmed).stream()
                .filter(user -> !user.getId().equals(excludingUserId))
                .limit(USER_SEARCH_LIMIT)
                .map(user -> new UserSearchResultResponse(
                        user.getId(),
                        user.getName(),
                        user.getNick(),
                        user.getEmail(),
                        user.getProfileDetails() != null ? user.getProfileDetails().getAvatarUrl() : null))
                .toList();
    }

    /**
     * Actualiza campos editables de perfil del usuario.
     * <p>
     * Campos soportados:
     * </p>
     * <ul>
     * <li>{@code name}: actualiza si no está en blanco</li>
     * <li>{@code nick}: actualiza si no está en blanco, validando unicidad
     * (excepto si coincide con el nick actual del propio usuario)</li>
     * <li>{@code avatarUrl}: actualiza URL (puede ser nulo)</li>
     * <li>{@code favoriteLocationTypeIds}: solo para tipo INDIVIDUAL, con
     * validación de nivel</li>
     * </ul>
     *
     * @param id    id del usuario a actualizar
     * @param patch payload parcial (todos los campos opcionales)
     * @return usuario actualizado desde BD
     * @throws ApiException con código BAD_REQUEST si id es nulo
     * @throws ApiException con código NOT_FOUND si el usuario no existe
     * @throws ApiException con código CONFLICT si el nick ya está en uso por otro usuario
     * @throws ApiException con código UNPROCESSABLE_ENTITY si favoritos incumplen
     *                      regla de nivel
     */
    public MapItUser updateUser(String id, UserPatchRequest patch) {
        if (id == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (patch == null) {
            return convertToDomain(user);
        }

        if (patch.name() != null && !patch.name().isBlank()) {
            user.setName(patch.name().trim());
        }

        if (patch.nick() != null && !patch.nick().isBlank()) {
            String normalizedNick = normalizeNick(patch.nick());
            if (!normalizedNick.equals(user.getNick())
                    && userRepository.existsByNick(normalizedNick)) {
                throw new ApiException("CONFLICT", "Ya existe un usuario con ese nick", HttpStatus.CONFLICT);
            }
            user.setNick(normalizedNick);
        }

        UserProfileDetails profileDetails = getOrCreateProfileDetails(user);

        if (patch.avatarUrl() != null) {
            profileDetails.setAvatarUrl(patch.avatarUrl());
        }
        if (patch.phone() != null) {
            profileDetails.setPhone(patch.phone());
        }
        if (patch.city() != null) {
            profileDetails.setCity(patch.city());
        }
        if (patch.province() != null) {
            profileDetails.setProvince(patch.province());
        }
        if (patch.bio() != null) {
            profileDetails.setBio(patch.bio());
        }
        if (patch.birthDate() != null) {
            profileDetails.setBirthDate(patch.birthDate());
        }
        if (patch.favoriteLocationTypeIds() != null && user.getUserType() == UserType.PARTICULAR) {
            List<String> sanitizedFavorites = sanitizeLongList(patch.favoriteLocationTypeIds());
            validateFavoriteLocationTypes(sanitizedFavorites, profileDetails.getLevel());
            user.setFavoriteLocationTypeIds(new ArrayList<>(new LinkedHashSet<>(sanitizedFavorites)));
        }
        log.info("usuario a salvar: {} ", user);
        User savedUser = userRepository.save(user);
        log.info("Usuario actualizado id={}", savedUser.getId());
        return convertToDomain(savedUser);
    }

    /**
     * Desbloquea una nueva capacidad para el usuario.
     * <p>
     * No hace nada si la capacidad ya está desbloqueada (operación idempotente).
     * </p>
     *
     * @param id           id del usuario
     * @param capabilityId identificador de capability a desbloquear
     * @throws ApiException con código BAD_REQUEST si id es nulo
     * @throws ApiException con código NOT_FOUND si el usuario no existe
     * @throws ApiException con código BAD_REQUEST si capabilityId es nulo/vacío
     */
    public void unlockCapability(String id, String capabilityId) {
        if (id == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (isBlank(capabilityId)) {
            throw new ApiException("BAD_REQUEST", "capabilityId invalido", HttpStatus.BAD_REQUEST);
        }

        String trimmed = capabilityId.trim();
        List<String> capabilities = user.getUnlockedCapabilities();
        if (capabilities == null) {
            capabilities = new ArrayList<>();
            user.setUnlockedCapabilities(capabilities);
        }

        if (!capabilities.contains(trimmed)) {
            capabilities.add(trimmed);
            userRepository.save(user);
            log.info("Capability desbloqueada userId={} capabilityId={}", id, trimmed);
        }
    }

    /**
     * Convierte usuario de dominio a DTO de respuesta serializable.
     *
     * @param user usuario de dominio
     * @return DTO listo para JSON
     */
    public MapItUserResponse toResponse(MapItUser user) {
        if (user == null) {
            throw new ApiException("BAD_REQUEST", "Usuario requerido para conversión", HttpStatus.BAD_REQUEST);
        }

        List<String> unlocked = user.getUnlockedCapabilities() == null
                ? new ArrayList<>()
                : new ArrayList<>(user.getUnlockedCapabilities());

        List<String> favorites = user.getFavoriteLocationTypeIds() == null
                ? new ArrayList<>()
                : new ArrayList<>(user.getFavoriteLocationTypeIds());

        return new MapItUserResponse(
                user.getId(),
                user.getName(),
                user.getNick(),
                user.getEmail(),
                user.getUserType(),
                user.getLevel(),
                user.getXp(),
                unlocked,
                favorites,
                user.getAvatarUrl(),
                user.getPhone(),
                user.getCity(),
                user.getProvince(),
                user.getBio(),
                user.getBirthDate(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    /**
     * Convierte entidad JPA a modelo de dominio.
     * <p>
     * Maneja nulos en colecciones de forma segura.
     * </p>
     */
    private MapItUser convertToDomain(User user) {
        if (user == null) {
            return null;
        }

        MapItUser domain = new MapItUser(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getUserType());

        domain.setNick(user.getNick());
        domain.setEmailVerified(user.isEmailVerified());

        UserProfileDetails profileDetails = user.getProfileDetails();

        if (profileDetails != null) {
            domain.setLevel(profileDetails.getLevel());
            domain.setXp(profileDetails.getXp());
            domain.setAvatarUrl(profileDetails.getAvatarUrl());
            domain.setPhone(profileDetails.getPhone());
            domain.setCity(profileDetails.getCity());
            domain.setProvince(profileDetails.getProvince());
            domain.setBio(profileDetails.getBio());
            domain.setBirthDate(profileDetails.getBirthDate());
            domain.setCreatedAt(profileDetails.getCreatedAt());
            domain.setUpdatedAt(profileDetails.getUpdatedAt());
        }

        Set<String> capabilities = user.getUnlockedCapabilities() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(user.getUnlockedCapabilities());
        domain.setUnlockedCapabilities(capabilities);

        Set<String> favorites = user.getFavoriteLocationTypeIds() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(user.getFavoriteLocationTypeIds());
        domain.setFavoriteLocationTypeIds(favorites);

        return domain;
    }

    /**
     * Convierte modelo de dominio a entidad JPA.
     * <p>
     * Realiza normalización de email y convierte Set a List para JPA.
     * </p>
     */
    private User convertToEntity(MapItUser domain) {
        if (domain == null) {
            return null;
        }

        User user = new User();
        user.setId(domain.getId());
        user.setName(domain.getName());
        user.setNick(normalizeNick(domain.getNick()));
        user.setEmail(normalizeEmail(domain.getEmail()));
        user.setPasswordHash(domain.getPasswordHash());
        user.setUserType(domain.getUserType());

        UserProfileDetails profileDetails = new UserProfileDetails();
        profileDetails.setLevel(domain.getLevel());
        profileDetails.setXp(domain.getXp());
        profileDetails.setAvatarUrl(domain.getAvatarUrl());
        user.attachProfileDetails(profileDetails);

        user.setUnlockedCapabilities(domain.getUnlockedCapabilities() == null
                ? new ArrayList<>()
                : new ArrayList<>(domain.getUnlockedCapabilities()));

        user.setFavoriteLocationTypeIds(domain.getFavoriteLocationTypeIds() == null
                ? new ArrayList<>()
                : new ArrayList<>(domain.getFavoriteLocationTypeIds()));

        return user;
    }

    /**
     * Capacidades iniciales para usuarios INDIVIDUAL.
     */
    private List<String> getBasicCapabilities() {
        return new ArrayList<>(List.of(
                "max_publications_1",
                "weekly_limit_3",
                "basic_search"));
    }

    /**
     * Capacidades iniciales para usuarios PROFESSIONAL/ENTITY.
     */
    private List<String> getAllCapabilities() {
        return new ArrayList<>(List.of(
                "max_publications_10",
                "weekly_limit_unlimited",
                "advanced_search",
                "premium_locations",
                "analytics_access",
                "priority_support"));
    }

    /**
     * Valida que favoritos cumplan restricciones de nivel.
     */
    private void validateFavoriteLocationTypes(List<String> locationTypeIds, Integer userLevel) {
        for (String locationTypeId : locationTypeIds) {
            LocationType locationType = locationTypeRepository.findById(locationTypeId)
                    .orElseThrow(() -> new ApiException(
                            "BAD_REQUEST",
                            "locationTypeId inválido: " + locationTypeId,
                            HttpStatus.BAD_REQUEST));

            if ("profesional".equalsIgnoreCase(locationType.getName()) && (userLevel == null || userLevel < 10)) {
                throw new ApiException(
                        "UNPROCESSABLE_ENTITY",
                        "Requiere nivel 10 para añadir ubicaciones profesionales a favoritos",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }

    /**
     * Obtiene el perfil del usuario o lo crea y enlaza si aún no existe.
     */
    private UserProfileDetails getOrCreateProfileDetails(User user) {
        UserProfileDetails profileDetails = user.getProfileDetails();
        if (profileDetails == null) {
            profileDetails = new UserProfileDetails();
            user.attachProfileDetails(profileDetails);
        }
        return profileDetails;
    }

    /**
     * Normaliza email para consultas y persistencia.
     */
    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /**
     * Normaliza nick para consultas y persistencia. Solo trim: el nick es
     * case-sensitive ({@code @mapituser} y {@code @mapitUser} son usuarios distintos).
     */
    private String normalizeNick(String nick) {
        return nick == null ? "" : nick.trim();
    }

    /**
     * Genera un nick único para un usuario que no lo especificó en el registro.
     * <p>
     * Parte del nombre (o, si no aporta caracteres válidos, de la parte local del email),
     * lo reduce al alfabeto permitido por {@link emc.mapIt.dto.AuthRegisterRequest#nick()}
     * y añade un sufijo numérico incremental hasta encontrar uno libre.
     * </p>
     */
    private String generateUniqueNick(String name, String email) {
        String base = slugifyForNick(name);
        if (base.length() < 3) {
            String localPart = email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : "";
            base = slugifyForNick(localPart);
        }
        if (base.length() < 3) {
            base = "user";
        }
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }

        String candidate = base;
        int suffix = 0;
        while (userRepository.existsByNick(candidate)) {
            suffix++;
            String suffixStr = String.valueOf(suffix);
            String truncatedBase = base.substring(0, Math.min(base.length(), 30 - suffixStr.length()));
            candidate = truncatedBase + suffixStr;
        }
        return candidate;
    }

    /**
     * Reduce un texto libre al alfabeto permitido en el nick ({@code [A-Za-z0-9._-]}),
     * quitando acentos y cualquier otro carácter no soportado.
     */
    private String slugifyForNick(String input) {
        if (input == null) {
            return "";
        }
        String withoutAccents = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase().replaceAll("[^a-z0-9._-]", "");
    }

    /**
     * Limpia lista de strings: trim, sin nulls ni vacíos.
     */
    private List<String> sanitizeLongList(List<String> input) {
        if (input == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String value : input) {
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * Cambia la contraseña de un usuario autenticado.
     * <p>
     * Verifica la contraseña actual antes de aplicar la nueva. La nueva contraseña
     * se valida contra la política de contraseñas y se almacena con hash BCrypt.
     * </p>
     *
     * @param id              id del usuario
     * @param currentPassword contraseña actual en texto plano (para verificación)
     * @param newPassword     nueva contraseña en texto plano
     * @throws ApiException BAD_REQUEST   si algún argumento es nulo/vacío
     * @throws ApiException NOT_FOUND     si el usuario no existe
     * @throws ApiException UNAUTHORIZED  si la contraseña actual no coincide
     * @throws ApiException UNPROCESSABLE_ENTITY si la nueva contraseña no cumple la política
     */
    public void changePassword(String id, String currentPassword, String newPassword) {
        if (id == null || currentPassword == null || newPassword == null) {
            throw new ApiException("BAD_REQUEST", "Datos de cambio de contraseña incompletos", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException("UNAUTHORIZED", "La contraseña actual no es correcta", HttpStatus.UNAUTHORIZED);
        }

        passwordPolicyService.validate(newPassword, java.util.List.of(
                user.getName() != null ? user.getName() : "",
                user.getNick() != null ? user.getNick() : "",
                user.getEmail() != null ? user.getEmail() : ""
        ));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Contraseña cambiada para usuario id={}", id);
    }

    /**
     * Evalúa string nulo o en blanco.
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
