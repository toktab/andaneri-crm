package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.service.BusinessService;
import ge.andaneri.crm.web.BusinessDtos.ArchiveRequest;
import ge.andaneri.crm.web.BusinessDtos.AssignRequest;
import ge.andaneri.crm.web.BusinessDtos.BulkRequest;
import ge.andaneri.crm.web.BusinessDtos.BulkResult;
import ge.andaneri.crm.web.BusinessDtos.BusinessDetail;
import ge.andaneri.crm.web.BusinessDtos.BusinessRequest;
import ge.andaneri.crm.web.BusinessDtos.BusinessSummary;
import ge.andaneri.crm.web.BusinessDtos.CategoryUsageDto;
import ge.andaneri.crm.web.BusinessDtos.CategoryUsageRequest;
import ge.andaneri.crm.web.BusinessDtos.ContactDto;
import ge.andaneri.crm.web.BusinessDtos.ContactRequest;
import ge.andaneri.crm.web.BusinessDtos.DuplicateDto;
import ge.andaneri.crm.web.BusinessDtos.GridRow;
import ge.andaneri.crm.web.BusinessDtos.InterestDto;
import ge.andaneri.crm.web.BusinessDtos.InterestRequest;
import ge.andaneri.crm.web.BusinessDtos.InterestUpdate;
import ge.andaneri.crm.web.BusinessDtos.PageDto;
import ge.andaneri.crm.web.BusinessDtos.PatchRequest;
import ge.andaneri.crm.web.BusinessDtos.StatusRequest;
import ge.andaneri.crm.web.BusinessDtos.UsageDto;
import ge.andaneri.crm.web.BusinessDtos.UsageRequest;
import ge.andaneri.crm.web.BusinessDtos.UsageUpdate;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

    private final BusinessService service;
    private final CurrentUser currentUser;

    public BusinessController(BusinessService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageDto<BusinessSummary> search(@ModelAttribute BusinessQuery query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort) {
        currentUser.require();
        return service.search(query.toFilter(), page, size, sort);
    }

    /** The spreadsheet view: every field of every row, filtered like the list. Up to 500 rows a page. */
    @GetMapping("/grid")
    public PageDto<GridRow> grid(@ModelAttribute BusinessQuery query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size,
            @RequestParam(required = false) String sort) {
        return service.grid(query.toFilter(), page, size, sort, currentUser.require());
    }

    /** Live "is this already in the CRM?" check for the add form. */
    @GetMapping("/duplicates")
    public List<DuplicateDto> duplicates(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String idCode,
            @RequestParam(required = false) Long excludeId) {
        currentUser.require();
        return service.findDuplicates(name, address, phone, idCode, excludeId);
    }

    @GetMapping("/{id}")
    public BusinessDetail detail(@PathVariable Long id) {
        return service.detail(id, currentUser.require());
    }

    /** Answers 409 DUPLICATE with the likely matches, unless {@code force=true}. */
    @PostMapping
    public BusinessDetail create(@Valid @RequestBody BusinessRequest request, @RequestParam(defaultValue = "false") boolean force) {
        return service.create(request, force, currentUser.require());
    }

    @PutMapping("/{id}")
    public BusinessDetail update(@PathVariable Long id, @Valid @RequestBody BusinessRequest request) {
        return service.update(id, request, currentUser.require());
    }

    /** One spreadsheet cell (or a few): {"changes": {"phone": "..."}, "version": 3}. */
    @PatchMapping("/{id}")
    public GridRow patch(@PathVariable Long id, @RequestBody PatchRequest request) {
        return service.patch(id, request.changes(), request.version(), currentUser.require());
    }

    @PostMapping("/bulk")
    public BulkResult bulk(@RequestBody BulkRequest request) {
        return service.bulk(request, currentUser.require());
    }

    @PostMapping("/{id}/status")
    public BusinessDetail status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(id, request.status(), request.note(), currentUser.require());
    }

    @PostMapping("/{id}/assign")
    public BusinessDetail assign(@PathVariable Long id, @RequestBody AssignRequest request) {
        return service.assign(id, request.userId(), currentUser.require());
    }

    @PostMapping("/{id}/archive")
    public BusinessDetail archive(@PathVariable Long id, @RequestBody ArchiveRequest request) {
        return service.setArchived(id, Boolean.TRUE.equals(request.archived()), currentUser.require());
    }

    // ------------------------------------------------------------ contacts

    @PostMapping("/{id}/contacts")
    public ContactDto addContact(@PathVariable Long id, @Valid @RequestBody ContactRequest request) {
        return service.addContact(id, request, currentUser.require());
    }

    @PutMapping("/{id}/contacts/{contactId}")
    public ContactDto updateContact(@PathVariable Long id, @PathVariable Long contactId, @Valid @RequestBody ContactRequest request) {
        return service.updateContact(id, contactId, request, currentUser.require());
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id, @PathVariable Long contactId) {
        service.deleteContact(id, contactId, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ usage and interests

    @PutMapping("/{id}/category-usages")
    public List<CategoryUsageDto> categoryUsages(@PathVariable Long id, @Valid @RequestBody List<CategoryUsageRequest> answers) {
        return service.setCategoryAnswers(id, answers, currentUser.require());
    }

    @PostMapping("/{id}/usages")
    public List<UsageDto> addUsages(@PathVariable Long id, @Valid @RequestBody UsageRequest request) {
        return service.addUsages(id, request, currentUser.require());
    }

    @PutMapping("/{id}/usages/{usageId}")
    public List<UsageDto> updateUsage(@PathVariable Long id, @PathVariable Long usageId, @Valid @RequestBody UsageUpdate request) {
        return service.updateUsage(id, usageId, request, currentUser.require());
    }

    @DeleteMapping("/{id}/usages/{usageId}")
    public List<UsageDto> deleteUsage(@PathVariable Long id, @PathVariable Long usageId) {
        return service.deleteUsage(id, usageId, currentUser.require());
    }

    @PostMapping("/{id}/interests")
    public List<InterestDto> addInterests(@PathVariable Long id, @Valid @RequestBody InterestRequest request) {
        return service.addInterests(id, request, currentUser.require());
    }

    @PutMapping("/{id}/interests/{interestId}")
    public List<InterestDto> updateInterest(@PathVariable Long id, @PathVariable Long interestId, @Valid @RequestBody InterestUpdate request) {
        return service.updateInterest(id, interestId, request, currentUser.require());
    }

    @DeleteMapping("/{id}/interests/{interestId}")
    public List<InterestDto> deleteInterest(@PathVariable Long id, @PathVariable Long interestId) {
        return service.deleteInterest(id, interestId, currentUser.require());
    }
}
