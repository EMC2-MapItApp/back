package emc.mapIt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.time.LocalDate;

@Document(collection = "user_profile_details")
public class UserProfileDetails {

    @Id
    private String userId;

    private String phone;

    private String city;

    private String province;

    private String bio;

    private LocalDate birthDate;

    private Integer level;

    private Integer xp;

    private String avatarUrl;

    private Instant createdAt;

    private Instant updatedAt;

    public void setUserId(String userId) {
        this.userId = userId;
    }

    // Getters and setters

    public String getUserId() {
        return userId;
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