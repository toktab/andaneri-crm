package ge.andaneri.crm.web;

import ge.andaneri.crm.domain.Brand;
import ge.andaneri.crm.domain.BusinessType;
import ge.andaneri.crm.domain.DrinkType;
import ge.andaneri.crm.domain.Flavor;
import ge.andaneri.crm.domain.Product;
import ge.andaneri.crm.domain.ProductCategory;
import ge.andaneri.crm.domain.ProductStatus;
import ge.andaneri.crm.domain.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record TypeDto(Long id, String nameKa, String nameEn, int sortOrder, boolean active) {
        public static TypeDto of(BusinessType type) {
            return new TypeDto(type.getId(), type.getNameKa(), type.getNameEn(), type.getSortOrder(), type.isActive());
        }

        public static TypeDto of(ProductCategory category) {
            return new TypeDto(category.getId(), category.getNameKa(), category.getNameEn(), category.getSortOrder(), category.isActive());
        }
    }

    public record BrandDto(Long id, String name, boolean own, boolean active) {
        public static BrandDto of(Brand brand) {
            return new BrandDto(brand.getId(), brand.getName(), brand.isOwn(), brand.isActive());
        }
    }

    public record FlavorDto(Long id, String nameKa, String nameEn, boolean active) {
        public static FlavorDto of(Flavor flavor) {
            return flavor == null ? null : new FlavorDto(flavor.getId(), flavor.getNameKa(), flavor.getNameEn(), flavor.isActive());
        }
    }

    public record ProductDto(Long id, Long brandId, String brandName, boolean own, Long categoryId,
            String sectionKa, String sectionEn, String nameKa, String nameEn, String packSize, String unit,
            BigDecimal price, Integer juicePercent, ProductStatus status, String description, int sortOrder,
            List<Long> flavorIds, Set<DrinkType> applications) {
        public static ProductDto of(Product product) {
            return new ProductDto(product.getId(), product.getBrand().getId(), product.getBrand().getName(),
                    product.getBrand().isOwn(), product.getCategory().getId(), product.getSectionKa(), product.getSectionEn(),
                    product.getNameKa(), product.getNameEn(), product.getPackSize(), product.getUnit(), product.getPrice(),
                    product.getJuicePercent(), product.getStatus(), product.getDescription(), product.getSortOrder(),
                    product.getFlavors().stream().map(Flavor::getId).toList(), Set.copyOf(product.getApplications()));
        }
    }

    /** Lightweight reference to one of our products, as used in suggestions and purchase lines. */
    public record ProductRef(Long id, String nameKa, String nameEn, BigDecimal price, ProductStatus status) {
        public static ProductRef of(Product product) {
            return product == null ? null
                    : new ProductRef(product.getId(), product.getNameKa(), product.getNameEn(), product.getPrice(), product.getStatus());
        }
    }

    public record AssignableUser(Long id, String fullName, Role role, boolean active) {
    }

    /** A workbook tab and how many (not archived) businesses are filed under it. */
    public record SheetDto(Long id, Long workbookId, String name, int sortOrder, String color, long businesses) {
    }

    /** A project with its sheets, as the Main section's tree shows it. */
    public record WorkbookDto(Long id, String name, String description, String color, int sortOrder, String sourceFile,
            java.time.Instant createdAt, List<SheetDto> sheets, long businesses) {
    }

    /** Projects, sheets outside any project, and how many businesses are not in any sheet. */
    public record WorkspaceTree(List<WorkbookDto> workbooks, List<SheetDto> looseSheets, long unfiled, long total) {
    }

    public record FieldDto(Long id, String label, int sortOrder, boolean active) {
        public static FieldDto of(ge.andaneri.crm.domain.CustomField field) {
            return new FieldDto(field.getId(), field.getLabel(), field.getSortOrder(), field.isActive());
        }
    }

    /** Everything the forms need for their dropdowns, fetched once and cached by the frontend. */
    public record Lookups(List<TypeDto> businessTypes, List<TypeDto> categories, List<BrandDto> brands,
            List<FlavorDto> flavors, List<AssignableUser> users, List<String> districts, List<String> cities,
            Map<String, Integer> settings, List<SheetDto> sheets, List<FieldDto> customFields, List<WorkbookRef> workbooks) {
    }

    public record WorkbookRef(Long id, String name) {
    }

    public record NamedRequest(
            @NotBlank @Size(max = 80) String nameKa,
            @NotBlank @Size(max = 80) String nameEn,
            Integer sortOrder,
            Boolean active) {
    }

    public record BrandRequest(@NotBlank @Size(max = 80) String name, Boolean own, Boolean active) {
    }

    /** Either name may be left blank when adding on the spot; the other one is copied into it. */
    public record FlavorRequest(@Size(max = 100) String nameKa, @Size(max = 100) String nameEn, Boolean active) {
    }

    public record ProductRequest(
            @NotNull Long brandId,
            @NotNull Long categoryId,
            @Size(max = 80) String sectionKa,
            @Size(max = 80) String sectionEn,
            @NotBlank @Size(max = 160) String nameKa,
            @NotBlank @Size(max = 160) String nameEn,
            @Size(max = 40) String packSize,
            @Size(max = 20) String unit,
            @DecimalMin("0") BigDecimal price,
            @Min(0) @Max(100) Integer juicePercent,
            @NotNull ProductStatus status,
            String description,
            Integer sortOrder,
            List<Long> flavorIds,
            Set<DrinkType> applications) {
    }
}
