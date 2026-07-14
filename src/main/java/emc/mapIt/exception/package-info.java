/**
 * Manejo de errores centralizado: {@link emc.mapIt.exception.ApiException} para errores
 * funcionales/de dominio (lleva {@code HttpStatus}, un {@code code} legible por máquina y un
 * mensaje), traducida junto con fallos de Bean Validation por
 * {@link emc.mapIt.exception.GlobalExceptionHandler} a un JSON de error consistente
 * {@code {"error": {code, message, status}}}.
 */
package emc.mapIt.exception;
