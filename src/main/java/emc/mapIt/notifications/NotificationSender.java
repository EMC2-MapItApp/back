package emc.mapIt.notifications;

/**
 * Puerto (arquitectura hexagonal) para notificar al usuario en el flujo de autenticación
 * (Fase 1: verificación de cuenta y restablecimiento de contraseña) y en el dominio Grupos
 * (invitaciones y aviso al organizador).
 * <p>
 * Los casos de uso de auth y de grupos dependen solo de esta interfaz, no del canal de entrega
 * concreto — hoy implementado por email ({@link EmailNotificationSender}). Un canal futuro
 * (push, in-app) se añadiría como un nuevo adaptador que implemente esta misma interfaz, sin
 * modificar los llamadores.
 * </p>
 */
public interface NotificationSender {

    /**
     * Notifica al usuario para que verifique su correo tras el registro (o un reenvío).
     *
     * @param to       destino del usuario en el canal actual (hoy: dirección de email)
     * @param userName nombre del usuario, para personalizar el mensaje
     * @param rawToken token de verificación en claro; el adaptador construye el enlace/acción
     */
    void sendVerificationEmail(String to, String userName, String rawToken);

    /**
     * Notifica al usuario con el enlace/acción para restablecer su contraseña.
     *
     * @param to       destino del usuario en el canal actual (hoy: dirección de email)
     * @param userName nombre del usuario, para personalizar el mensaje
     * @param rawToken token de restablecimiento en claro; el adaptador construye el enlace/acción
     */
    void sendPasswordResetEmail(String to, String userName, String rawToken);

    /**
     * Notifica a un usuario que ha sido invitado a un grupo.
     *
     * @param to             destino del usuario invitado (hoy: dirección de email)
     * @param userName       nombre del usuario invitado, para personalizar el mensaje
     * @param groupName      nombre del grupo al que se le invita
     * @param invitedByName  nombre de quien envía la invitación
     * @param invitationId   id de la invitación; el adaptador construye el enlace de destino
     */
    void sendGroupInvitationEmail(String to, String userName, String groupName, String invitedByName,
            String invitationId);

    /**
     * Notifica al organizador de un grupo de un aviso enviado por uno de sus miembros.
     *
     * @param to            destino del organizador (hoy: dirección de email)
     * @param organizerName nombre del organizador, para personalizar el mensaje
     * @param groupName     nombre del grupo
     * @param fromUserName  nombre del miembro que envía el aviso
     * @param message       mensaje escrito por el miembro
     */
    void sendGroupOrganizerNoticeEmail(String to, String organizerName, String groupName, String fromUserName,
            String message);
}
