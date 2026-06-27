package emc.mapIt.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @ElementCollection
    @CollectionTable(name = "user_unlocked_capabilities", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "capability_id", length = 100)
    private List<String> unlockedCapabilities = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_favorite_location_types", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "location_type_id", length = 100)
    private List<String> favoriteLocationTypeIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 30)
    private UserType userType;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserProfileDetails profileDetails;

    public void attachProfileDetails(UserProfileDetails details) {
        this.profileDetails = details;
        details.setUser(this);
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserProfileDetails getProfileDetails() {
        return profileDetails;
    }

    public List<String> getUnlockedCapabilities() {
        return unlockedCapabilities;
    }

    public void setUnlockedCapabilities(List<String> unlockedCapabilities) {
        this.unlockedCapabilities = unlockedCapabilities == null ? new ArrayList<>() : unlockedCapabilities;
    }

    public List<String> getFavoriteLocationTypeIds() {
        return favoriteLocationTypeIds;
    }

    public void setFavoriteLocationTypeIds(List<String> favoriteLocationTypeIds) {
        this.favoriteLocationTypeIds = favoriteLocationTypeIds == null ? new ArrayList<>() : favoriteLocationTypeIds;
    }
}
