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

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendBaseUrl;

    public EmailNotificationSender(
            JavaMailSender mailSender,
            @Value("${mapit.mail.from}") String fromAddress,
            @Value("${mapit.mail.frontend-base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Envia el correo de verificacion con el enlace que apunta a la pagina
     * {@code /verify-email} del frontend con el token en claro como query param.
     */
    @Override
    public void sendVerificationEmail(String to, String userName, String nick, String rawToken) {
        String link = frontendBaseUrl + "/verify-email?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String html = loadTemplate(TEMPLATE_PATH)
                .replace("{{name}}", userName)
                .replace("{{nick}}", nick != null ? nick : "")
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

    private String loadTemplate(String templatePath) {
        try (InputStream is = new ClassPathResource(templatePath).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo cargar la plantilla de email: " + templatePath, ex);
        }
    }
}
