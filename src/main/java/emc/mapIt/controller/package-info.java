/**
 * Endpoints REST bajo {@code /api/v1/...}. Controllers finos: validan el request (Bean
 * Validation) y delegan toda la lógica de negocio en {@link emc.mapIt.service}; no construyen
 * respuestas de error manualmente (eso lo centraliza
 * {@link emc.mapIt.exception.GlobalExceptionHandler}).
 */
package emc.mapIt.controller;
