package emc.mapIt.service;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.dto.MapItUserResponse;
import emc.mapIt.dto.UserSearchResultResponse;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.repository.CapabilityDefinitionRepository;
import emc.mapIt.repository.LocationTypeRepository;
import emc.mapIt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cubre {@link UserService#toPublicResponse}: la ruta pública {@code GET /users/{id}} no debe
 * filtrar PII (email, teléfono, fecha de nacimiento, ciudad, provincia) de un usuario a nadie
 * que no sea el propio usuario o un ADMIN.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private LocationTypeRepository locationTypeRepository;
    @Mock private CapabilityDefinitionRepository capabilityDefinitionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicyService passwordPolicyService;

    private UserService userService;
    private MapItUser target;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, locationTypeRepository, capabilityDefinitionRepository,
                passwordEncoder, passwordPolicyService);

        target = new MapItUser();
        target.setId("user-1");
        target.setName("Ana");
        target.setNick("ana");
        target.setEmail("ana@test.com");
        target.setUserType(UserType.PARTICULAR);
        target.setPhone("600000000");
        target.setCity("Madrid");
        target.setProvince("Madrid");
        target.setBio("Hola, soy Ana");
        target.setBirthDate(LocalDate.of(1990, 1, 1));
        target.setAvatarUrl("https://example.com/ana.png");
        target.setLevel(3);
        target.setXp(120);
    }

    @Test
    void toPublicResponse_viewerEsElPropioUsuario_devuelveDatosCompletos() {
        MapItUserResponse response = userService.toPublicResponse(target, "user-1");

        assertThat(response.email()).isEqualTo("ana@test.com");
        assertThat(response.phone()).isEqualTo("600000000");
        assertThat(response.city()).isEqualTo("Madrid");
        assertThat(response.province()).isEqualTo("Madrid");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        verifyNoInteractions(userRepository);
    }

    @Test
    void toPublicResponse_viewerEsAdmin_devuelveDatosCompletos() {
        User admin = new User();
        admin.setId("admin-1");
        admin.setUserType(UserType.ADMIN);
        org.mockito.Mockito.when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));

        MapItUserResponse response = userService.toPublicResponse(target, "admin-1");

        assertThat(response.email()).isEqualTo("ana@test.com");
        assertThat(response.phone()).isEqualTo("600000000");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
    }

    @Test
    void toPublicResponse_viewerEsOtroUsuario_enmascaraCamposPrivados() {
        User other = new User();
        other.setId("user-2");
        other.setUserType(UserType.PARTICULAR);
        org.mockito.Mockito.when(userRepository.findById("user-2")).thenReturn(Optional.of(other));

        MapItUserResponse response = userService.toPublicResponse(target, "user-2");

        assertThat(response.email()).isNull();
        assertThat(response.phone()).isNull();
        assertThat(response.city()).isNull();
        assertThat(response.province()).isNull();
        assertThat(response.birthDate()).isNull();

        // Lo que sí es razonable mostrar en un perfil público se conserva.
        assertThat(response.id()).isEqualTo("user-1");
        assertThat(response.name()).isEqualTo("Ana");
        assertThat(response.nick()).isEqualTo("ana");
        assertThat(response.avatarUrl()).isEqualTo("https://example.com/ana.png");
        assertThat(response.bio()).isEqualTo("Hola, soy Ana");
        assertThat(response.level()).isEqualTo(3);
    }

    @Test
    void toPublicResponse_viewerAnonimo_enmascaraCamposPrivados() {
        MapItUserResponse response = userService.toPublicResponse(target, null);

        assertThat(response.email()).isNull();
        assertThat(response.phone()).isNull();
        assertThat(response.city()).isNull();
        assertThat(response.province()).isNull();
        assertThat(response.birthDate()).isNull();
        assertThat(response.name()).isEqualTo("Ana");
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchUsers_limitaEnLaQueryMongo_noEnMemoria() {
        User other = new User();
        other.setId("user-2");
        other.setName("Bea");
        other.setNick("bea");
        other.setEmail("bea@test.com");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(userRepository.findByNickContainingIgnoreCaseOrEmailContainingIgnoreCase(
                anyString(), anyString(), pageableCaptor.capture()))
                .thenReturn(List.of(other));

        List<UserSearchResultResponse> results = userService.searchUsers("bea", "user-1");

        assertThat(results).hasSize(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(21);
        verify(userRepository).findByNickContainingIgnoreCaseOrEmailContainingIgnoreCase(
                "bea", "bea", pageableCaptor.getValue());
    }

    @Test
    void searchUsers_enmascaraElEmailDeCadaResultado() {
        User other = new User();
        other.setId("user-2");
        other.setName("Eusebio");
        other.setNick("euse");
        other.setEmail("eusebio.montero@gmail.com");
        when(userRepository.findByNickContainingIgnoreCaseOrEmailContainingIgnoreCase(
                anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(other));

        List<UserSearchResultResponse> results = userService.searchUsers("euse", "user-1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).email()).isEqualTo("eu***ro@gmail.com");
    }

    @Test
    void searchUsers_enmascaraPorCompletoUnaParteLocalCorta() {
        User other = new User();
        other.setId("user-2");
        other.setName("Bea");
        other.setNick("bea");
        other.setEmail("bea@test.com");
        when(userRepository.findByNickContainingIgnoreCaseOrEmailContainingIgnoreCase(
                anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(other));

        List<UserSearchResultResponse> results = userService.searchUsers("bea", "user-1");

        assertThat(results.get(0).email()).isEqualTo("***@test.com");
    }

    // ── unlockCapability ────────────────────────────────────────

    @Test
    void unlockCapability_conIdDelCatalogoReal_laDesbloquea() {
        User user = new User();
        user.setId("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(capabilityDefinitionRepository.existsById("cap-mapas-ilimitados")).thenReturn(true);

        userService.unlockCapability("user-1", "cap-mapas-ilimitados");

        assertThat(user.getUnlockedCapabilities()).containsExactly("cap-mapas-ilimitados");
        verify(userRepository).save(user);
    }

    @Test
    void unlockCapability_conIdInventado_lanzaNotFoundYNoGuarda() {
        User user = new User();
        user.setId("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(capabilityDefinitionRepository.existsById("no-existe")).thenReturn(false);

        assertThatThrownBy(() -> userService.unlockCapability("user-1", "no-existe"))
                .isInstanceOf(ApiException.class);

        verify(userRepository, never()).save(any());
        assertThat(user.getUnlockedCapabilities()).isEmpty();
    }
}
