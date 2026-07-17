package emc.mapIt.notifications;

/**
 * Puerto (arquitectura hexagonal) para notificar al usuario en el flujo de autenticación
 * (Fase 1: verificación de cuenta y restablecimiento de contraseña).
 * <p>
 * Los casos de uso de auth dependen solo de esta interfaz, no del canal de entrega concreto —
 * hoy implementado por email ({@link EmailNotificationSender}). Un canal futuro (push, in-app)
 * se añadiría como un nuevo adaptador que implemente esta misma interfaz, sin modificar los
 * llamadores.
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
}
