package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.domain.ProductCategory;
import ge.andaneri.crm.domain.ProductCategoryRepository;
import ge.andaneri.crm.domain.ProductUsage;
import ge.andaneri.crm.domain.ProductUsageRepository;
import ge.andaneri.crm.domain.UsageSource;
import ge.andaneri.crm.web.UserDtos.UserRef;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the market pours, from what is written down: which brands are on the bars, and which flavors -
 * counted separately for each category, because vanilla syrup and vanilla puree are two different
 * questions. Every number can be opened to see exactly which places are behind it.
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    /** One brand and how many places use it. */
    public record BrandCount(Long brandId, String name, boolean own, long businesses) {
    }

    /** One flavor within a category and how many places use it. */
    public record FlavorCount(Long flavorId, String nameKa, String nameEn, long businesses) {
    }

    /** A category with its flavors, so syrups and purees are read apart. */
    public record CategoryFlavors(Long categoryId, String nameKa, String nameEn, List<FlavorCount> flavors) {
    }

    public record Market(List<BrandCount> brands, List<CategoryFlavors> categories) {
    }

    /** One place behind a number, and how it uses the thing. */
    public record Who(Long businessId, String name, String status, UserRef assignedTo, String brand, String flavor,
            String source, String quantity, String frequency) {
    }

    private final ProductUsageRepository usages;
    private final ProductCategoryRepository categories;
    private final CurrentUser currentUser;

    public MarketController(ProductUsageRepository usages, ProductCategoryRepository categories, CurrentUser currentUser) {
        this.usages = usages;
        this.categories = categories;
        this.currentUser = currentUser;
    }

    /**
     * The round summary: brands across everything, then the flavors of each category.
     *
     * @param ownToo count our own bottles as well; off by default, so the lists read as "what the
     *               competition is on" rather than "what everyone pours".
     */
    @GetMapping
    public Market market(@RequestParam(defaultValue = "false") boolean ownToo) {
        currentUser.require();
        List<BrandCount> brands = usages.countBusinessesByBrandInCategory(null).stream()
                .map(row -> new BrandCount((Long) row[0], (String) row[1], (Boolean) row[2], (Long) row[3]))
                .toList();

        List<CategoryFlavors> out = new ArrayList<>();
        for (ProductCategory category : categories.findAllByOrderBySortOrderAscIdAsc()) {
            List<FlavorCount> flavors = usages.countBusinessesByFlavorInCategory(category.getId(), ownToo).stream()
                    .map(row -> new FlavorCount((Long) row[0], (String) row[1], (String) row[2], (Long) row[3]))
                    .toList();
            if (!flavors.isEmpty()) {
                out.add(new CategoryFlavors(category.getId(), category.getNameKa(), category.getNameEn(), flavors));
            }
        }
        return new Market(brands, out);
    }

    /** Who is behind one of those numbers: the places, with who looks after each. */
    @GetMapping("/who")
    public List<Who> who(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long flavorId,
            @RequestParam(required = false) Long brandId, @RequestParam(required = false) UsageSource source) {
        currentUser.require();
        List<Who> out = new ArrayList<>();
        for (ProductUsage usage : usages.findMatching(categoryId, flavorId, brandId, source)) {
            out.add(new Who(usage.getBusiness().getId(), usage.getBusiness().getName(), usage.getBusiness().getStatus().name(),
                    UserRef.of(usage.getBusiness().getAssignedTo()),
                    usage.getBrand() == null ? null : usage.getBrand().getName(),
                    usage.getFlavor() == null ? null : usage.getFlavor().getNameKa(),
                    usage.getSource().name(), usage.getQuantity(), usage.getFrequency()));
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }
}
