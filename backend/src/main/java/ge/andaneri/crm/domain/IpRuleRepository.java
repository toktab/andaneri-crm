package ge.andaneri.crm.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IpRuleRepository extends JpaRepository<IpRule, Long> {

    @Query("select r from IpRule r left join fetch r.createdBy order by r.kind, r.createdAt desc")
    List<IpRule> findAllForList();
}
