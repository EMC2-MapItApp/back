package emc.mapIt.geo;

import java.util.Optional;

/**
 * Puerto (arquitectura hexagonal) para resolver una ubicación aproximada a partir de una IP.
 * <p>
 * {@link GeoIpService} depende solo de esta interfaz, no del proveedor externo concreto que la
 * implementa hoy ({@link IpApiGeoLocationProvider}, ip-api.com). Devuelve
 * {@link Optional#empty()} cuando el proveedor no está disponible o no devuelve coordenadas
 * válidas — es el caso de uso, no el adaptador, quien decide aplicar un fallback.
 * </p>
 */
public interface GeoLocationProvider {

    /**
     * Resuelve la ubicación aproximada asociada a una IP.
     *
     * @param ip IP a geolocalizar (real o simulada)
     * @return ubicación resuelta, o {@link Optional#empty()} si el proveedor falla o no tiene
     *         datos válidos para esa IP
     */
    Optional<GeoLocation> locate(String ip);
}
