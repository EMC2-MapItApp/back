package emc.mapIt.mapper;

import emc.mapIt.dto.CreatePublicationRequest;
import emc.mapIt.dto.PublicationResponse;
import emc.mapIt.entity.Publication;
import emc.mapIt.entity.PublicationVisibility;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationMapperTest {

    private final PublicationMapper mapper = new PublicationMapper();

    private static final BigDecimal ORIGIN_LAT = BigDecimal.valueOf(40.4168);
    private static final BigDecimal ORIGIN_LNG = BigDecimal.valueOf(-3.7038);

    @Test
    void toEntity_ubicacionExacta_conservaCoordenadasOriginales() {
        Publication publication = mapper.toEntity(request(true), null, null);

        assertThat(publication.getLat()).isEqualByComparingTo(ORIGIN_LAT);
        assertThat(publication.getLng()).isEqualByComparingTo(ORIGIN_LNG);
    }

    @Test
    void toEntity_sinExactLocationEnMetadata_seComportaComoExacta() {
        CreatePublicationRequest request = new CreatePublicationRequest(
                null, "loc-1", "Título", null,
                LocalDateTime.now(), null,
                ORIGIN_LAT, ORIGIN_LNG, 0, Map.of(), null, null);

        Publication publication = mapper.toEntity(request, null, null);

        assertThat(publication.getLat()).isEqualByComparingTo(ORIGIN_LAT);
        assertThat(publication.getLng()).isEqualByComparingTo(ORIGIN_LNG);
    }

    @RepeatedTest(20)
    void toEntity_ubicacionAproximada_desplazaDentroDeRadioDe5Km() {
        Publication publication = mapper.toEntity(request(false), null, null);

        assertThat(publication.getLat()).isNotEqualByComparingTo(ORIGIN_LAT);
        assertThat(publication.getLng()).isNotEqualByComparingTo(ORIGIN_LNG);

        double distanceKm = haversineKm(
                ORIGIN_LAT.doubleValue(), ORIGIN_LNG.doubleValue(),
                publication.getLat().doubleValue(), publication.getLng().doubleValue());

        assertThat(distanceKm).isLessThanOrEqualTo(5.0);
    }

    // ── toResponse (enmascarado de contenido privado) ────────────

    private Publication privatePublication() {
        Publication publication = new Publication();
        publication.setId("pub-1");
        publication.setAuthorId("author-1");
        publication.setVisibility(PublicationVisibility.PRIVATE);
        publication.setTitle("Título privado");
        publication.setDescription("Descripción privada");
        publication.setLat(ORIGIN_LAT);
        publication.setLng(ORIGIN_LNG);
        publication.setMetadata(Map.of("slots", 10));
        return publication;
    }

    @Test
    void toResponse_privadaSinAcceso_ocultaContenidoPeroConservaElPin() {
        PublicationResponse response = mapper.toResponse(privatePublication(), 3L, false, false);

        assertThat(response.title()).isNull();
        assertThat(response.description()).isNull();
        assertThat(response.metadata()).isNull();
        assertThat(response.occupiedSlots()).isNull();
        assertThat(response.hasAccess()).isFalse();
        assertThat(response.accessRequestPending()).isFalse();
        // El pin debe poder dibujarse en el mapa aunque el contenido esté oculto.
        assertThat(response.lat()).isEqualByComparingTo(ORIGIN_LAT);
        assertThat(response.lng()).isEqualByComparingTo(ORIGIN_LNG);
    }

    @Test
    void toResponse_privadaConAcceso_devuelveContenidoCompleto() {
        PublicationResponse response = mapper.toResponse(privatePublication(), 3L, true, null);

        assertThat(response.title()).isEqualTo("Título privado");
        assertThat(response.description()).isEqualTo("Descripción privada");
        assertThat(response.metadata()).containsEntry("slots", 10);
        assertThat(response.occupiedSlots()).isEqualTo(3L);
        assertThat(response.hasAccess()).isTrue();
    }

    @Test
    void toResponse_publica_hasAccessSiempreTrueYSinAccessRequestPending() {
        Publication publication = new Publication();
        publication.setId("pub-2");
        publication.setVisibility(PublicationVisibility.PUBLIC);
        publication.setTitle("Evento público");

        PublicationResponse response = mapper.toResponse(publication, 0L, true, true);

        assertThat(response.title()).isEqualTo("Evento público");
        assertThat(response.hasAccess()).isTrue();
        assertThat(response.accessRequestPending()).isNull();
    }

    private CreatePublicationRequest request(boolean exactLocation) {
        return new CreatePublicationRequest(
                null, "loc-1", "Título", null,
                LocalDateTime.now(), null,
                ORIGIN_LAT, ORIGIN_LNG, 0,
                Map.of("exactLocation", exactLocation), null, null);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
