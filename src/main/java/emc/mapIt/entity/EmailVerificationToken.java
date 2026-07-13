package emc.mapIt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Token de un solo uso para verificar el email de un usuario tras el registro (Fase 1 auth).
 * <p>
 * Coleccion propia (no embebida en {@link User}) porque tiene ciclo de vida independiente:
 * se crea al registrar/reenviar, se consume al verificar, y expira solo via el indice TTL
 * de Mongo ({@code expireAfterSeconds = 0} sobre {@link #expiresAt}: el documento se
 * autoborra al llegar a ese instante).
 * </p>
 */
@Document(collection = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    private String id;

    private String userId;

    // sha256(token) via HashService.sha256 - el token en claro nunca se persiste
    private String tokenHash;

    private Instant createdAt;

    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    private Instant consumedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }
}
