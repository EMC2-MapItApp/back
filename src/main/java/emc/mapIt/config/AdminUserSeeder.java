package emc.mapIt.config;

import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea, si no existe todavía, la cuenta {@link UserType#ADMIN} usada para administrar datos de
 * referencia. Idempotente: si el email ya existe, no hace nada — seguro de ejecutar en cada
 * arranque. Email/nick/password se leen de entorno (nunca hardcodeados); los defaults solo
 * aplican en dev local, igual que el resto de secretos del proyecto (ver
 * {@code application-dev.yaml}).
 * <p>
 * Excluido del perfil {@code prod} a propósito ({@link Profile @Profile("!prod")}): si las
 * variables de entorno reales (Secret Manager) no llegasen a Cloud Run, este seeder no debe
 * poder crear en producción una cuenta ADMIN con la password de desarrollo por defecto, visible
 * en este mismo fichero. Si algún día hace falta sembrar el admin en un Mongo de producción
 * vacío (entorno nuevo, disaster recovery), hacerlo a mano o levantar el perfil {@code prod} sin
 * esta exclusión para ese despliegue puntual — no dejar el seeder activo en prod de forma
 * permanente.
 * </p>
 */
@Component
@Profile("!prod")
public class AdminUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminNick;
    private final String adminPassword;

    public AdminUserSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${mapit.admin.email:admin@mapit-web.com}") String adminEmail,
            @Value("${mapit.admin.nick:admin}") String adminNick,
            @Value("${mapit.admin.password:admin-dev-ONLY-FOR-LOCAL}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminNick = adminNick;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            User adminUser = new User();
            adminUser.setName("Admin");
            adminUser.setNick(adminNick);
            adminUser.setEmail(adminEmail);
            adminUser.setPasswordHash(passwordEncoder.encode(adminPassword));
            adminUser.setUserType(UserType.ADMIN);
            // Cuenta semilla de desarrollo: se da por verificada de entrada para no
            // depender del orden de arranque respecto a EmailVerificationBackfillSeeder.
            adminUser.setEmailVerified(true);

            userRepository.save(adminUser);
            log.info("Admin user created successfully: {}", adminEmail);
        } else {
            log.info("Admin user already exists: {}", adminEmail);
        }
    }
}
