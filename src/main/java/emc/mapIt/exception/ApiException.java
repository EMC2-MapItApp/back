package emc.mapIt.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepcion de dominio HTTP con codigo funcional y status.
 */
public class ApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    /**
     * Crea una excepcion API estandar.
     */
    public ApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /** @return codigo funcional del error. */
    public String getCode() {
        return code;
    }

    /** @return estado HTTP asociado. */
    public HttpStatus getStatus() {
        return status;
    }
}
