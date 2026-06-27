package emc.mapIt.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_profile_details")
public class UserProfileDetails {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 25)
    private String phone;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String province;

    @Column(length = 1000)
    private String bio;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column
    private Integer level;

    @Column
    private Integer xp;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Inicializa timestamps al crear el detalle de perfil.
     */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Actualiza timestamp de modificación antes de guardar cambios.
     */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and setters

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getPhone() {
        return phone;
    }

    public String getCity() {
        return city;
    }

    public String getProvince() {
        return province;
    }

    public String getBio() {
        return bio;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    /**
     * Nivel de gamificación del usuario.
     */
    public Integer getLevel() {
        return level;
    }

    /**
     * Experiencia acumulada del usuario.
     */
    public Integer getXp() {
        return xp;
    }

    /**
     * URL del avatar público del usuario.
     */
    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setPhone(String phone) {
        this.phone = normalize(phone);
    }

    public void setCity(String city) {
        this.city = normalize(city);
    }

    public void setProvince(String province) {
        this.province = normalize(province);
    }

    public void setBio(String bio) {
        this.bio = normalize(bio);
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    /**
     * Actualiza el nivel del perfil.
     */
    public void setLevel(Integer level) {
        this.level = level;
    }

    /**
     * Actualiza los puntos de experiencia del perfil.
     */
    public void setXp(Integer xp) {
        this.xp = xp;
    }

    /**
     * Actualiza el avatar del perfil normalizando valores en blanco a nulo.
     */
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = normalize(avatarUrl);
    }

    /**
     * Normaliza valores de texto para persistir nulos en vez de cadenas vacías.
     */
    private String normalize(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}