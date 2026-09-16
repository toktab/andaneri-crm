package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.domain.TaskStatus;
import ge.andaneri.crm.service.WorkService;
import ge.andaneri.crm.web.WorkDtos.ActivityDto;
import ge.andaneri.crm.web.WorkDtos.ActivityRequest;
import ge.andaneri.crm.web.WorkDtos.CommentDto;
import ge.andaneri.crm.web.WorkDtos.CommentRequest;
import ge.andaneri.crm.web.WorkDtos.CompleteRequest;
import ge.andaneri.crm.web.WorkDtos.PurchaseDto;
import ge.andaneri.crm.web.WorkDtos.PurchaseRequest;
import ge.andaneri.crm.web.WorkDtos.TaskDto;
import ge.andaneri.crm.web.WorkDtos.TaskRequest;
import ge.andaneri.crm.web.WorkDtos.TimelineItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkController {

    public record CancelledTasks(int cancelled) {
    }

    private final WorkService service;
    private final CurrentUser currentUser;

    public WorkController(WorkService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------ per business

    @PostMapping("/businesses/{id}/activities")
    public ActivityDto logActivity(@PathVariable Long id, @Valid @RequestBody ActivityRequest request) {
        return service.logActivity(id, request, currentUser.require());
    }

    /** One entry, to open it for changing. */
    @GetMapping("/businesses/{id}/activities/{activityId}")
    public ActivityDto activity(@PathVariable Long id, @PathVariable Long activityId) {
        return service.activity(id, activityId, currentUser.require());
    }

    @PutMapping("/businesses/{id}/activities/{activityId}")
    public ActivityDto updateActivity(@PathVariable Long id, @PathVariable Long activityId, @Valid @RequestBody ActivityRequest request) {
        return service.updateActivity(id, activityId, request, currentUser.require());
    }

    @DeleteMapping("/businesses/{id}/activities/{activityId}")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id, @PathVariable Long activityId) {
        service.deleteActivity(id, activityId, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    /** Clears the open tasks that came out of the spreadsheet's "next step" column. */
    @PostMapping("/tasks/imported/cancel")
    public CancelledTasks cancelImported() {
        return new CancelledTasks(service.cancelImportedTasks(currentUser.require()));
    }

    @GetMapping("/businesses/{id}/timeline")
    public List<TimelineItem> timeline(@PathVariable Long id) {
        currentUser.require();
        return service.timeline(id);
    }

    @PostMapping("/businesses/{id}/comments")
    public CommentDto comment(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        return service.addComment(id, request, currentUser.require());
    }

    @GetMapping("/businesses/{id}/purchases")
    public List<PurchaseDto> purchases(@PathVariable Long id) {
        currentUser.require();
        return service.purchasesOf(id);
    }

    @PostMapping("/businesses/{id}/purchases")
    public PurchaseDto addPurchase(@PathVariable Long id, @Valid @RequestBody PurchaseRequest request) {
        return service.addPurchase(id, request, currentUser.require());
    }

    @DeleteMapping("/businesses/{id}/purchases/{purchaseId}")
    public ResponseEntity<Void> deletePurchase(@PathVariable Long id, @PathVariable Long purchaseId) {
        service.deletePurchase(id, purchaseId, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ team-wide

    @GetMapping("/activities")
    public List<ActivityDto> activities(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Long userId) {
        currentUser.require();
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(7)) : from;
        return service.activitiesBetween(start, end, userId);
    }

    @GetMapping("/purchases/recent")
    public List<PurchaseDto> recentPurchases(@RequestParam(required = false) Long userId, @RequestParam(defaultValue = "20") int limit) {
        currentUser.require();
        return service.recentPurchases(userId, limit);
    }

    // ------------------------------------------------------------ tasks and the calendar

    /** The calendar: every task due in [from, to). */
    @GetMapping("/tasks")
    public List<TaskDto> tasks(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) List<TaskStatus> status) {
        currentUser.require();
        return service.tasksBetween(from, to, userId, status);
    }

    @GetMapping("/tasks/overdue")
    public List<TaskDto> overdue(@RequestParam(required = false) Long userId) {
        currentUser.require();
        return service.overdue(userId);
    }

    @PostMapping("/tasks")
    public TaskDto createTask(@Valid @RequestBody TaskRequest request) {
        return service.createTask(request, currentUser.require());
    }

    @PutMapping("/tasks/{id}")
    public TaskDto updateTask(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return service.updateTask(id, request, currentUser.require());
    }

    /** Just the time, for a task pushed to another day from a list. */
    @PostMapping("/tasks/{id}/move")
    public TaskDto moveTask(@PathVariable Long id, @Valid @RequestBody MoveRequest request) {
        return service.moveTask(id, request.dueAt(), currentUser.require());
    }

    public record MoveRequest(@NotNull Instant dueAt) {}

    @PostMapping("/tasks/{id}/complete")
    public TaskDto complete(@PathVariable Long id, @Valid @RequestBody(required = false) CompleteRequest request) {
        return service.completeTask(id, request == null ? null : request.activity(), currentUser.require());
    }

    @PostMapping("/tasks/{id}/cancel")
    public TaskDto cancel(@PathVariable Long id) {
        return service.cancelTask(id, currentUser.require());
    }

    @PostMapping("/tasks/{id}/reopen")
    public TaskDto reopen(@PathVariable Long id) {
        return service.reopenTask(id, currentUser.require());
    }
}
