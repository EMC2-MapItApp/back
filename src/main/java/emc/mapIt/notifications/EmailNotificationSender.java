package emc.mapIt.notifications;

import emc.mapIt.exception.ApiException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Adaptador de salida (arquitectura hexagonal) del puerto {@link NotificationSender} vía email
 * (Fase 1 auth: verificación de cuenta y restablecimiento de contraseña).
 * <p>
 * Responsabilidad única: construir el enlace, cargar la plantilla y enviar el correo. No conoce
 * nada del ciclo de vida del token (eso vive en los servicios de auth que consumen el puerto).
 * Envuelve {@link JavaMailSender}, configurado vía SMTP genérico para poder cambiar de
 * proveedor solo con variables de entorno.
 * </p>
 */
@Component
public class EmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);
    private static final String TEMPLATE_PATH = "templates/email/verification-email.html";
    private static final String RESET_TEMPLATE_PATH = "templates/email/password-reset-email.html";
    private static final String GROUP_INVITATION_TEMPLATE_PATH = "templates/email/group-invitation-email.html";
    private static final String GROUP_ORGANIZER_NOTICE_TEMPLATE_PATH = "templates/email/group-organizer-notice-email.html";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendBaseUrl;

    public EmailNotificationSender(
            JavaMailSender mailSender,
            @Value("${mapit.mail.from}") String fromAddress,
            @Value("${mapit.mail.frontend-base-url}") String frontendBaseUrl,
            // Solo para el log de diagnóstico de abajo — nunca se envía ni se loguea la password.
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${spring.mail.port:}") String smtpPort,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendBaseUrl = frontendBaseUrl;

        // Causa más común de "no llega el correo": SMTP_USERNAME/PASSWORD vacíos (valor por
        // defecto en application-dev.yaml si la variable de entorno no está exportada en el
        // proceso que arrancó el backend) — Gmail/Resend rechazan la autenticación y el envío
        // falla, pero solo se ve si se mira este log al arrancar.
        log.info("EmailNotificationSender configurado: host={} port={} from={} username={}",
                smtpHost, smtpPort, fromAddress, smtpUsername.isBlank() ? "(VACÍO)" : "(configurado)");
        if (smtpUsername.isBlank()) {
            log.warn("spring.mail.username está vacío — el envío de email fallará en la autenticación SMTP. "
                    + "Comprueba que MAPIT_SMTP_USERNAME (y MAPIT_SMTP_PASSWORD) están exportadas "
                    + "en el proceso que arranca el backend.");
        }
    }

    /**
     * Envia el correo de verificacion con el enlace que apunta a la pagina
     * {@code /verify-email} del frontend con el token en claro como query param.
     */
    @Override
    public void sendVerificationEmail(String to, String userName, String rawToken) {
        String link = frontendBaseUrl + "/verify-email?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String html = loadTemplate(TEMPLATE_PATH)
                .replace("{{name}}", userName)
                .replace("{{link}}", link);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(fromAddress);
            helper.setSubject("Verifica tu correo en MapIt");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email de verificacion enviado email={}", to);
        } catch (MessagingException | MailException ex) {
            // MimeMessageHelper lanza MessagingException (checked); JavaMailSender.send(...)
            // lanza MailException (unchecked, org.springframework.mail) si falla el transporte SMTP.
            log.error("Fallo enviando email de verificacion email={}", to, ex);
            throw new ApiException(
                    "MAIL_SEND_FAILED",
                    "No se pudo enviar el correo de verificacion.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Envia el correo de restablecimiento de contraseña con el enlace que apunta a la pagina
     * {@code /reset-password} del frontend con el token en claro como query param.
     */
    @Override
    public void sendPasswordResetEmail(String to, String userName, String rawToken) {
        String link = frontendBaseUrl + "/reset-password?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String html = loadTemplate(RESET_TEMPLATE_PATH)
                .replace("{{name}}", userName)
                .replace("{{link}}", link);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(fromAddress);
            helper.setSubject("Restablece tu contraseña en MapIt");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email de restablecimiento de contraseña enviado email={}", to);
        } catch (MessagingException | MailException ex) {
            log.error("Fallo enviando email de restablecimiento de contraseña email={}", to, ex);
            throw new ApiException(
                    "MAIL_SEND_FAILED",
                    "No se pudo enviar el correo de restablecimiento de contraseña.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Envia el correo de invitación a un grupo, con el enlace que apunta a la pagina
     * {@code /group-invitation} del frontend con el id de la invitación como query param
     * {@code token} (ver comentario de {@code GroupInvitation.id} sobre por qué no es un
     * secreto hasheado como los tokens de verificación/reset).
     */
    @Override
    public void sendGroupInvitationEmail(String to, String userName, String groupName, String invitedByName,
            String invitationId) {
        log.info("Preparando email de invitación a grupo: to={} groupName={} invitationId={}",
                to, groupName, invitationId);

        String link = frontendBaseUrl + "/group-invitation?token=" + URLEncoder.encode(invitationId, StandardCharsets.UTF_8);
        log.debug("Enlace de invitación generado: {}", link);

        String html = loadTemplate(GROUP_INVITATION_TEMPLATE_PATH)
                .replace("{{name}}", escapeHtml(userName))
                .replace("{{groupName}}", escapeHtml(groupName))
                .replace("{{invitedByName}}", escapeHtml(invitedByName))
                .replace("{{link}}", link);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(fromAddress);
            helper.setSubject("Te han invitado a un grupo en MapIt");
            helper.setText(html, true);

            log.debug("Enviando email de invitación a grupo vía SMTP: to={} from={}", to, fromAddress);
            mailSender.send(message);
            log.info("Email de invitación a grupo enviado correctamente: to={}", to);
        } catch (MessagingException | MailException ex) {
            log.error("Fallo enviando email de invitación a grupo: to={} groupName={} causa={}: {}",
                    to, groupName, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw new ApiException(
                    "MAIL_SEND_FAILED",
                    "No se pudo enviar el correo de invitación al grupo.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Envia al organizador de un grupo el aviso escrito por uno de sus miembros. A diferencia
     * del resto de correos de esta clase, incrusta texto libre de un usuario ({@code message}):
     * se escapa a HTML para no introducir un vector de inyección en el cliente de correo.
     */
    @Override
    public void sendGroupOrganizerNoticeEmail(String to, String organizerName, String groupName, String fromUserName,
            String message) {
        log.info("Preparando email de aviso al organizador: to={} groupName={} fromUserName={}",
                to, groupName, fromUserName);

        String html = loadTemplate(GROUP_ORGANIZER_NOTICE_TEMPLATE_PATH)
                .replace("{{organizerName}}", escapeHtml(organizerName))
                .replace("{{groupName}}", escapeHtml(groupName))
                .replace("{{fromUserName}}", escapeHtml(fromUserName))
                .replace("{{message}}", escapeHtml(message));

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setTo(to);
            helper.setFrom(fromAddress);
            helper.setSubject("Nuevo aviso en tu grupo \"" + groupName + "\" de MapIt");
            helper.setText(html, true);

            log.debug("Enviando email de aviso al organizador vía SMTP: to={} from={}", to, fromAddress);
            mailSender.send(mimeMessage);
            log.info("Email de aviso al organizador enviado correctamente: to={}", to);
        } catch (MessagingException | MailException ex) {
            log.error("Fallo enviando email de aviso al organizador: to={} groupName={} causa={}: {}",
                    to, groupName, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw new ApiException(
                    "MAIL_SEND_FAILED",
                    "No se pudo enviar el correo de aviso al organizador.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String loadTemplate(String templatePath) {
        try (InputStream is = new ClassPathResource(templatePath).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo cargar la plantilla de email: " + templatePath, ex);
        }
    }

    /** Escapa caracteres HTML especiales para incrustar texto de usuario de forma segura en la plantilla. */
    private String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
