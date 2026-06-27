package emc.mapIt.dto;

/**
 * Envelope de error estandarizado para respuestas REST.
 */
public record ErrorResponse(ErrorBody error) {

    /**
     * Cuerpo detallado de error.
     */
    public record ErrorBody(String code, String message, int status) {
    }
}
