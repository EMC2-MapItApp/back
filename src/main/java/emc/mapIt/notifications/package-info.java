/**
 * Módulo de notificaciones al usuario, organizado como arquitectura hexagonal (puertos y
 * adaptadores): los casos de uso de auth
 * ({@link emc.mapIt.service.EmailVerificationService},
 * {@link emc.mapIt.service.PasswordResetService}) dependen únicamente del puerto
 * {@link emc.mapIt.notifications.NotificationSender}, no del canal de entrega concreto. Hoy
 * ese puerto lo implementa {@link emc.mapIt.notifications.EmailNotificationSender} (email vía
 * SMTP); un canal futuro (push, in-app — ver {@code docs/IDEAS.md}) se añadiría como un nuevo
 * adaptador sin modificar los llamadores. Ver {@code docs/ARQUITECTURA.md} para el porqué de
 * este límite.
 */
package emc.mapIt.notifications;
