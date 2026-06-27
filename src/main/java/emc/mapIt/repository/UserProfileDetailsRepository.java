package emc.mapIt.repository;

import emc.mapIt.entity.UserProfileDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserProfileDetailsRepository extends JpaRepository<UserProfileDetails, UUID> {
}