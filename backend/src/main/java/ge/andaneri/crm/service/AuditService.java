package ge.andaneri.crm.service;

import ge.andaneri.crm.domain.AuditEntry;
import ge.andaneri.crm.domain.AuditEntryRepository;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.security.ClientIp;
import org.springframework.stereotype.Service;

/**
 * Writes the readable "who did what" lines ("STATUS: NEW -> CONTACTED"). Called inside the transaction of
 * the change it describes. The raw before / after rows are written separately by ChangeAuditListener.
 */
@Service
public class AuditService {

    private final AuditEntryRepository entries;

    public AuditService(AuditEntryRepository entries) {
        this.entries = entries;
    }

    public void record(Long businessId, String entity, Long entityId, String action, String summary, User user) {
        AuditEntry entry = new AuditEntry(businessId, entity, entityId, action, summary, user);
        entry.setIp(ClientIp.current());
        entries.save(entry);
    }
}
