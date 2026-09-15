package ge.andaneri.crm.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("select distinct p from Product p join fetch p.brand join fetch p.category left join fetch p.flavors order by p.sortOrder, p.id")
    List<Product> findAllForCatalog();
}
