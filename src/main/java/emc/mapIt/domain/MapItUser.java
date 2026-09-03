package emc.mapIt.domain;

import emc.mapIt.entity.UserType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Modelo de dominio para usuario del sistema MapIt.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapItUser {

    private String id;
    private String name;
    private String nick;
    private String email;
    private String passwordHash;
    private UserType userType;
    private Integer level; // ✅ Añadido
    private Integer xp; // ✅ Añadido
    private String avatarUrl; // ✅ Añadido
    private Set<String> unlockedCapabilities;
    private Set<String> favoriteLocationTypeIds;
    private String phone;
    private String city;
    private String province;
    private String bio;
    private LocalDate birthDate;
    private Instant createdAt;
    private Instant updatedAt;
    // Fase 1 auth: AuthService.login() bloquea el acceso mientras sea false
    private boolean emailVerified;

    // Constructor principal
    public MapItUser(String id, String name, String email, String passwordHash, UserType userType) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.userType = userType;
        this.unlockedCapabilities = new LinkedHashSet<>();
        this.favoriteLocationTypeIds = new LinkedHashSet<>();

        // Inicializar valores por defecto según tipo de usuario
        if (userType == UserType.PARTICULAR) {
            this.level = 0;
            this.xp = 0;
        }
    }

    // ✅ Métodos de utilidad adicionales

    /**
     * Verifica si el usuario puede crear lugares.
     */
    public boolean canCreatePlaces() {
        return userType == UserType.PROFESSIONAL || userType == UserType.ENTITY;
    }

    /**
     * Añade una capacidad si no la tiene ya.
     */
    public boolean addCapability(String capability) {
        if (unlockedCapabilities == null) {
            unlockedCapabilities = new LinkedHashSet<>();
        }
        return unlockedCapabilities.add(capability);
    }

    /**
     * Verifica si tiene una capacidad específica.
     */
    public boolean hasCapability(String capability) {
        return unlockedCapabilities != null && unlockedCapabilities.contains(capability);
    }

    /**
     * Añade un tipo de ubicación favorito (solo para individuos).
     */
    public boolean addFavoriteLocationType(String locationTypeId) {
        if (userType != UserType.PARTICULAR) {
            return false;
        }

        if (favoriteLocationTypeIds == null) {
            favoriteLocationTypeIds = new LinkedHashSet<>();
        }

        return favoriteLocationTypeIds.add(locationTypeId);
    }

    @Override
    public String toString() {
        return "MapItUser{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", nick='" + nick + '\'' +
                ", email='" + email + '\'' +
                ", userType=" + userType +
                ", level=" + level +
                ", xp=" + xp +
                '}';
    }
}