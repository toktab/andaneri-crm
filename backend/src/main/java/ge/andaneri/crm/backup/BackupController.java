package ge.andaneri.crm.backup;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.backup.BackupService.BackupInfo;
import ge.andaneri.crm.backup.BackupService.EventInfo;
import ge.andaneri.crm.backup.BackupService.Status;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin backups: make one (and download it), download one someone else made while it is kept, see who
 * downloaded what from where, and turn any backup into an Excel file. Admins and root only: a backup
 * holds every customer's data.
 */
@RestController
@RequestMapping("/api/admin/backups")
public class BackupController {

    public record Overview(Status status, List<BackupInfo> backups, List<EventInfo> log) {
    }

    private final BackupService backups;
    private final BackupConverter converter;
    private final RestoreService restore;
    private final CurrentUser currentUser;

    public BackupController(BackupService backups, BackupConverter converter, RestoreService restore, CurrentUser currentUser) {
        this.backups = backups;
        this.converter = converter;
        this.restore = restore;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Overview overview() {
        currentUser.requireAdmin();
        Instant now = Instant.now();
        return new Overview(backups.status(now), backups.list(now), backups.log(60));
    }

    @GetMapping("/status")
    public Status status() {
        currentUser.requireAdmin();
        return backups.status(Instant.now());
    }

    @PostMapping
    public BackupInfo create(HttpServletRequest request) {
        User user = currentUser.requireAdmin();
        return backups.create(user, ClientIp.of(request), ClientIp.userAgent(request));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable long id, HttpServletRequest request) {
        User user = currentUser.requireAdmin();
        BackupService.Content content = backups.download(id, user, ClientIp.of(request), ClientIp.userAgent(request));
        return file(content.bytes(), content.fileName(), MediaType.parseMediaType("application/zip"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
        User user = currentUser.requireAdmin();
        backups.delete(id, user, ClientIp.of(request), ClientIp.userAgent(request));
        return ResponseEntity.noContent().build();
    }

    /**
     * Puts a backup back into this install: the team, the catalog, the projects, the settings, the notes
     * and every business with its whole history. Refuses a database that already holds businesses unless
     * {@code force} says otherwise, and {@code dryRun} only reports what a restore would bring.
     */
    @PostMapping("/restore")
    public RestoreService.RestoreResult restore(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "false") boolean dryRun) throws IOException {
        User user = currentUser.requireAdmin();
        return restore.restore(file.getBytes(), force, dryRun, user);
    }

    @PostMapping("/convert")
    public ResponseEntity<byte[]> convert(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "ka") String lang) throws IOException {
        currentUser.requireAdmin();
        boolean ka = !"en".equals(lang);
        String base = file.getOriginalFilename() == null ? "andaneri-backup" : file.getOriginalFilename().replaceAll("\\.(zip|json)$", "");
        return file(converter.toExcel(file.getBytes(), ka), base + ".xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private static ResponseEntity<byte[]> file(byte[] bytes, String fileName, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName.replaceAll("[^A-Za-z0-9._-]", "_")
                        + "\"; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"))
                .contentType(type)
                .body(bytes);
    }
}
