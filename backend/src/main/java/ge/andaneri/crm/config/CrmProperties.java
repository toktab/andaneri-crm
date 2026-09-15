package ge.andaneri.crm.config;

import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under {@code crm.*} in application.properties. */
@ConfigurationProperties(prefix = "crm")
public record CrmProperties(
        String jwtSecret,
        int tokenHours,
        String zone,
        String adminUsername,
        String adminPassword,
        boolean demoData,
        /** Creates the throwaway login test / test (admin). For trying things out only: switch off for real use. */
        boolean testAccount,
        List<String> corsOrigins,
        /** The root account, kept in step with these two on every start. Empty username: no root account. */
        String rootUsername,
        String rootPassword,
        /**
         * Store the full text of failed passwords in the sign-in log. Off, only a masked hint is kept
         * ("t•••••2, 8 characters"): a failed attempt is very often someone's real password with a typo.
         */
        boolean logFullFailedPasswords,
        /** Root can always sign in, even from an address the whitelist does not allow, so it can never lock itself out. */
        boolean rootBypassWhitelist,
        /** When run from the IDE (not a packaged jar), build and watch the frontend next to the backend. */
        boolean frontendAutostart,
        /** The frontend project folder. Empty: look for ./frontend and ../frontend. */
        String frontendDir,
        /** A folder with a built frontend to serve (index.html and assets). Empty: the watched build, or the jar's own copy. */
        String frontendDist,
        /** Send push reminders from this instance. Off in tests, which trigger the scheduler by hand. */
        boolean remindersEnabled,
        /** VAPID key pair (base64url) that signs push messages. Empty: made once and kept in the database. */
        String vapidPublicKey,
        String vapidPrivateKey,
        /** Contact for the push services: a mailto: or https: address. */
        String vapidSubject) {

    public ZoneId zoneId() {
        return ZoneId.of(zone == null || zone.isBlank() ? "Asia/Tbilisi" : zone);
    }
}
