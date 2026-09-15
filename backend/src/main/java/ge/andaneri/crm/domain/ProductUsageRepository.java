package ge.andaneri.crm.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductUsageRepository extends JpaRepository<ProductUsage, Long> {

    @Query("select u from ProductUsage u join fetch u.category left join fetch u.brand left join fetch u.flavor"
            + " where u.business.id = :businessId order by u.id")
    List<ProductUsage> findForBusiness(Long businessId);

    @Query("select u from ProductUsage u join fetch u.category left join fetch u.brand left join fetch u.flavor"
            + " where u.business.id in :ids order by u.id")
    List<ProductUsage> findForBusinesses(Collection<Long> ids);

    /** (brand name, own, number of businesses using it): who the market buys from. */
    @Query("select br.name, br.own, count(distinct b.id) from ProductUsage u join u.brand br join u.business b"
            + " where b.archived = false group by br.name, br.own order by count(distinct b.id) desc")
    List<Object[]> countBusinessesByBrand();

    /** (flavor id, Georgian, English, number of businesses) for flavors bought from anyone but us. */
    @Query("select f.id, f.nameKa, f.nameEn, count(distinct b.id) from ProductUsage u join u.flavor f join u.business b"
            + " left join u.brand br where b.archived = false and (br is null or br.own = false)"
            + " group by f.id, f.nameKa, f.nameEn order by count(distinct b.id) desc")
    List<Object[]> countBusinessesByCompetitorFlavor();

    /** Pairs of (business id, flavor id) for the spreadsheet view's flavor column. */
    @Query("select distinct u.business.id, u.flavor.id from ProductUsage u where u.business.id in :ids and u.flavor is not null")
    List<Object[]> findFlavorIds(Collection<Long> ids);

    /** Pairs of (business id, brand name) for the list page's "uses" column. */
    @Query("select distinct u.business.id, br.name from ProductUsage u join u.brand br where u.business.id in :ids")
    List<Object[]> findBrandNames(Collection<Long> ids);
}
