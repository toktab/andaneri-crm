package ge.andaneri.crm.io;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.io.ImportService.ImportRequest;
import ge.andaneri.crm.io.ImportService.ImportResult;
import ge.andaneri.crm.io.ImportService.ParsedFile;
import ge.andaneri.crm.io.ImportService.Preview;
import ge.andaneri.crm.io.JsonTransferService.JsonFile;
import ge.andaneri.crm.io.JsonTransferService.JsonImportResult;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * Excel, CSV and JSON import. Anyone may import: a salesperson's rows become theirs, a supervisor
 * can hand them to someone or leave them unassigned.
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService service;
    private final JsonTransferService jsonTransfer;
    private final CurrentUser currentUser;
    private final JsonMapper json;

    public ImportController(ImportService service, JsonTransferService jsonTransfer, CurrentUser currentUser, JsonMapper json) {
        this.service = service;
        this.jsonTransfer = jsonTransfer;
        this.currentUser = currentUser;
        this.json = json;
    }

    @PostMapping("/parse")
    public ParsedFile parse(@RequestParam("file") MultipartFile file) {
        currentUser.require();
        return service.read(file);
    }

    @PostMapping("/preview")
    public Preview preview(@Valid @RequestBody ImportRequest request) {
        currentUser.require();
        return service.preview(request);
    }

    @PostMapping("/commit")
    public ImportResult commit(@Valid @RequestBody ImportRequest request) {
        return service.commit(request, currentUser.require());
    }

    /** A file made by the JSON export. Send it once with dryRun=true to see what would happen, then with false. */
    @PostMapping("/json")
    public JsonImportResult importJson(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "true") boolean skipDuplicates) {
        var user = currentUser.require();
        JsonFile parsed;
        try {
            parsed = json.readValue(file.getBytes(), JsonFile.class);
        } catch (IOException | RuntimeException ex) {
            throw ApiException.badRequest("UNREADABLE_FILE");
        }
        return jsonTransfer.importFile(parsed, skipDuplicates, dryRun, user);
    }
}
