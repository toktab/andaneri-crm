package ge.andaneri.crm.service;

import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.Priority;
import java.util.List;

/**
 * Every way the business list can be narrowed. All of them combine: "cafes in Vake using Monin,
 * not contacted for 14 days" is typeId + district + brandId + notContactedDays.
 *
 * @param q                 name, address, phone, identification code, district, or a contact's name or phone
 * @param brandId           uses this brand (any category)
 * @param flavorId          uses this flavor (any brand)
 * @param interestFlavorId  interested in this flavor, and has not turned it down
 * @param notContactedDays  no call, visit or meeting logged for at least this many days
 * @param purchasedDaysAgo  last order at least this many days ago
 * @param followUpDue       has an open task due today or earlier
 * @param sheetId           filed under this sheet
 * @param workbookId        filed under any sheet of this project
 * @param unfiled           not filed under any sheet
 */
public record BusinessFilter(
        String q,
        List<BusinessStatus> statuses,
        Long typeId,
        String district,
        String city,
        Long assignedToId,
        boolean unassigned,
        Long brandId,
        Long flavorId,
        Long interestFlavorId,
        Boolean customer,
        Integer notContactedDays,
        Integer purchasedDaysAgo,
        Priority priority,
        boolean archived,
        boolean followUpDue,
        Long sheetId,
        Long workbookId,
        boolean unfiled) {
}
