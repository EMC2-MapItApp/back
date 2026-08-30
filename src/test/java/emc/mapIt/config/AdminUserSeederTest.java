package emc.mapIt.config;

import emc.mapIt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Cubre que {@link AdminUserSeeder} está excluido del perfil {@code prod}
 * ({@code @Profile("!prod")}): si las variables de entorno de Secret Manager no llegasen a
 * Cloud Run, este seeder no debe poder crear en producción una cuenta ADMIN con la password de
 * desarrollo por defecto.
 */
class AdminUserSeederTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, AdminUserSeeder.class);

    @Test
    void enPerfilProd_elSeederNoSeRegistra() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(AdminUserSeeder.class));
    }

    @Test
    void enPerfilDev_elSeederSiSeRegistra() {
        contextRunner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).hasSingleBean(AdminUserSeeder.class));
    }

    @Test
    void sinPerfilActivo_elSeederSiSeRegistra() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(AdminUserSeeder.class));
    }

    @Configuration
    static class TestConfig {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }
    }
}
