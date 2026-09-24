package ge.andaneri.crm.auth;

import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.User;
import org.springframework.stereotype.Component;

/**
 * The two accounts that hold the keys: the first admin and root, both named in the environment.
 *
 * <p>They are kept out of every automatic lock. An office works from one address, so one person fumbling
 * their password used to block that address for everyone on it - the person who could undo it included.
 * These two get in from anywhere, always, and their passwords are set in the environment rather than on
 * the site, so nobody can lock the door and lose the key.
 */
@Component
public class ProtectedAccounts {

    private final CrmProperties properties;

    public ProtectedAccounts(CrmProperties properties) {
        this.properties = properties;
    }

    public boolean isProtected(String username) {
        return username != null && (matches(username, properties.adminUsername()) || matches(username, properties.rootUsername()));
    }

    public boolean isProtected(User user) {
        return user != null && (user.isRoot() || isProtected(user.getUsername()));
    }

    private static boolean matches(String username, String configured) {
        return configured != null && !configured.isBlank() && configured.trim().equalsIgnoreCase(username.trim());
    }
}
