package ge.andaneri.crm.service;

import ge.andaneri.crm.domain.BusinessSheet;
import ge.andaneri.crm.domain.BusinessSheetRepository;
import ge.andaneri.crm.domain.CustomField;
import ge.andaneri.crm.domain.CustomFieldRepository;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.Workbook;
import ge.andaneri.crm.domain.WorkbookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds projects, sheets and custom fields by name, creating them when they do not exist yet: what the
 * Excel and JSON imports do with a file name, its tabs and its unknown columns.
 */
@Service
public class WorkspaceService {

    private final WorkbookRepository workbooks;
    private final BusinessSheetRepository sheets;
    private final CustomFieldRepository fields;

    public WorkspaceService(WorkbookRepository workbooks, BusinessSheetRepository sheets, CustomFieldRepository fields) {
        this.workbooks = workbooks;
        this.sheets = sheets;
        this.fields = fields;
    }

    @Transactional
    public Workbook workbook(String name, String sourceFile, User user) {
        String clean = cut(name, 120);
        return workbooks.findFirstByNameIgnoreCase(clean).orElseGet(() -> {
            Workbook workbook = new Workbook();
            workbook.setName(clean);
            workbook.setSourceFile(sourceFile == null ? null : cut(sourceFile, 255));
            workbook.setSortOrder((int) workbooks.count());
            workbook.setCreatedBy(user);
            return workbooks.save(workbook);
        });
    }

    /** A sheet of that name within the project (or outside any project when {@code workbook} is null). */
    @Transactional
    public BusinessSheet sheet(String name, Workbook workbook, User user) {
        String clean = cut(name, 80);
        return (workbook == null ? sheets.findFirstByWorkbookIsNullAndNameIgnoreCase(clean)
                : sheets.findFirstByWorkbookIdAndNameIgnoreCase(workbook.getId(), clean)).orElseGet(() -> {
                    BusinessSheet sheet = new BusinessSheet();
                    sheet.setWorkbook(workbook);
                    sheet.setName(clean);
                    sheet.setSortOrder((int) (workbook == null ? sheets.count() : sheets.countByWorkbookId(workbook.getId())));
                    sheet.setCreatedBy(user);
                    return sheets.save(sheet);
                });
    }

    /** A custom field with that label; a retired one of the same name comes back into use. */
    @Transactional
    public CustomField field(String label, User user) {
        String clean = cut(label, 80);
        return fields.findFirstByLabelIgnoreCase(clean).map(existing -> {
            existing.setActive(true);
            return existing;
        }).orElseGet(() -> {
            CustomField field = new CustomField();
            field.setLabel(clean);
            field.setSortOrder((int) fields.count());
            field.setCreatedBy(user);
            return fields.save(field);
        });
    }

    private static String cut(String value, int max) {
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
