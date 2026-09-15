package ge.andaneri.crm.web;

import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.Priority;
import ge.andaneri.crm.service.BusinessFilter;
import java.util.List;

/**
 * The list's query parameters, bound once and shared by the list, the spreadsheet view and the export,
 * so all three always show exactly the same businesses. {@code status} may be "NEW,CONTACTED".
 */
public record BusinessQuery(
        String q,
        List<BusinessStatus> status,
        Long typeId,
        String district,
        String city,
        Long assignedToId,
        Boolean unassigned,
        Long brandId,
        Long flavorId,
        Long interestFlavorId,
        Boolean customer,
        Integer notContactedDays,
        Integer purchasedDaysAgo,
        Priority priority,
        Boolean archived,
        Boolean followUpDue,
        Long sheetId,
        Long workbookId,
        Boolean unfiled) {

    public BusinessFilter toFilter() {
        return new BusinessFilter(q, status, typeId, district, city, assignedToId, Boolean.TRUE.equals(unassigned), brandId,
                flavorId, interestFlavorId, customer, notContactedDays, purchasedDaysAgo, priority, Boolean.TRUE.equals(archived),
                Boolean.TRUE.equals(followUpDue), sheetId, workbookId, Boolean.TRUE.equals(unfiled));
    }
}
