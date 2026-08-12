package emc.mapIt.notifications;

import emc.mapIt.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canal para que un usuario autenticado informe de errores o sugerencias al equipo de desarrollo.
 * No hay ninguna ruta declarada pública en {@code SecurityConfig} para {@code /api/v1/feedback} —
 * requiere sesión, como el resto de "enviar mensaje a alguien" del proyecto
 * ({@code GroupController.notifyOrganizer}/{@code contactMembers}).
 */
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    private final FeedbackService feedbackService;
    private final AuthService authService;

    public FeedbackController(FeedbackService feedbackService, AuthService authService) {
        this.feedbackService = feedbackService;
        this.authService = authService;
    }

    /**
     * Envía un feedback (bug/sugerencia/otro) del usuario autenticado a las cuentas administradoras.
     *
     * @param request       categoría, asunto y mensaje
     * @param authorization cabecera Authorization con JWT
     * @return sin contenido
     */
    @PostMapping
    public ResponseEntity<Void> send(@Valid @RequestBody DeveloperFeedbackRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de feedback category={}", request.category());
        feedbackService.send(authService.requireUserId(authorization), request);
        return ResponseEntity.noContent().build();
    }
}
