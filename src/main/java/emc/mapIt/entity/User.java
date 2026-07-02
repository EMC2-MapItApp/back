package emc.mapIt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String name;

    private String email;

    private String passwordHash;

    private List<String> unlockedCapabilities = new ArrayList<>();

    private List<Long> favoriteLocationTypeIds = new ArrayList<>();

    private UserType userType;

    @DBRef(lazy = true)
    private UserProfileDetails profileDetails;

    public void attachProfileDetails(UserProfileDetails details) {
        this.profileDetails = details;
        if (details != null) details.setUserId(this.id);
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public List<Long> getFavoriteLocationTypeIds() {
        return favoriteLocationTypeIds;
    }

    public void setFavoriteLocationTypeIds(List<Long> favoriteLocationTypeIds) {
        this.favoriteLocationTypeIds = favoriteLocationTypeIds == null ? new ArrayList<>() : favoriteLocationTypeIds;
    }
}
