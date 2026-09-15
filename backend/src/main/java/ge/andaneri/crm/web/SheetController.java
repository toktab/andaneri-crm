package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessSheet;
import ge.andaneri.crm.domain.BusinessSheetRepository;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.Workbook;
import ge.andaneri.crm.domain.WorkbookRepository;
import ge.andaneri.crm.service.AuditService;
import ge.andaneri.crm.web.CatalogDtos.SheetDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sheets, like the tabs of a workbook, inside a project or on their own. Anyone can add, rename and move
 * one; deleting and merging is for supervisors, and never deletes a business.
 */
@RestController
@RequestMapping("/api/sheets")
public class SheetController {

    public record SheetRequest(@NotBlank @Size(max = 80) String name, Long workbookId, Integer sortOrder, @Size(max = 20) String color) {
    }

    private final BusinessSheetRepository sheets;
    private final WorkbookRepository workbooks;
    private final BusinessRepository businesses;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public SheetController(BusinessSheetRepository sheets, WorkbookRepository workbooks, BusinessRepository businesses,
            CurrentUser currentUser, AuditService audit) {
        this.sheets = sheets;
        this.workbooks = workbooks;
        this.businesses = businesses;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SheetDto> list() {
        Map<Long, Long> counts = counts();
        return sheets.findAllByOrderBySortOrderAscIdAsc().stream().map(s -> dto(s, counts)).toList();
    }

    @PostMapping
    @Transactional
    public SheetDto create(@Valid @RequestBody SheetRequest request) {
        User user = currentUser.require();
        Workbook workbook = workbook(request.workbookId());
        String name = request.name().trim();
        if (existing(workbook, name).isPresent()) {
            throw ApiException.field("name", "taken");
        }
        BusinessSheet sheet = new BusinessSheet();
        sheet.setWorkbook(workbook);
        sheet.setName(name);
        sheet.setColor(request.color());
        sheet.setSortOrder(request.sortOrder() == null
                ? (int) (workbook == null ? sheets.count() : sheets.countByWorkbookId(workbook.getId())) : request.sortOrder());
        sheet.setCreatedBy(user);
        return dto(sheets.save(sheet), Map.of());
    }

    /** Rename, recolour, reorder, or move to another project (workbookId). */
    @PutMapping("/{id}")
    @Transactional
    public SheetDto update(@PathVariable Long id, @Valid @RequestBody SheetRequest request) {
        currentUser.require();
        BusinessSheet sheet = sheets.findById(id).orElseThrow(ApiException::notFound);
        Workbook workbook = workbook(request.workbookId());
        String name = request.name().trim();
        existing(workbook, name).filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw ApiException.field("name", "taken"); });
        sheet.setWorkbook(workbook);
        sheet.setName(name);
        sheet.setColor(request.color());
        if (request.sortOrder() != null) {
            sheet.setSortOrder(request.sortOrder());
        }
        return dto(sheet, counts());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User user = currentUser.requireSupervisor();
        BusinessSheet sheet = sheets.findById(id).orElseThrow(ApiException::notFound);
        int unfiled = businesses.clearSheet(id);
        audit.record(null, "BusinessSheet", id, "DELETED", sheet.getName() + " (" + unfiled + " businesses unfiled)", user);
        sheets.delete(sheet);
        return ResponseEntity.noContent().build();
    }

    /** Moves every business of this sheet into the target sheet, then removes this one. */
    @PostMapping("/{id}/merge-into/{targetId}")
    @Transactional
    public SheetDto merge(@PathVariable Long id, @PathVariable Long targetId) {
        User user = currentUser.requireSupervisor();
        if (Objects.equals(id, targetId)) {
            throw ApiException.badRequest("SAME_SHEET");
        }
        BusinessSheet source = sheets.findById(id).orElseThrow(ApiException::notFound);
        BusinessSheet target = sheets.findById(targetId).orElseThrow(ApiException::notFound);
        int moved = businesses.moveSheet(id, target);
        audit.record(null, "BusinessSheet", targetId, "MERGED", source.getName() + " -> " + target.getName() + " (" + moved + ")", user);
        sheets.delete(source);
        return dto(target, counts());
    }

    Map<Long, Long> counts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : sheets.countBusinesses()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    static SheetDto dto(BusinessSheet s, Map<Long, Long> counts) {
        return new SheetDto(s.getId(), s.getWorkbook() == null ? null : s.getWorkbook().getId(), s.getName(), s.getSortOrder(),
                s.getColor(), counts.getOrDefault(s.getId(), 0L));
    }

    private java.util.Optional<BusinessSheet> existing(Workbook workbook, String name) {
        return workbook == null ? sheets.findFirstByWorkbookIsNullAndNameIgnoreCase(name)
                : sheets.findFirstByWorkbookIdAndNameIgnoreCase(workbook.getId(), name);
    }

    private Workbook workbook(Long id) {
        return id == null ? null : workbooks.findById(id).orElseThrow(() -> ApiException.field("workbookId", "invalid"));
    }
}
