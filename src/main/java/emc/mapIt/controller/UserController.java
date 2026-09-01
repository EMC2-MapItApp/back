package emc.mapIt.controller;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.dto.ChangePasswordRequest;
import emc.mapIt.dto.UserPatchRequest;
import emc.mapIt.dto.MapItUserResponse;
import emc.mapIt.dto.PublicationResponse;
import emc.mapIt.dto.UserSearchResultResponse;
import emc.mapIt.exception.ApiException;
import emc.mapIt.service.AuthService;
import emc.mapIt.service.PublicationService;
import emc.mapIt.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para la gestión de usuarios en la plataforma MapIt.
 * <p>
 * Proporciona endpoints para consultar perfiles, actualizar información
 * personal,
 * gestionar capacidades desbloqueadas y administrar recursos relacionados con
 * el usuario
 * como favoritos, estadísticas y publicaciones.
 * </p>
 *
 * <h3>Tipos de Usuario</h3>
 * <ul>
 * <li><strong>INDIVIDUAL</strong>: Usuario con gamificación (nivel, XP,
 * favoritos)</li>
 * <li><strong>PROFESSIONAL</strong>: Usuario profesional que puede crear
 * lugares</li>
 * <li><strong>ENTITY</strong>: Entidad/empresa que puede crear lugares</li>
 * </ul>
 *
 * <h3>Autenticación</h3>
 * <p>
 * Muchos endpoints requieren autenticación mediante token JWT en el header
 * <code>Authorization</code>. Los usuarios solo pueden modificar su propia
 * información.
 * </p>
 *
 * @author MapIt Development Team
 * @version 1.0.0
 * @since 2026-06-15
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final AuthService authService;
    private final PublicationService publicationService;

    /**
     * Constructor para inyección de dependencias.
     *
     * @param userService servicio de gestión de usuarios
     * @param authService servicio de autenticación
     */
    public UserController(UserService userService, AuthService authService, PublicationService publicationService) {
        this.userService = userService;
        this.authService = authService;
        this.publicationService = publicationService;
    }

    /**
     * Busca usuarios por coincidencia parcial de nick o email.
     * <p>
     * Usado por el buscador de invitación a grupos. Requiere sesión (a diferencia del resto de
     * lecturas de este controller, que son públicas) — devolver nick/email de otros usuarios no
     * debe quedar accesible sin autenticar. Excluye al propio usuario autenticado del resultado.
     * </p>
     *
     * @param query         texto de búsqueda (nick o email); menos de 2 caracteres devuelve
     *                      lista vacía
     * @param authorization header de autorización con token JWT
     * @return usuarios que coinciden, máximo 20
     */
    @GetMapping("/search")
    public List<UserSearchResultResponse> search(@RequestParam(name = "q", defaultValue = "") String query,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        String requesterId = authService.requireUserId(authorization);
        log.debug("Búsqueda de usuarios query={} requesterId={}", query, requesterId);
        return userService.searchUsers(query, requesterId);
    }

    /**
     * Recupera el perfil de un usuario por su identificador. Ruta pública (ver
     * {@code SecurityConfig}): accesible sin sesión, pero los campos de contacto/perfil privados
     * ({@code email}, {@code phone}, {@code birthDate}, {@code city}, {@code province}) solo se
     * incluyen si quien consulta es el propio usuario o un ADMIN — para cualquier otro viewer
     * (incluido anónimo) llegan a {@code null}. Ver {@link UserService#toPublicResponse}.
     *
     * @param id            identificador único del usuario
     * @param authorization header de autorización con token JWT, opcional
     * @return {@link MapItUserResponse} con información del usuario, enmascarada según el viewer
     * @throws ApiException con código NOT_FOUND si el usuario no existe
     *
     * @see MapItUserResponse
     * @see UserService#getByIdOrThrow(UUID)
     */
    @GetMapping("/{id}")
    public MapItUserResponse getById(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de usuario id={}", id);
        MapItUser user = userService.getByIdOrThrow(id);
        String viewerId = authService.resolveUserIdOrNull(authorization);
        return userService.toPublicResponse(user, viewerId);
    }

    /**
     * Actualiza campos editables del perfil del usuario.
     * <p>
     * Permite modificar nombre, avatar y ubicaciones favoritas (solo para usuarios
     * individuales).
     * El usuario debe estar autenticado y solo puede modificar su propio perfil.
     * </p>
     *
     * @param id            identificador del usuario a actualizar
     * @param request       datos a actualizar, todos los campos son opcionales
     * @param authorization header de autorización con token JWT
     * @return {@link MapItUserResponse} con los datos actualizados
     * @throws ApiException con código FORBIDDEN si intenta modificar otro usuario
     * @throws ApiException con código NOT_FOUND si el usuario no existe
     * @throws ApiException con código UNPROCESSABLE_ENTITY si los datos son
     *                      inválidos
     *
     * @see UserPatchRequest
     * @see UserService#updateUser(UUID, UserPatchRequest)
     */
    @PatchMapping("/{id}")
    public MapItUserResponse patchUser(@PathVariable String id,
            @Valid @RequestBody UserPatchRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Actualización de usuario id={}", id);
        ensureSameUser(id, authorization);
        log.info("Usuario a actualizar: {}", request);
        MapItUser updatedUser = userService.updateUser(id, request);
        log.info("Usuario actualizado correctamente: {}", updatedUser);
        return userService.toResponse(updatedUser);
    }

    /**
     * Lista las capacidades desbloqueadas del usuario.
     * <p>
     * Las capacidades determinan qué funcionalidades puede usar el usuario
     * (límites de publicaciones, búsquedas avanzadas, etc.).
     * </p>
     *
     * @param id identificador del usuario
     * @return lista de identificadores de capacidades desbloqueadas
     * @throws ApiException con código NOT_FOUND si el usuario no existe
     *
     * @see UserService#getByIdOrThrow(UUID)
     */
    @GetMapping("/{id}/capabilities")
    public List<String> getCapabilities(@PathVariable String id) {
        log.debug("Lectura de capacidades usuario id={}", id);
        MapItUser user = userService.getByIdOrThrow(id);
        return userService.toResponse(user).unlockedCapabilities();
    }

    /**
     * Desbloquea una nueva capacidad para el usuario.
     * <p>
     * Solo usuarios autenticados pueden desbloquear capacidades en su propio
     * perfil.
     * La capacidad se añade si es válida y no estaba ya desbloqueada.
     * </p>
     *
     * @param id            identificador del usuario
     * @param capabilityId  identificador de la capacidad a desbloquear
     * @param authorization header de autorización con token JWT
     * @return lista actualizada de capacidades desbloqueadas
     * @throws ApiException con código FORBIDDEN si intenta modificar otro usuario
     * @throws ApiException con código NOT_FOUND si el usuario no existe
     * @throws ApiException con código BAD_REQUEST si el capabilityId es inválido
     *
     * @see UserService#unlockCapability(UUID, String)
     */
    @PostMapping("/{id}/capabilities/{capabilityId}")
    public List<String> unlockCapability(@PathVariable String id,
            @PathVariable String capabilityId,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Desbloqueo capability={} para usuario id={}", capabilityId, id);
        ensureSameUser(id, authorization);

        userService.unlockCapability(id, capabilityId);
        MapItUser updatedUser = userService.getByIdOrThrow(id);
        return userService.toResponse(updatedUser).unlockedCapabilities();
    }

    /**
     * Devuelve las publicaciones creadas por el usuario.
     * <p>
     * Devuelve las publicaciones persistidas en base de datos y permite filtrar
     * por publicaciones activas.
     * </p>
     *
     * @param id            identificador del usuario
     * @param activeOnly    si true, solo devuelve publicaciones activas (por defecto:
     *                      true)
     * @param authorization cabecera Authorization con JWT, opcional — ruta pública; el viewer
     *                      resuelto determina si las publicaciones {@code PRIVATE} del usuario se
     *                      devuelven con contenido enmascarado o excluidas (anónimo)
     * @return lista de publicaciones del usuario
     * @throws ApiException con código NOT_FOUND si el usuario no existe
     */
    @GetMapping("/{id}/publications")
    public List<PublicationResponse> getUserPublications(@PathVariable String id,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de publicaciones usuario id={}, activeOnly={}", id, activeOnly);
        userService.getByIdOrThrow(id);
        return publicationService.findByAuthor(id, activeOnly, authService.resolveUserIdOrNull(authorization));
    }

    /**
     * Cambia la contraseña del usuario autenticado.
     * <p>
     * Requiere la contraseña actual para verificar la identidad antes de aplicar
     * el cambio. La nueva contraseña se valida contra la política de contraseñas.
     * </p>
     *
     * @param id            identificador del usuario
     * @param request       {@link ChangePasswordRequest} con contraseña actual y nueva
     * @param authorization header de autorización con token JWT
     * @throws ApiException FORBIDDEN     si intenta modificar otro usuario
     * @throws ApiException NOT_FOUND     si el usuario no existe
     * @throws ApiException UNAUTHORIZED  si la contraseña actual no coincide
     * @throws ApiException UNPROCESSABLE_ENTITY si la nueva contraseña no cumple la política
     */
    @PatchMapping("/{id}/password")
    public void changePassword(@PathVariable String id,
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Cambio de contraseña para usuario id={}", id);
        ensureSameUser(id, authorization);
        userService.changePassword(id, request.currentPassword(), request.newPassword());
    }

    /**
     * Verifica que el usuario autenticado coincide con el id solicitado.
     * <p>
     * Método de validación interno que asegura que los usuarios solo puedan
     * modificar su propia información. Extrae el ID del token JWT y lo compara
     * con el ID solicitado en la URL.
     * </p>
     *
     * @param requestedUserId ID del usuario que se intenta modificar
     * @param authorization   header de autorización con token JWT
     * @throws ApiException con código FORBIDDEN si los IDs no coinciden
     * @throws ApiException si el token es inválido o ha expirado
     *
     * @see AuthService#requireUserId(String)
     */
    private void ensureSameUser(String requestedUserId, String authorization) {
        String authUserId = authService.requireUserId(authorization);
        if (!requestedUserId.equals(authUserId)) {
            throw new ApiException("FORBIDDEN", "No puedes modificar otro usuario", HttpStatus.FORBIDDEN);
        }
    }
}