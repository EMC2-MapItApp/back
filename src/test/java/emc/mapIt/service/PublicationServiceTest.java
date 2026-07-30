package emc.mapIt.service;

import emc.mapIt.entity.Publication;
import emc.mapIt.mapper.PublicationMapper;
import emc.mapIt.repository.LocationTypeRepository;
import emc.mapIt.repository.PublicationEnrollmentRepository;
import emc.mapIt.repository.PublicationRepository;
import emc.mapIt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTest {

    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    @Mock private PublicationRepository publicationRepository;
    @Mock private PublicationEnrollmentRepository publicationEnrollmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private LocationTypeRepository locationTypeRepository;
    @Mock private PublicationMapper publicationMapper;

    private PublicationService publicationService;

    @BeforeEach
    void setUp() {
        publicationService = new PublicationService(
                publicationRepository, publicationEnrollmentRepository,
                userRepository, locationTypeRepository, publicationMapper);
    }

    // ── deleteExpiredPublications ───────────────────────────────

    @Test
    void deleteExpiredPublications_conCaducadasHaceMasDeTresMeses_lasElimina() {
        Publication caducada = new Publication();
        caducada.setId("pub-1");
        caducada.setEndDate(ZonedDateTime.now(MADRID_ZONE).minusMonths(4));

        when(publicationRepository.findExpiredSince(any())).thenReturn(List.of(caducada));

        publicationService.deleteExpiredPublications();

        ArgumentCaptor<ZonedDateTime> cutoffCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(publicationRepository).findExpiredSince(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBefore(ZonedDateTime.now(MADRID_ZONE).minusMonths(2));

        verify(publicationRepository).deleteAll(List.of(caducada));
    }

    @Test
    void deleteExpiredPublications_sinCaducadas_noBorraNada() {
        when(publicationRepository.findExpiredSince(any())).thenReturn(List.of());

        publicationService.deleteExpiredPublications();

        verify(publicationRepository, never()).deleteAll(any());
    }
}
