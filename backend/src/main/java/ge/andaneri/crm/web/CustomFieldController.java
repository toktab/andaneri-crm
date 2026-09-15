package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.domain.CustomField;
import ge.andaneri.crm.domain.CustomFieldRepository;
import ge.andaneri.crm.service.WorkspaceService;
import ge.andaneri.crm.web.CatalogDtos.FieldDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fields the team adds, most often while importing a spreadsheet column that has no built-in place.
 * Anyone can add one (an existing field with the same name is reused); renaming and retiring is for admins.
 */
@RestController
@RequestMapping("/api")
public class CustomFieldController {

    public record FieldRequest(@NotBlank @Size(max = 80) String label, Integer sortOrder, Boolean active) {
    }

    private final CustomFieldRepository fields;
    private final WorkspaceService workspace;
    private final CurrentUser currentUser;

    public CustomFieldController(CustomFieldRepository fields, WorkspaceService workspace, CurrentUser currentUser) {
        this.fields = fields;
        this.workspace = workspace;
        this.currentUser = currentUser;
    }

    @PostMapping("/custom-fields")
    public FieldDto create(@Valid @RequestBody FieldRequest request) {
        return FieldDto.of(workspace.field(request.label(), currentUser.require()));
    }

    @PutMapping("/admin/custom-fields/{id}")
    @Transactional
    public FieldDto update(@PathVariable Long id, @Valid @RequestBody FieldRequest request) {
        currentUser.requireAdmin();
        CustomField field = fields.findById(id).orElseThrow(ApiException::notFound);
        field.setLabel(request.label().trim());
        if (request.sortOrder() != null) {
            field.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            field.setActive(request.active());
        }
        return FieldDto.of(field);
    }
}
