package emc.mapIt.exception;

import emc.mapIt.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
/**
 * Traduce excepciones a respuestas HTTP de error consistentes.
 */
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    /**
     * Maneja errores funcionales del dominio.
     */
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.warn("API error code={} status={} message={}", ex.getCode(), ex.getStatus().value(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(new ErrorResponse.ErrorBody(ex.getCode(), ex.getMessage(), ex.getStatus().value())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    /**
     * Maneja validaciones de request body.
     */
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .collect(Collectors.joining(", "));
        String message = details.isBlank() ? "Request body invalido" : "Campos invalidos: " + details;
        log.warn("Validation error fields={}", details);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(new ErrorResponse.ErrorBody("BAD_REQUEST", message, HttpStatus.BAD_REQUEST.value())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    /**
     * Maneja body ausente o JSON malformado en la peticion.
     */
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Request body no legible: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(new ErrorResponse.ErrorBody("BAD_REQUEST", "Request body ausente o malformado", HttpStatus.BAD_REQUEST.value())));
    }

    @ExceptionHandler(Exception.class)
    /**
     * Maneja errores no controlados.
     */
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(new ErrorResponse.ErrorBody(
                        "INTERNAL_ERROR",
                        "Error interno inesperado",
                        HttpStatus.INTERNAL_SERVER_ERROR.value())));
    }
}
