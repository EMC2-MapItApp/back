package emc.mapIt.service;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.dto.MapItUserResponse;
import emc.mapIt.dto.UserPatchRequest;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserProfileDetails;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de aplicación para gestión de usuarios con persistencia JPA.
 * <p>
 * Responsabilidades:
 * </p>
 * <ul>
 *   <li>Crear usuarios validando unicidad de email</li>
 *   <li>Consultar usuarios por id/email con errores de dominio</li>
 *   <li>Actualizar campos editables del perfil</li>
 *   <li>Desbloquear capacidades</li>
 *   <li>Convertir entre modelo de dominio, entidad y DTO de respuesta</li>
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

    private final UserRepository userRepository;

    /**
     * Constructor para inyección de dependencias.
     *
     * @param userRepository repositorio de usuarios
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Crea un usuario nuevo persistiéndolo en base de datos.
     * <p>
     * Reglas de configuración inicial por tipo:
     * </p>
     * <ul>
     *   <li>INDIVIDUAL: nivel/XP iniciales en 0, capacidades básicas, sin favoritos</li>
     *   <li>PROFESSIONAL/ENTITY: nivel/XP nulos, capacidades ampliadas, sin favoritos</li>
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
        if (isBlank(candidate.getName()) || isBlank(candidate.getEmail()) || isBlank(candidate.getPasswordHash()) || candidate.getUserType() == null) {
            throw new ApiException("BAD_REQUEST", "Datos incompletos para crear usuario", HttpStatus.BAD_REQUEST);
        }

        String normalizedEmail = normalizeEmail(candidate.getEmail());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException("CONFLICT", "Ya existe un usuario con ese email", HttpStatus.CONFLICT);
        }

        // Asegurar normalización consistente antes de persistir
        candidate.setName(candidate.getName().trim());
        candidate.setEmail(normalizedEmail);
        if (candidate.getProfileDetails() == null) {
            log.debug("Inicializando UserProfileDetails para email={}", normalizedEmail);
            candidate.attachProfileDetails(new UserProfileDetails());
        } else if (candidate.getProfileDetails().getUser() == null) {
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
     * Busca un usuario por UUID.
     *
     * @param id identificador único del usuario
     * @return usuario encontrado
     * @throws ApiException con código NOT_FOUND si no existe
     */
    @Transactional(readOnly = true)
    public MapItUser getByIdOrThrow(UUID id) {
        if (id == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findByIdWithProfile(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));
        return convertToDomain(user);
    }

    /**
     * Actualiza campos editables de perfil del usuario.
     * <p>
     * Campos soportados:
     * </p>
     * <ul>
     *   <li>{@code name}: actualiza si no está en blanco</li>
     *   <li>{@code avatarUrl}: actualiza URL (puede ser nulo)</li>
     *   <li>{@code favoriteLocationTypeIds}: solo para tipo INDIVIDUAL, con validación de nivel</li>
     * </ul>
     *
     * @param id    id del usuario a actualizar
     * @param patch payload parcial (todos los campos opcionales)
     * @return usuario actualizado desde BD
     * @throws ApiException con código BAD_REQUEST si id es nulo
     * @throws ApiException con código NOT_FOUND si el usuario no existe
     * @throws ApiException con código UNPROCESSABLE_ENTITY si favoritos incumplen regla de nivel
     */
    public MapItUser updateUser(UUID id, UserPatchRequest patch) {
        if (id == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByIdWithProfile(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (patch == null) {
            return convertToDomain(user);
        }

        if (patch.name() != null && !patch.name().isBlank()) {
            user.setName(patch.name().trim());
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
            List<String> sanitizedFavorites = sanitizeStringList(patch.favoriteLocationTypeIds());
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
    public void unlockCapability(UUID id, String capabilityId) {
        if (id == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByIdWithProfile(id)
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
        user.getUpdatedAt()
);
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
                user.getUserType()
        );

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
                "basic_search"
        ));
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
                "priority_support"
        ));
    }

    /**
     * Valida que favoritos cumplan restricciones de nivel.
     */
    private void validateFavoriteLocationTypes(List<String> locationTypeIds, Integer userLevel) {
        for (String locationTypeId : locationTypeIds) {
            if (locationTypeId.endsWith("-profesional") && (userLevel == null || userLevel < 10)) {
                throw new ApiException(
                        "UNPROCESSABLE_ENTITY",
                        "Requiere nivel 10 para añadir ubicaciones profesionales a favoritos",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
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
     * Limpia lista de strings: trim, sin nulls ni vacíos.
     */
    private List<String> sanitizeStringList(List<String> input) {
        if (input == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String value : input) {
            if (!isBlank(value)) {
                result.add(value.trim());
            }
        }
        return result;
    }

    /**
     * Evalúa string nulo o en blanco.
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
