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
import ge.andaneri.crm.web.CatalogDtos.WorkbookDto;
import ge.andaneri.crm.web.CatalogDtos.WorkspaceTree;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projects: sets of sheets, usually one per imported Excel file. They can be renamed, merged into one
 * another, or removed; removing a project never removes a business.
 */
@RestController
@RequestMapping("/api/workbooks")
public class WorkbookController {

    public record WorkbookRequest(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description,
            @Size(max = 20) String color, Integer sortOrder) {
    }

    private final WorkbookRepository workbooks;
    private final BusinessSheetRepository sheets;
    private final BusinessRepository businesses;
    private final SheetController sheetController;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public WorkbookController(WorkbookRepository workbooks, BusinessSheetRepository sheets, BusinessRepository businesses,
            SheetController sheetController, CurrentUser currentUser, AuditService audit) {
        this.workbooks = workbooks;
        this.sheets = sheets;
        this.businesses = businesses;
        this.sheetController = sheetController;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** The whole tree in one call: projects with their sheets and counts, loose sheets, and the unfiled count. */
    @GetMapping("/tree")
    @Transactional(readOnly = true)
    public WorkspaceTree tree() {
        currentUser.require();
        Map<Long, Long> counts = sheetController.counts();
        List<BusinessSheet> allSheets = sheets.findAllByOrderBySortOrderAscIdAsc();
        List<WorkbookDto> projects = workbooks.findAllByOrderBySortOrderAscIdAsc().stream().map(w -> {
            List<SheetDto> own = allSheets.stream()
                    .filter(s -> s.getWorkbook() != null && s.getWorkbook().getId().equals(w.getId()))
                    .map(s -> SheetController.dto(s, counts)).toList();
            return new WorkbookDto(w.getId(), w.getName(), w.getDescription(), w.getColor(), w.getSortOrder(), w.getSourceFile(),
                    w.getCreatedAt(), own, own.stream().mapToLong(SheetDto::businesses).sum());
        }).toList();
        List<SheetDto> loose = allSheets.stream().filter(s -> s.getWorkbook() == null).map(s -> SheetController.dto(s, counts)).toList();
        return new WorkspaceTree(projects, loose, businesses.countUnfiled(), businesses.countActive());
    }

    @PostMapping
    @Transactional
    public WorkbookDto create(@Valid @RequestBody WorkbookRequest request) {
        User user = currentUser.require();
        Workbook workbook = new Workbook();
        apply(workbook, request);
        if (request.sortOrder() == null) {
            workbook.setSortOrder((int) workbooks.count());
        }
        workbook.setCreatedBy(user);
        workbook = workbooks.save(workbook);
        return dto(workbook);
    }

    @PutMapping("/{id}")
    @Transactional
    public WorkbookDto update(@PathVariable Long id, @Valid @RequestBody WorkbookRequest request) {
        currentUser.require();
        Workbook workbook = workbooks.findById(id).orElseThrow(ApiException::notFound);
        apply(workbook, request);
        return dto(workbook);
    }

    /**
     * Removes a project. With {@code deleteSheets} its sheets go too (their businesses become unfiled);
     * without, the sheets stay, outside any project.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean deleteSheets) {
        User user = currentUser.requireSupervisor();
        Workbook workbook = workbooks.findById(id).orElseThrow(ApiException::notFound);
        if (deleteSheets) {
            for (BusinessSheet sheet : sheets.findByWorkbookIdOrderBySortOrderAscIdAsc(id)) {
                businesses.clearSheet(sheet.getId());
                sheets.delete(sheet);
            }
        } else {
            sheets.detachFromWorkbook(id);
        }
        audit.record(null, "Workbook", id, "DELETED", workbook.getName() + (deleteSheets ? " with its sheets" : ""), user);
        workbooks.delete(workbook);
        return ResponseEntity.noContent().build();
    }

    /** Moves all of this project's sheets into the target project, then removes this one. */
    @PostMapping("/{id}/merge-into/{targetId}")
    @Transactional
    public WorkbookDto merge(@PathVariable Long id, @PathVariable Long targetId) {
        User user = currentUser.requireSupervisor();
        if (Objects.equals(id, targetId)) {
            throw ApiException.badRequest("SAME_WORKBOOK");
        }
        Workbook source = workbooks.findById(id).orElseThrow(ApiException::notFound);
        Workbook target = workbooks.findById(targetId).orElseThrow(ApiException::notFound);
        int moved = sheets.moveAllToWorkbook(id, target);
        audit.record(null, "Workbook", targetId, "MERGED", source.getName() + " -> " + target.getName() + " (" + moved + " sheets)", user);
        workbooks.delete(source);
        return dto(target);
    }

    private static void apply(Workbook workbook, WorkbookRequest request) {
        workbook.setName(request.name().trim());
        workbook.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        workbook.setColor(request.color());
        if (request.sortOrder() != null) {
            workbook.setSortOrder(request.sortOrder());
        }
    }

    private WorkbookDto dto(Workbook w) {
        return new WorkbookDto(w.getId(), w.getName(), w.getDescription(), w.getColor(), w.getSortOrder(), w.getSourceFile(),
                w.getCreatedAt(), List.of(), 0);
    }
}
