package ge.andaneri.crm.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("select c from Comment c join fetch c.author where c.business.id = :businessId order by c.createdAt desc")
    List<Comment> findForBusiness(Long businessId);
}
