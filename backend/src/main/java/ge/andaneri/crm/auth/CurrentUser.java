package ge.andaneri.crm.auth;

import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The signed-in user, read fresh from the database on every call. The token only proves who is
 * asking: a deactivated account, a changed role or a "sign out everywhere" takes effect immediately.
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED");
        }
        long id;
        try {
            id = Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw ApiException.unauthorized("UNAUTHENTICATED");
        }
        User user = users.findById(id).orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED"));
        if (!user.isActive()) {
            throw ApiException.unauthorized("ACCOUNT_DISABLED");
        }
        // Tokens from before sessions could be revoked carry no version and count as version 0.
        if (authentication instanceof JwtAuthenticationToken jwt) {
            Object claim = jwt.getToken().getClaims().get("tv");
            long version = claim instanceof Number number ? number.longValue() : 0;
            if (version != user.getTokenVersion()) {
                throw ApiException.unauthorized("SESSION_REVOKED");
            }
        }
        return user;
    }

    public User requireSupervisor() {
        User user = require();
        if (!user.isSupervisor()) {
            throw ApiException.forbidden();
        }
        return user;
    }

    public User requireAdmin() {
        User user = require();
        if (!user.isAdmin()) {
            throw ApiException.forbidden();
        }
        return user;
    }

    public User requireRoot() {
        User user = require();
        if (!user.isRoot()) {
            throw ApiException.forbidden();
        }
        return user;
    }

    /**
     * Everyone can read every business (so two people never cold-call the same bar), but a
     * salesperson changes only the ones assigned to them, the ones they added, or unassigned ones.
     */
    public static boolean canEdit(User user, Business business) {
        if (user.isSupervisor()) {
            return true;
        }
        User assigned = business.getAssignedTo();
        if (assigned == null) {
            return true;
        }
        return assigned.getId().equals(user.getId())
                || (business.getCreatedBy() != null && business.getCreatedBy().getId().equals(user.getId()));
    }

    public static void requireEdit(User user, Business business) {
        if (!canEdit(user, business)) {
            throw ApiException.forbidden();
        }
    }
}
