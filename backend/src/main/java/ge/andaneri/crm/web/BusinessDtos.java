package ge.andaneri.crm.web;

import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.CategoryUsage;
import ge.andaneri.crm.domain.Contact;
import ge.andaneri.crm.domain.ContactChannel;
import ge.andaneri.crm.domain.DrinkType;
import ge.andaneri.crm.domain.Interest;
import ge.andaneri.crm.domain.InterestReason;
import ge.andaneri.crm.domain.InterestStatus;
import ge.andaneri.crm.domain.Openness;
import ge.andaneri.crm.domain.PriceSensitivity;
import ge.andaneri.crm.domain.Priority;
import ge.andaneri.crm.domain.ProductUsage;
import ge.andaneri.crm.domain.Satisfaction;
import ge.andaneri.crm.domain.UsageAnswer;
import ge.andaneri.crm.domain.UsageSource;
import ge.andaneri.crm.web.CatalogDtos.FlavorDto;
import ge.andaneri.crm.web.CatalogDtos.ProductRef;
import ge.andaneri.crm.web.UserDtos.UserRef;
import ge.andaneri.crm.domain.TastingFeedback;
import ge.andaneri.crm.web.WorkDtos.ActivityDto;
import ge.andaneri.crm.web.WorkDtos.TaskDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class BusinessDtos {

    private BusinessDtos() {
    }

    public record PageDto<T>(List<T> items, long total, int page, int size) {
    }

    /** Create and edit share one shape. Only the name is required: add fast, fill in later. */
    public record BusinessRequest(
            @NotBlank @Size(max = 160) String name,
            Long typeId,
            BusinessStatus status,
            Priority priority,
            @Size(max = 255) String address,
            @Size(max = 80) String city,
            @Size(max = 80) String district,
            @Size(max = 60) String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 200) String website,
            @Size(max = 500) String mapsUrl,
            BigDecimal latitude,
            BigDecimal longitude,
            @Size(max = 40) String idCode,
            @Size(max = 200) String legalName,
            @Min(0) @Max(10000) Integer branches,
            @Size(max = 120) String visitHours,
            String notes,
            @Size(max = 200) String menuChange,
            Openness switchOpenness,
            PriceSensitivity priceSensitivity,
            Satisfaction satisfaction,
            String competitorNotes,
            @Min(1) @Max(365) Integer reorderDays,
            Long assignedToId,
            Set<DrinkType> drinkTypes,
            /** The version the form was loaded at; a newer one on the server means someone else saved first. */
            Long version,
            /** Optional first contact, so the quick-add form can take "Giorgi, bar manager" too. */
            @Valid ContactRequest firstContact,
            /** The sheet to file it under; null for none. */
            Long sheetId,
            /** Custom field id to value; a blank value clears it. Null leaves custom values alone. */
            java.util.Map<Long, String> customValues) {
    }

    /** One spreadsheet cell or a few at once: {"phone": "599...", "custom.3": "yes"}. */
    public record PatchRequest(java.util.Map<String, Object> changes, Long version) {
    }

    /** The same change for many selected rows. Supervisor-only parts: assigning, unassigning, archiving. */
    public record BulkRequest(List<Long> ids, Long sheetId, Boolean clearSheet, BusinessStatus status, Priority priority,
            Long assignedToId, Boolean unassign, Boolean archived) {
    }

    public record BulkResult(int updated, int skipped) {
    }

    /** One row of the spreadsheet view: every field there is, plus what the other screens work out. */
    public record GridRow(Long id, String name, String legalName, Long typeId, BusinessStatus status, Priority priority,
            String phone, String email, String website, String mapsUrl, String address, String city, String district,
            String idCode, Integer branches, String visitHours, String notes, String menuChange, String competitorNotes,
            Openness switchOpenness, PriceSensitivity priceSensitivity, Satisfaction satisfaction, Integer reorderDays,
            UserRef assignedTo, Long sheetId, Instant lastContactAt, LocalDate lastPurchaseDate, int purchaseCount,
            NextTaskRef nextTask, List<String> brands, List<Long> flavorIds, String contacts,
            java.util.Map<Long, String> customValues, long version, boolean canEdit, boolean archived, Instant createdAt) {
    }

    public record NextTaskRef(Long id, String type, Instant dueAt) {
    }

    public record BusinessSummary(Long id, String name, Long typeId, BusinessStatus status, Priority priority,
            String address, String district, String city, String phone, String mapsUrl, UserRef assignedTo,
            Instant lastContactAt, LocalDate lastPurchaseDate, int purchaseCount, NextTaskRef nextTask,
            List<String> brands, boolean archived, Long sheetId) {
    }

    public record PurchaseSummary(int count, LocalDate lastDate, BigDecimal total, Integer averageDays,
            int expectedDays, LocalDate nextReorderDate, Long daysSinceLast) {
    }

    /** One of our products worth offering, and why: what it replaces, what they asked for, or a trend fit. */
    public record Suggestion(String kind, ProductRef product, String sectionKa, String sectionEn, String matchKa, String matchEn) {
    }

    /** {@code strong}: certainly the same place. Otherwise only possibly (a shared phone or ID code, as chain branches have). */
    public record DuplicateDto(Long id, String name, String address, String phone, String reason, boolean strong) {
    }

    public record BusinessDetail(Long id, String name, Long typeId, BusinessStatus status, Priority priority,
            String address, String city, String district, String phone, String email, String website, String mapsUrl,
            BigDecimal latitude, BigDecimal longitude, String idCode, String legalName, Integer branches,
            String visitHours, String notes, String menuChange,
            Openness switchOpenness, PriceSensitivity priceSensitivity, Satisfaction satisfaction,
            String competitorNotes, Integer reorderDays, UserRef assignedTo, UserRef createdBy,
            Instant lastContactAt, boolean archived, Instant createdAt, Instant updatedAt, long version,
            Set<DrinkType> drinkTypes, List<ContactDto> contacts, List<CategoryUsageDto> categoryUsages,
            List<UsageDto> usages, List<InterestDto> interests, List<TaskDto> openTasks,
            PurchaseSummary purchases, List<Suggestion> suggestions, ActivityDto lastActivity,
            /** What nobody has found out yet (see BusinessService#missingInfo): the "ask them" list. */
            List<String> missing, boolean canEdit, Long sheetId, String sheetName, Long workbookId, String workbookName,
            java.util.Map<Long, String> customValues) {

        public static BusinessDetail of(Business b, List<ContactDto> contacts, List<CategoryUsageDto> categoryUsages,
                List<UsageDto> usages, List<InterestDto> interests, List<TaskDto> openTasks,
                PurchaseSummary purchases, List<Suggestion> suggestions, ActivityDto lastActivity,
                List<String> missing, boolean canEdit, java.util.Map<Long, String> customValues) {
            var sheet = b.getSheet();
            var workbook = sheet == null ? null : sheet.getWorkbook();
            return new BusinessDetail(b.getId(), b.getName(), b.getType() == null ? null : b.getType().getId(),
                    b.getStatus(), b.getPriority(), b.getAddress(), b.getCity(), b.getDistrict(), b.getPhone(),
                    b.getEmail(), b.getWebsite(), b.getMapsUrl(), b.getLatitude(), b.getLongitude(), b.getIdCode(),
                    b.getLegalName(), b.getBranches(), b.getVisitHours(), b.getNotes(), b.getMenuChange(), b.getSwitchOpenness(), b.getPriceSensitivity(), b.getSatisfaction(),
                    b.getCompetitorNotes(), b.getReorderDays(), UserRef.of(b.getAssignedTo()), UserRef.of(b.getCreatedBy()),
                    b.getLastContactAt(), b.isArchived(), b.getCreatedAt(), b.getUpdatedAt(), b.getVersion(),
                    Set.copyOf(b.getDrinkTypes()), contacts, categoryUsages, usages, interests, openTasks, purchases,
                    suggestions, lastActivity, missing, canEdit, sheet == null ? null : sheet.getId(),
                    sheet == null ? null : sheet.getName(), workbook == null ? null : workbook.getId(),
                    workbook == null ? null : workbook.getName(), customValues);
        }
    }

    // ------------------------------------------------------------ contacts

    public record ContactRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 80) String roleTitle,
            @Size(max = 60) String phone,
            @Email @Size(max = 120) String email,
            ContactChannel preferredChannel,
            Boolean decisionMaker,
            String notes) {
    }

    public record ContactDto(Long id, String name, String roleTitle, String phone, String email,
            ContactChannel preferredChannel, boolean decisionMaker, String notes) {
        public static ContactDto of(Contact c) {
            return new ContactDto(c.getId(), c.getName(), c.getRoleTitle(), c.getPhone(), c.getEmail(),
                    c.getPreferredChannel(), c.isDecisionMaker(), c.getNotes());
        }
    }

    // ------------------------------------------------------------ what they use and want

    public record CategoryUsageRequest(@NotNull Long categoryId, @NotNull UsageAnswer answer, String notes) {
    }

    public record CategoryUsageDto(Long categoryId, UsageAnswer answer, String notes, Instant updatedAt) {
        public static CategoryUsageDto of(CategoryUsage u) {
            return new CategoryUsageDto(u.getCategory().getId(), u.getAnswer(), u.getNotes(), u.getUpdatedAt());
        }
    }

    /** Adds one usage row per flavor ("Monin: Vanilla, Caramel" is two rows), or one row with no flavor. */
    public record UsageRequest(
            @NotNull Long categoryId,
            Long brandId,
            /** Bought, fresh, or made by the bar itself. Absent means bought. */
            UsageSource source,
            List<Long> flavorIds,
            @Size(max = 160) String productName,
            @Size(max = 80) String quantity,
            @Size(max = 80) String frequency,
            String notes) {
    }

    public record UsageUpdate(
            @NotNull Long categoryId,
            Long brandId,
            UsageSource source,
            Long flavorId,
            @Size(max = 160) String productName,
            @Size(max = 80) String quantity,
            @Size(max = 80) String frequency,
            String notes) {
    }

    public record UsageDto(Long id, Long categoryId, Long brandId, String brandName, boolean ownBrand, FlavorDto flavor,
            UsageSource source, String productName, String quantity, String frequency, String notes, Instant createdAt) {
        public static UsageDto of(ProductUsage u) {
            return new UsageDto(u.getId(), u.getCategory().getId(),
                    u.getBrand() == null ? null : u.getBrand().getId(),
                    u.getBrand() == null ? null : u.getBrand().getName(),
                    u.getBrand() != null && u.getBrand().isOwn(),
                    FlavorDto.of(u.getFlavor()), u.getSource(), u.getProductName(), u.getQuantity(), u.getFrequency(), u.getNotes(),
                    u.getCreatedAt());
        }
    }

    /** One interest row per flavor and per product given. */
    public record InterestRequest(
            List<Long> flavorIds,
            List<Long> productIds,
            InterestStatus status,
            InterestReason reason,
            TastingFeedback feedback,
            String notes) {
    }

    public record InterestUpdate(@NotNull InterestStatus status, InterestReason reason, TastingFeedback feedback, String notes) {
    }

    public record InterestDto(Long id, FlavorDto flavor, ProductRef product, InterestStatus status,
            InterestReason reason, TastingFeedback feedback, String notes, Instant updatedAt) {
        public static InterestDto of(Interest i) {
            return new InterestDto(i.getId(), FlavorDto.of(i.getFlavor()), ProductRef.of(i.getProduct()),
                    i.getStatus(), i.getReason(), i.getFeedback(), i.getNotes(), i.getUpdatedAt());
        }
    }

    public record StatusRequest(@NotNull BusinessStatus status, @Size(max = 255) String note) {
    }

    public record AssignRequest(Long userId) {
    }

    public record ArchiveRequest(Boolean archived) {
    }
}
