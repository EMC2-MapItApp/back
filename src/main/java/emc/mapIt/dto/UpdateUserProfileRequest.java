package emc.mapIt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class UpdateUserProfileRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 25)
    private String phone;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String province;

    @Size(max = 1000)
    private String bio;

    private LocalDate birthDate;

    public String getName() {
        return name;
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

    public void setName(String name) {
        this.name = name;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
}