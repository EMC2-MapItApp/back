package emc.mapIt.controller;

import emc.mapIt.dto.CreatePublicationRequest;
import emc.mapIt.dto.PublicationResponse;
import emc.mapIt.service.AuthService;
import emc.mapIt.service.PublicationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
     * Recupera una publicación por su identificador.
     *
     * @param id identificador de la publicación
     * @return vista serializable de la publicación
     */
    @GetMapping("/{id}")
    public PublicationResponse getById(@PathVariable Long id) {
        log.debug("Lectura de publicación id={}", id);
        return publicationService.findById(id);
    }

    /**
     * Lista publicaciones disponibles para el mapa.
     *
     * @param activeOnly si true, solo devuelve publicaciones activas
     * @return lista de publicaciones serializables
     */
    @GetMapping
    public List<PublicationResponse> getAll(@RequestParam(defaultValue = "true") boolean activeOnly) {
        log.debug("Lectura de publicaciones activeOnly={}", activeOnly);
        return publicationService.findAll(activeOnly);
    }

    /**
     * Lista publicaciones de un autor.
     *
     * @param authorId   identificador del autor
     * @param activeOnly si true, solo muestra activas
     * @return lista de publicaciones serializables
     */
    @GetMapping("/author/{authorId}")
    public List<PublicationResponse> getByAuthor(@PathVariable java.util.UUID authorId,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        log.debug("Lectura de publicaciones authorId={}, activeOnly={}", authorId, activeOnly);
        return publicationService.findByAuthor(authorId, activeOnly);
    }

    /**
     * Elimina definitivamente una publicación.
     *
     * @param id            identificador de la publicación
     * @param authorization cabecera Authorization con JWT
     * @return respuesta 204 sin contenido
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de borrado definitivo de publicación id={}", id);
        publicationService.deleteById(id, authService.requireUserId(authorization));
        return ResponseEntity.noContent().build();
    }
}
