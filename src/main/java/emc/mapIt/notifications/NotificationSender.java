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
     * @param nick     nick asignado (generado o elegido en el registro)
     * @param rawToken token de verificación en claro; el adaptador construye el enlace/acción
     */
    void sendVerificationEmail(String to, String userName, String nick, String rawToken);

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
     * Invita por email a alguien que todavía no tiene cuenta en MapIt a registrarse, mencionando
     * el grupo al que se le ha invitado. A diferencia de {@link #sendGroupInvitationEmail}, no
     * lleva un enlace de aceptar/rechazar (no hay cuenta con la que autenticarse todavía) — la
     * invitación se vincula sola a su cuenta en cuanto se registre y verifique el email (ver
     * {@code GroupService#claimEmailInvitations}), y a partir de ahí aparece como cualquier otra
     * invitación pendiente dentro de la aplicación.
     *
     * @param to            dirección de email invitada
     * @param groupName     nombre del grupo al que se le invita
     * @param invitedByName nombre de quien envía la invitación
     */
    void sendGroupSignupInvitationEmail(String to, String groupName, String invitedByName);

    /**
     * Notifica al organizador de un grupo de un aviso enviado por uno de sus miembros.
     *
     * @param to            destino del organizador (hoy: dirección de email)
     * @param organizerName nombre del organizador, para personalizar el mensaje
     * @param groupName     nombre del grupo
     * @param fromUserName  nombre del miembro que envía el aviso
     * @param subject       asunto elegido por el remitente; se muestra en el cuerpo del correo,
     *                      no reemplaza el asunto real del email (fijo, ver adaptador)
     * @param message       mensaje escrito por el miembro
     */
    void sendGroupOrganizerNoticeEmail(String to, String organizerName, String groupName, String fromUserName,
            String subject, String message);

    /**
     * Notifica a un miembro de un grupo de un mensaje difundido por el organizador a (una parte
     * o) todos los miembros.
     *
     * @param to             destino del miembro (hoy: dirección de email)
     * @param recipientName  nombre del miembro destinatario, para personalizar el mensaje
     * @param groupName      nombre del grupo
     * @param organizerName  nombre del organizador que envía el mensaje
     * @param subject        asunto elegido por el organizador; se muestra en el cuerpo del
     *                       correo, no reemplaza el asunto real del email (fijo, ver adaptador)
     * @param message        mensaje escrito por el organizador
     */
    void sendGroupBroadcastEmail(String to, String recipientName, String groupName, String organizerName,
            String subject, String message);

    /**
     * Notifica al organizador de un grupo de que alguien ha solicitado unirse (iniciado desde una
     * publicación privada del grupo a la que quien solicita no podía apuntarse).
     *
     * @param to             destino del organizador (hoy: dirección de email)
     * @param organizerName  nombre del organizador, para personalizar el mensaje
     * @param groupName      nombre del grupo
     * @param requesterName  nombre de quien solicita unirse
     */
    void sendGroupJoinRequestEmail(String to, String organizerName, String groupName, String requesterName);

    /**
     * Notifica a quien solicitó unirse a un grupo de que su solicitud ha sido resuelta.
     *
     * @param to            destino de quien solicitó (hoy: dirección de email)
     * @param requesterName nombre de quien solicitó, para personalizar el mensaje
     * @param groupName     nombre del grupo
     * @param accepted      {@code true} si se aceptó, {@code false} si se rechazó
     */
    void sendGroupJoinRequestResolvedEmail(String to, String requesterName, String groupName, boolean accepted);

    /**
     * Notifica a un usuario que ha sido invitado individualmente a una publicación (evento),
     * sea cual sea su visibilidad.
     *
     * @param to               destino del usuario invitado (hoy: dirección de email)
     * @param userName         nombre del usuario invitado, para personalizar el mensaje
     * @param publicationTitle título de la publicación a la que se le invita
     * @param invitedByName    nombre de quien envía la invitación
     */
    void sendPublicationInvitationEmail(String to, String userName, String publicationTitle, String invitedByName);

    /**
     * Notifica al autor de una publicación privada de que alguien ha solicitado apuntarse.
     *
     * @param to               destino del autor (hoy: dirección de email)
     * @param authorName       nombre del autor, para personalizar el mensaje
     * @param publicationTitle título de la publicación
     * @param requesterName    nombre de quien solicita acceso
     */
    void sendPublicationAccessRequestEmail(String to, String authorName, String publicationTitle,
            String requesterName);

    /**
     * Notifica a quien solicitó acceso a una publicación privada de que su solicitud ha sido
     * resuelta.
     *
     * @param to               destino de quien solicitó (hoy: dirección de email)
     * @param requesterName    nombre de quien solicitó, para personalizar el mensaje
     * @param publicationTitle título de la publicación
     * @param accepted         {@code true} si se aceptó, {@code false} si se rechazó
     */
    void sendPublicationAccessRequestResolvedEmail(String to, String requesterName, String publicationTitle,
            boolean accepted);

    /**
     * Notifica a una cuenta administradora de un feedback (bug/sugerencia/otro) enviado por un
     * usuario al equipo de desarrollo.
     *
     * @param to             destino de la cuenta administradora (hoy: dirección de email)
     * @param category       categoría elegida por el remitente ({@code BUG}, {@code SUGGESTION}
     *                       u {@code OTHER})
     * @param fromUserName   nombre del usuario que envía el feedback
     * @param fromUserEmail  email del usuario que envía el feedback, para poder responderle
     * @param subject        asunto elegido por el remitente
     * @param message        mensaje escrito por el remitente
     */
    void sendDeveloperFeedbackEmail(String to, String category, String fromUserName, String fromUserEmail,
            String subject, String message);
}
