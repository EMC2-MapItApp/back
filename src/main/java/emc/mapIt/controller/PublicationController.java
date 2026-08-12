package emc.mapIt.controller;

import emc.mapIt.dto.ChangeVisibilityRequest;
import emc.mapIt.dto.CreatePublicationRequest;
import emc.mapIt.dto.EnrollmentDto;
import emc.mapIt.dto.PublicationAccessRequestResponse;
import emc.mapIt.dto.PublicationEnrollmentResponse;
import emc.mapIt.dto.PublicationResponse;
import emc.mapIt.service.AuthService;
import emc.mapIt.service.PublicationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

/**
 * Controlador REST para la persistencia y consulta de publicaciones.
 */
@RestController
@RequestMapping("/api/v1/publications")
public class PublicationController {

    private static final Logger log = LoggerFactory.getLogger(PublicationController.class);

    private final PublicationService publicationService;
    private final AuthService authService;

    /**
     * Constructor para inyección de dependencias.
     */
    public PublicationController(PublicationService publicationService, AuthService authService) {
        this.publicationService = publicationService;
        this.authService = authService;
    }

    /**
     * Crea una nueva actividad/evento persistida en base de datos.
     *
     * @param request       payload de creación
     * @param authorization cabecera Authorization con JWT
     * @return publicación creada
     */
    @PostMapping
    public ResponseEntity<PublicationResponse> create(@Valid @RequestBody CreatePublicationRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de creación de publicación");
        PublicationResponse response = publicationService.createEvent(authService.requireUserId(authorization),
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Recupera una publicación por su identificador. Ruta pública (ver {@code SecurityConfig}):
     * accesible sin sesión, pero si hay una válida se usa para calcular pertenencia a grupo en
     * publicaciones privadas.
     *
     * @param id            identificador de la publicación
     * @param authorization cabecera Authorization con JWT, opcional
     * @return vista serializable de la publicación
     */
    @GetMapping("/{id}")
    public PublicationResponse getById(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de publicación id={}", id);
        return publicationService.findById(id, authService.resolveUserIdOrNull(authorization));
    }

    /**
     * Lista publicaciones disponibles para el mapa. Ruta pública, misma lógica de viewer opcional
     * que {@link #getById}.
     *
     * @param activeOnly    si true, solo devuelve publicaciones activas
     * @param authorization cabecera Authorization con JWT, opcional
     * @return lista de publicaciones serializables
     */
    @GetMapping
    public List<PublicationResponse> getAll(@RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de publicaciones activeOnly={}", activeOnly);
        return publicationService.findAll(activeOnly, authService.resolveUserIdOrNull(authorization));
    }

    /**
     * Lista publicaciones de un autor. Ruta pública, misma lógica de viewer opcional que
     * {@link #getById}.
     *
     * @param authorId      identificador del autor
     * @param activeOnly    si true, solo muestra activas
     * @param authorization cabecera Authorization con JWT, opcional
     * @return lista de publicaciones serializables
     */
    @GetMapping("/author/{authorId}")
    public List<PublicationResponse> getByAuthor(@PathVariable String authorId,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de publicaciones authorId={}, activeOnly={}", authorId, activeOnly);
        return publicationService.findByAuthor(authorId, activeOnly, authService.resolveUserIdOrNull(authorization));
    }

    /**
     * Elimina definitivamente una publicación.
     *
     * @param id            identificador de la publicación
     * @param authorization cabecera Authorization con JWT
     * @return respuesta 204 sin contenido
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de borrado definitivo de publicación id={}", id);
        publicationService.deleteById(id, authService.requireUserId(authorization));
        return ResponseEntity.noContent().build();
    }

    /**
     * Cambia la visibilidad de una publicación existente. Solo el autor o un ADMIN pueden
     * hacerlo; bloqueado si al pasar a privada hay inscritos ajenos al grupo destino.
     *
     * @param id            identificador de la publicación
     * @param request       visibilidad destino y, si aplica, grupo
     * @param authorization cabecera Authorization con JWT
     * @return publicación actualizada
     */
    @PatchMapping("/{id}/visibility")
    public PublicationResponse changeVisibility(@PathVariable String id,
            @Valid @RequestBody ChangeVisibilityRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de cambio de visibilidad publicationId={}", id);
        return publicationService.changeVisibility(id, authService.requireUserId(authorization), request);
    }

    /**
     * Inscribe al usuario autenticado en una publicación.
     *
     * @param id            identificador de la publicación
     * @param authorization cabecera Authorization con JWT
     * @return estado actualizado de ocupación
     */
    @PostMapping("/{id}/enroll")
    public ResponseEntity<PublicationEnrollmentResponse> enroll(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de inscripción publicationId={}", id);
        PublicationEnrollmentResponse response = publicationService.enroll(id,
                authService.requireUserId(authorization));
        return ResponseEntity.ok(response);
    }

    /**
     * Solicita apuntarse a una publicación privada de la que no se tiene acceso todavía. La
     * aprueba el autor de la publicación (ver {@link #getPendingAccessRequests}), no el
     * organizador de ningún grupo.
     *
     * @param id            identificador de la publicación privada
     * @param authorization cabecera Authorization con JWT
     * @return solicitud de acceso creada
     */
    @PostMapping("/{id}/access-requests")
    public ResponseEntity<PublicationAccessRequestResponse> requestAccess(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de acceso publicationId={}", id);
        PublicationAccessRequestResponse response = publicationService.requestAccess(id,
                authService.requireUserId(authorization));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista las solicitudes de acceso pendientes de una publicación. Solo el autor (o un ADMIN)
     * puede verlas.
     *
     * @param id            identificador de la publicación
     * @param authorization cabecera Authorization con JWT
     * @return solicitudes pendientes
     */
    @GetMapping("/{id}/access-requests")
    public List<PublicationAccessRequestResponse> getPendingAccessRequests(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de solicitudes de acceso publicationId={}", id);
        return publicationService.getPendingAccessRequests(id, authService.requireUserId(authorization));
    }

    /**
     * Acepta una solicitud de acceso. Solo el autor de la publicación (o un ADMIN) puede hacerlo.
     *
     * @param requestId     identificador de la solicitud
     * @param authorization cabecera Authorization con JWT
     * @return solicitud actualizada
     */
    @PostMapping("/access-requests/{requestId}/accept")
    public PublicationAccessRequestResponse acceptAccessRequest(@PathVariable String requestId,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Aceptando solicitud de acceso requestId={}", requestId);
        return publicationService.acceptAccessRequest(requestId, authService.requireUserId(authorization));
    }

    /**
     * Rechaza una solicitud de acceso. Solo el autor de la publicación (o un ADMIN) puede hacerlo.
     *
     * @param requestId     identificador de la solicitud
     * @param authorization cabecera Authorization con JWT
     * @return solicitud actualizada
     */
    @PostMapping("/access-requests/{requestId}/reject")
    public PublicationAccessRequestResponse rejectAccessRequest(@PathVariable String requestId,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Rechazando solicitud de acceso requestId={}", requestId);
        return publicationService.rejectAccessRequest(requestId, authService.requireUserId(authorization));
    }

    /**
     * Lista los usuarios inscritos en una publicación. Requiere sesión (ver
     * {@code SecurityConfig}: no está en la lista de lecturas públicas); en publicaciones
     * privadas solo pueden verla los miembros del grupo.
     *
     * @param id            identificador de la publicación
     * @param authorization cabecera Authorization con JWT
     * @return lista de inscritos con sus nombres
     */
    @GetMapping("/{id}/enrollments")
    public List<EnrollmentDto> getEnrollments(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de inscritos publicationId={}", id);
        return publicationService.getEnrollments(id, authService.requireUserId(authorization));
    }

    /**
     * Desapunta al usuario autenticado de una publicación.
     *
     * @param id            identificador de la publicación
     * @param authorization cabecera Authorization con JWT
     * @return sin contenido
     */
    @DeleteMapping("/{id}/unenroll")
    public ResponseEntity<Void> unenroll(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de desapuntamiento publicationId={}", id);
        publicationService.unenroll(id, authService.requireUserId(authorization));
        return ResponseEntity.noContent().build();
    }
}
