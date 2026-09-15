package ge.andaneri.crm.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByBusinessIdOrderByDecisionMakerDescNameAsc(Long businessId);

    List<Contact> findByBusinessIdIn(Collection<Long> businessIds);
}
