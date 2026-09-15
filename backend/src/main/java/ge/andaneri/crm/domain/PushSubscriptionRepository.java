package ge.andaneri.crm.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpointHash(String endpointHash);

    List<PushSubscription> findByUserId(Long userId);

    long countByUserId(Long userId);

    @Transactional
    @Modifying
    @Query("update PushSubscription s set s.lastSuccessAt = :at where s.id = :id")
    int markDelivered(Long id, Instant at);
}
