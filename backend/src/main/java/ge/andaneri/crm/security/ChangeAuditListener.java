package ge.andaneri.crm.security;

import ge.andaneri.crm.domain.AuditEntry;
import ge.andaneri.crm.domain.Business;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * One change-log row for every row the application inserts, updates or deletes, with the values before
 * and after, who did it and from where. Hooked into Hibernate after commit, so a change that rolled back
 * leaves no trace, and every entity added later is covered without touching this class.
 */
@Component
public class ChangeAuditListener implements PostCommitInsertEventListener, PostCommitUpdateEventListener, PostCommitDeleteEventListener {

    /** Bookkeeping that changes on its own and says nothing about who did what. */
    private static final Set<String> IGNORED = Set.of("version", "updatedAt", "lastLoginAt", "lastLoginIp", "remindedAt");
    private static final Set<String> SECRET = Set.of("passwordHash", "calendarToken", "p256dh", "authSecret", "endpoint", "endpointHash");
    /** Plumbing rather than work: a device allowing notifications is not something anyone "changed". */
    private static final Set<String> SKIPPED_ENTITIES = Set.of(AuditEntry.class.getName(), "ge.andaneri.crm.domain.PushSubscription");

    private final EntityManagerFactory entityManagerFactory;
    private final SecurityLog securityLog;
    private final JsonMapper json;

    public ChangeAuditListener(EntityManagerFactory entityManagerFactory, SecurityLog securityLog, JsonMapper json) {
        this.entityManagerFactory = entityManagerFactory;
        this.securityLog = securityLog;
        this.json = json;
    }

    @PostConstruct
    void register() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_COMMIT_INSERT, this);
        registry.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        registry.appendListeners(EventType.POST_COMMIT_DELETE, this);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return !SKIPPED_ENTITIES.contains(persister.getEntityName());
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        Map<String, Object[]> changes = new LinkedHashMap<>();
        String[] names = event.getPersister().getPropertyNames();
        for (int i = 0; i < names.length; i++) {
            String value = render(names[i], event.getState()[i]);
            if (value != null && !IGNORED.contains(names[i])) {
                changes.put(names[i], new Object[] {null, value});
            }
        }
        write("INSERT", event.getPersister(), event.getId(), event.getState(), changes);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (event.getOldState() == null || event.getDirtyProperties() == null) {
            return;
        }
        Map<String, Object[]> changes = new LinkedHashMap<>();
        String[] names = event.getPersister().getPropertyNames();
        for (int index : event.getDirtyProperties()) {
            if (IGNORED.contains(names[index])) {
                continue;
            }
            changes.put(names[index], new Object[] {render(names[index], event.getOldState()[index]), render(names[index], event.getState()[index])});
        }
        if (!changes.isEmpty()) {
            write("UPDATE", event.getPersister(), event.getId(), event.getState(), changes);
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        Map<String, Object[]> changes = new LinkedHashMap<>();
        String[] names = event.getPersister().getPropertyNames();
        Object[] state = event.getDeletedState();
        for (int i = 0; state != null && i < names.length; i++) {
            String value = render(names[i], state[i]);
            if (value != null && !IGNORED.contains(names[i])) {
                changes.put(names[i], new Object[] {value, null});
            }
        }
        write("DELETE", event.getPersister(), event.getId(), state, changes);
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent event) {
        // Rolled back: nothing happened, nothing to log.
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent event) {
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent event) {
    }

    private void write(String action, EntityPersister persister, Object id, Object[] state, Map<String, Object[]> changes) {
        String entity = persister.getEntityName().substring(persister.getEntityName().lastIndexOf('.') + 1);
        Long entityId = id instanceof Number number ? number.longValue() : null;
        Long businessId = Business.class.getName().equals(persister.getEntityName()) ? entityId : businessIdOf(persister, state);
        String text;
        try {
            text = json.writeValueAsString(changes);
        } catch (RuntimeException ex) {
            text = null;
        }
        securityLog.change(businessId, entity, entityId, action, text, currentUserId(), ClientIp.current());
    }

    private Long businessIdOf(EntityPersister persister, Object[] state) {
        if (state == null) {
            return null;
        }
        List<String> names = List.of(persister.getPropertyNames());
        int index = names.indexOf("business");
        if (index < 0 || state[index] == null) {
            return null;
        }
        Object id = identifier(state[index]);
        return id instanceof Number number ? number.longValue() : null;
    }

    private String render(String property, Object value) {
        if (value == null) {
            return null;
        }
        if (SECRET.contains(property)) {
            return "********";
        }
        if (value instanceof PersistentCollection<?> collection) {
            return collection.wasInitialized() && value instanceof Collection<?> items ? "[" + items.size() + "]" : null;
        }
        if (value instanceof Collection<?>) {
            return null;
        }
        if (value instanceof HibernateProxy || value.getClass().isAnnotationPresent(Entity.class)) {
            Object id = identifier(value);
            return id == null ? null : "#" + id;
        }
        String text = value instanceof Enum<?> e ? e.name() : value.toString();
        return text.length() > 300 ? text.substring(0, 297) + "..." : text;
    }

    private Object identifier(Object entity) {
        if (entity instanceof HibernateProxy proxy) {
            return proxy.getHibernateLazyInitializer().getIdentifier();
        }
        try {
            return entityManagerFactory.getPersistenceUnitUtil().getIdentifier(entity);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
