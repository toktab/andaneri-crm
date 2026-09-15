package ge.andaneri.crm.domain;

/**
 * SALES works their own accounts; SUPERVISOR sees and edits everyone's; ADMIN also runs users and the
 * catalog; ROOT is the one account set from environment variables, with everything plus the security trail.
 */
public enum Role {
    SALES, SUPERVISOR, ADMIN, ROOT
}
