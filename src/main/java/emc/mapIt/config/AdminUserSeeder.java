package emc.mapIt.config;

import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        final String ADMIN_EMAIL = "eusebio.montero@gmail.com";
        final String ADMIN_NICK = "admin";
        final String ADMIN_PASSWORD = "12345678";

        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            User adminUser = new User();
            adminUser.setName("Eusebio Montero");
            adminUser.setNick(ADMIN_NICK);
            adminUser.setEmail(ADMIN_EMAIL);
            adminUser.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
            adminUser.setUserType(UserType.ADMIN);
            // Cuenta semilla de desarrollo: se da por verificada de entrada para no
            // depender del orden de arranque respecto a EmailVerificationBackfillSeeder.
            adminUser.setEmailVerified(true);

            userRepository.save(adminUser);
            log.info("Admin user created successfully: {}", ADMIN_EMAIL);
        } else {
            log.info("Admin user already exists: {}", ADMIN_EMAIL);
        }
    }
}
