package ge.andaneri.crm.notify;

import ge.andaneri.crm.config.CrmProperties;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The server's push signing keys. Browsers bind each subscription to the public key it was made with, so
 * the pair must never change once devices have subscribed: it is created on first use and kept in the
 * database (or given as VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY).
 */
@Component
public class VapidKeys {

    private static final String PUBLIC = "vapid_public_key";
    private static final String PRIVATE = "vapid_private_key";

    private final CrmProperties properties;
    private final JdbcTemplate jdbc;
    private volatile KeyPair pair;

    public VapidKeys(CrmProperties properties, JdbcTemplate jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    public KeyPair pair() {
        KeyPair current = pair;
        if (current == null) {
            synchronized (this) {
                if (pair == null) {
                    pair = load();
                }
                current = pair;
            }
        }
        return current;
    }

    public String publicKey() {
        return WebPush.publicKeyText(pair());
    }

    public String subject() {
        String subject = properties.vapidSubject();
        return subject == null || subject.isBlank() ? "mailto:crm@andaneri.ge" : subject.trim();
    }

    private KeyPair load() {
        try {
            if (notBlank(properties.vapidPublicKey()) && notBlank(properties.vapidPrivateKey())) {
                return WebPush.keyPair(properties.vapidPublicKey(), properties.vapidPrivateKey());
            }
            String pub = secret(PUBLIC);
            String priv = secret(PRIVATE);
            if (pub != null && priv != null) {
                return WebPush.keyPair(pub, priv);
            }
            KeyPair created = WebPush.newKeyPair();
            Timestamp now = Timestamp.valueOf(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
            jdbc.update("insert into app_secrets (name, secret_value, created_at) values (?, ?, ?)", PUBLIC, WebPush.publicKeyText(created), now);
            jdbc.update("insert into app_secrets (name, secret_value, created_at) values (?, ?, ?)", PRIVATE, WebPush.privateKeyText(created), now);
            return created;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Push signing keys are not valid", ex);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // Another instance created them at the same moment: use theirs.
            return loadStored();
        }
    }

    private KeyPair loadStored() {
        try {
            return WebPush.keyPair(secret(PUBLIC), secret(PRIVATE));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Push signing keys are not valid", ex);
        }
    }

    private String secret(String name) {
        List<String> values = jdbc.queryForList("select secret_value from app_secrets where name = ?", String.class, name);
        return values.isEmpty() ? null : values.get(0);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
