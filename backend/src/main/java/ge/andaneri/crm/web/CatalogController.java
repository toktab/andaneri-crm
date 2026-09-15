package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.Brand;
import ge.andaneri.crm.domain.BrandRepository;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessType;
import ge.andaneri.crm.domain.BusinessTypeRepository;
import ge.andaneri.crm.domain.Flavor;
import ge.andaneri.crm.domain.FlavorRepository;
import ge.andaneri.crm.domain.Product;
import ge.andaneri.crm.domain.ProductCategory;
import ge.andaneri.crm.domain.ProductCategoryRepository;
import ge.andaneri.crm.domain.ProductRepository;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.service.AuditService;
import ge.andaneri.crm.service.SettingsService;
import ge.andaneri.crm.web.CatalogDtos.AssignableUser;
import ge.andaneri.crm.web.CatalogDtos.BrandDto;
import ge.andaneri.crm.web.CatalogDtos.BrandRequest;
import ge.andaneri.crm.web.CatalogDtos.FlavorDto;
import ge.andaneri.crm.web.CatalogDtos.FlavorRequest;
import ge.andaneri.crm.web.CatalogDtos.Lookups;
import ge.andaneri.crm.web.CatalogDtos.NamedRequest;
import ge.andaneri.crm.web.CatalogDtos.ProductDto;
import ge.andaneri.crm.web.CatalogDtos.ProductRequest;
import ge.andaneri.crm.web.CatalogDtos.TypeDto;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Brands, flavors, business types, product categories and the price list. Anyone can add a brand
 * or a flavor on the spot (a salesperson standing at a bar meets brands we have never heard of);
 * changing or retiring them, and everything about our own products, is for admins.
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final BusinessTypeRepository types;
    private final ProductCategoryRepository categories;
    private final BrandRepository brands;
    private final FlavorRepository flavors;
    private final ProductRepository products;
    private final UserRepository users;
    private final BusinessRepository businesses;
    private final SettingsService settings;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final SheetController sheets;
    private final ge.andaneri.crm.domain.CustomFieldRepository customFields;
    private final ge.andaneri.crm.domain.WorkbookRepository workbooks;

    public CatalogController(BusinessTypeRepository types, ProductCategoryRepository categories, BrandRepository brands,
            FlavorRepository flavors, ProductRepository products, UserRepository users, BusinessRepository businesses,
            SettingsService settings, CurrentUser currentUser, AuditService audit, SheetController sheets,
            ge.andaneri.crm.domain.CustomFieldRepository customFields, ge.andaneri.crm.domain.WorkbookRepository workbooks) {
        this.sheets = sheets;
        this.customFields = customFields;
        this.workbooks = workbooks;
        this.types = types;
        this.categories = categories;
        this.brands = brands;
        this.flavors = flavors;
        this.products = products;
        this.users = users;
        this.businesses = businesses;
        this.settings = settings;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping("/lookups")
    @Transactional(readOnly = true)
    public Lookups lookups() {
        currentUser.require();
        return new Lookups(
                types.findAllByOrderBySortOrderAscIdAsc().stream().map(TypeDto::of).toList(),
                categories.findAllByOrderBySortOrderAscIdAsc().stream().map(TypeDto::of).toList(),
                brands.findAllByOrderByOwnDescNameAsc().stream().map(BrandDto::of).toList(),
                flavors.findAll().stream()
                        .sorted(Comparator.comparing(Flavor::getNameKa))
                        .map(FlavorDto::of).toList(),
                users.findAllByOrderByFullNameAsc().stream()
                        .map(user -> new AssignableUser(user.getId(), user.getFullName(), user.getRole(), user.isActive()))
                        .toList(),
                businesses.findDistricts(),
                businesses.findCities(),
                settings.all(),
                sheets.list(),
                customFields.findAllByOrderBySortOrderAscIdAsc().stream().map(CatalogDtos.FieldDto::of).toList(),
                workbooks.findAllByOrderBySortOrderAscIdAsc().stream().map(w -> new CatalogDtos.WorkbookRef(w.getId(), w.getName())).toList());
    }

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public List<ProductDto> products() {
        currentUser.require();
        return products.findAllForCatalog().stream().map(ProductDto::of).toList();
    }

    // ------------------------------------------------------------ quick-add, for everyone

    @PostMapping("/brands")
    @Transactional
    public BrandDto addBrand(@Valid @RequestBody BrandRequest request) {
        User user = currentUser.require();
        String name = request.name().trim();
        return brands.findByNameIgnoreCase(name).map(BrandDto::of).orElseGet(() -> {
            Brand brand = brands.save(new Brand(name, false));
            audit.record(null, "Brand", brand.getId(), "CREATED", name, user);
            return BrandDto.of(brand);
        });
    }

    @PostMapping("/flavors")
    @Transactional
    public FlavorDto addFlavor(@Valid @RequestBody FlavorRequest request) {
        User user = currentUser.require();
        String ka = blankToNull(request.nameKa());
        String en = blankToNull(request.nameEn());
        if (ka == null && en == null) {
            throw ApiException.field("nameKa", "required");
        }
        String nameKa = ka != null ? ka : en;
        String nameEn = en != null ? en : ka;
        return flavors.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(nameKa, nameEn).map(FlavorDto::of).orElseGet(() -> {
            Flavor flavor = flavors.save(new Flavor(nameKa, nameEn));
            audit.record(null, "Flavor", flavor.getId(), "CREATED", nameKa + " / " + nameEn, user);
            return FlavorDto.of(flavor);
        });
    }

    // ------------------------------------------------------------ admin

    @PutMapping("/admin/brands/{id}")
    @Transactional
    public BrandDto updateBrand(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        User admin = currentUser.requireAdmin();
        Brand brand = brands.findById(id).orElseThrow(ApiException::notFound);
        brand.setName(request.name().trim());
        if (request.own() != null) {
            brand.setOwn(request.own());
        }
        if (request.active() != null) {
            brand.setActive(request.active());
        }
        audit.record(null, "Brand", id, "UPDATED", brand.getName(), admin);
        return BrandDto.of(brand);
    }

    @PutMapping("/admin/flavors/{id}")
    @Transactional
    public FlavorDto updateFlavor(@PathVariable Long id, @Valid @RequestBody FlavorRequest request) {
        User admin = currentUser.requireAdmin();
        Flavor flavor = flavors.findById(id).orElseThrow(ApiException::notFound);
        if (blankToNull(request.nameKa()) != null) {
            flavor.setNameKa(request.nameKa().trim());
        }
        if (blankToNull(request.nameEn()) != null) {
            flavor.setNameEn(request.nameEn().trim());
        }
        if (request.active() != null) {
            flavor.setActive(request.active());
        }
        audit.record(null, "Flavor", id, "UPDATED", flavor.getNameKa(), admin);
        return FlavorDto.of(flavor);
    }

    @PostMapping("/admin/business-types")
    @Transactional
    public TypeDto addType(@Valid @RequestBody NamedRequest request) {
        currentUser.requireAdmin();
        BusinessType type = new BusinessType(request.nameKa().trim(), request.nameEn().trim(),
                request.sortOrder() == null ? (int) types.count() : request.sortOrder());
        return TypeDto.of(types.save(type));
    }

    @PutMapping("/admin/business-types/{id}")
    @Transactional
    public TypeDto updateType(@PathVariable Long id, @Valid @RequestBody NamedRequest request) {
        currentUser.requireAdmin();
        BusinessType type = types.findById(id).orElseThrow(ApiException::notFound);
        type.setNameKa(request.nameKa().trim());
        type.setNameEn(request.nameEn().trim());
        if (request.sortOrder() != null) {
            type.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            type.setActive(request.active());
        }
        return TypeDto.of(type);
    }

    @PostMapping("/admin/categories")
    @Transactional
    public TypeDto addCategory(@Valid @RequestBody NamedRequest request) {
        currentUser.requireAdmin();
        ProductCategory category = new ProductCategory(request.nameKa().trim(), request.nameEn().trim(),
                request.sortOrder() == null ? (int) categories.count() : request.sortOrder());
        return TypeDto.of(categories.save(category));
    }

    @PutMapping("/admin/categories/{id}")
    @Transactional
    public TypeDto updateCategory(@PathVariable Long id, @Valid @RequestBody NamedRequest request) {
        currentUser.requireAdmin();
        ProductCategory category = categories.findById(id).orElseThrow(ApiException::notFound);
        category.setNameKa(request.nameKa().trim());
        category.setNameEn(request.nameEn().trim());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return TypeDto.of(category);
    }

    @PostMapping("/admin/products")
    @Transactional
    public ProductDto addProduct(@Valid @RequestBody ProductRequest request) {
        User admin = currentUser.requireAdmin();
        Product product = new Product();
        apply(product, request);
        product = products.save(product);
        audit.record(null, "Product", product.getId(), "CREATED", product.getNameKa() + " " + product.getPrice(), admin);
        return ProductDto.of(product);
    }

    @PutMapping("/admin/products/{id}")
    @Transactional
    public ProductDto updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        User admin = currentUser.requireAdmin();
        Product product = products.findById(id).orElseThrow(ApiException::notFound);
        String before = product.getNameKa() + " " + product.getPrice() + " " + product.getStatus();
        apply(product, request);
        audit.record(null, "Product", id, "UPDATED",
                before + " -> " + product.getNameKa() + " " + product.getPrice() + " " + product.getStatus(), admin);
        return ProductDto.of(product);
    }

    private void apply(Product product, ProductRequest request) {
        product.setBrand(brands.findById(request.brandId()).orElseThrow(() -> ApiException.field("brandId", "invalid")));
        product.setCategory(categories.findById(request.categoryId()).orElseThrow(() -> ApiException.field("categoryId", "invalid")));
        product.setSectionKa(blankToNull(request.sectionKa()));
        product.setSectionEn(blankToNull(request.sectionEn()));
        product.setNameKa(request.nameKa().trim());
        product.setNameEn(request.nameEn().trim());
        product.setPackSize(blankToNull(request.packSize()));
        product.setUnit(blankToNull(request.unit()));
        product.setPrice(request.price());
        product.setJuicePercent(request.juicePercent());
        product.setStatus(request.status());
        product.setDescription(blankToNull(request.description()));
        if (request.sortOrder() != null) {
            product.setSortOrder(request.sortOrder());
        }
        product.getFlavors().clear();
        if (request.flavorIds() != null) {
            product.getFlavors().addAll(flavors.findAllById(request.flavorIds()));
        }
        product.getApplications().clear();
        if (request.applications() != null) {
            product.getApplications().addAll(request.applications());
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
