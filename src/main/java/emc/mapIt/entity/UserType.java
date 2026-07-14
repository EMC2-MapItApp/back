package emc.mapIt.entity;

/**
 * Tipo de cuenta de {@link User}. Solo {@link #PARTICULAR} tiene hoy un flujo de negocio
 * completo (auth, perfil, publicaciones); {@link #PROFESSIONAL} y {@link #ENTITY} existen en el
 * modelo pero su lógica específica está en progreso — no asumir que ya distinguen capacidades o
 * límites distintos en el código actual.
 */
public enum UserType {
    /** Usuario individual — único tipo con flujo de negocio completo hoy. */
    PARTICULAR,
    /** Profesional (autónomo, negocio local...). En progreso. */
    PROFESSIONAL,
    /** Entidad (ayuntamiento, asociación...). En progreso. */
    ENTITY,
    /** Cuenta administrativa, creada por {@code AdminUserSeeder} al arrancar. */
    ADMIN
}
