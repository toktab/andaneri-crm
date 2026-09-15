package ge.andaneri.crm.web;

import ge.andaneri.crm.domain.Activity;
import ge.andaneri.crm.domain.ActivityResult;
import ge.andaneri.crm.domain.ActivityType;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.Comment;
import ge.andaneri.crm.domain.Contact;
import ge.andaneri.crm.domain.Priority;
import ge.andaneri.crm.domain.Purchase;
import ge.andaneri.crm.domain.PurchaseItem;
import ge.andaneri.crm.domain.Task;
import ge.andaneri.crm.domain.TaskStatus;
import ge.andaneri.crm.domain.TaskType;
import ge.andaneri.crm.web.BusinessDtos.CategoryUsageRequest;
import ge.andaneri.crm.web.BusinessDtos.InterestRequest;
import ge.andaneri.crm.web.BusinessDtos.UsageRequest;
import ge.andaneri.crm.web.UserDtos.UserRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WorkDtos {

    private WorkDtos() {
    }

    public record ContactRef(Long id, String name, String roleTitle, String phone) {
        public static ContactRef of(Contact c) {
            return c == null ? null : new ContactRef(c.getId(), c.getName(), c.getRoleTitle(), c.getPhone());
        }
    }

    // ------------------------------------------------------------ tasks

    /** A task with enough of its business attached to call it or drive there straight from the dashboard. */
    public record TaskDto(Long id, Long businessId, String businessName, String businessPhone, String businessAddress,
            String businessMapsUrl, ContactRef contact, UserRef assignedTo, TaskType type, String title, Instant dueAt,
            Instant endAt, boolean allDay, String location, Priority priority, TaskStatus status, String notes,
            Instant completedAt, UserRef completedBy, Long activityId, Integer remindMinutes) {
        public static TaskDto of(Task t) {
            var b = t.getBusiness();
            return new TaskDto(t.getId(), b == null ? null : b.getId(), b == null ? null : b.getName(),
                    b == null ? null : b.getPhone(), b == null ? null : b.getAddress(), b == null ? null : b.getMapsUrl(),
                    ContactRef.of(t.getContact()), UserRef.of(t.getAssignedTo()), t.getType(), t.getTitle(), t.getDueAt(),
                    t.getEndAt(), t.isAllDay(), t.getLocation(), t.getPriority(), t.getStatus(), t.getNotes(),
                    t.getCompletedAt(), UserRef.of(t.getCompletedBy()), t.getActivity() == null ? null : t.getActivity().getId(),
                    t.getRemindMinutes());
        }
    }

    public record TaskRequest(
            Long businessId,
            Long contactId,
            Long assignedToId,
            @NotNull TaskType type,
            @Size(max = 200) String title,
            @NotNull Instant dueAt,
            Instant endAt,
            Boolean allDay,
            @Size(max = 200) String location,
            Priority priority,
            String notes,
            /** Null: the assignee's default. 0: no reminder. Otherwise minutes before, up to a week. */
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(10080) Integer remindMinutes) {
    }

    /** Completing a task can record what happened in the same step; without an activity it is just ticked off. */
    public record CompleteRequest(@Valid ActivityRequest activity) {
    }

    // ------------------------------------------------------------ activities

    /**
     * Everything one call or visit produces, saved in one go: what happened, the new pipeline stage,
     * what they use and want, and the next step. This is the "record it once" form.
     */
    public record ActivityRequest(
            @NotNull ActivityType type,
            @NotNull ActivityResult result,
            Long contactId,
            Instant occurredAt,
            String notes,
            BusinessStatus newStatus,
            @Valid NextTask nextTask,
            Long completeTaskId,
            @Valid List<UsageRequest> usages,
            @Valid InterestRequest interests,
            @Valid List<CategoryUsageRequest> categoryAnswers) {
    }

    // Booleans in request records are nullable: Jackson 3 refuses a missing primitive, and the forms often leave these out.
    public record NextTask(
            @NotNull TaskType type,
            @NotNull Instant dueAt,
            Instant endAt,
            Boolean allDay,
            @Size(max = 200) String title,
            @Size(max = 200) String location,
            String notes,
            Long assignedToId,
            Long contactId,
            Priority priority) {
    }

    public record ActivityDto(Long id, Long businessId, String businessName, ActivityType type, ActivityResult result,
            Instant occurredAt, String notes, UserRef user, ContactRef contact, boolean imported) {
        public static ActivityDto of(Activity a) {
            return new ActivityDto(a.getId(), a.getBusiness().getId(), a.getBusiness().getName(), a.getType(), a.getResult(),
                    a.getOccurredAt(), a.getNotes(), UserRef.of(a.getUser()), ContactRef.of(a.getContact()), a.isImported());
        }
    }

    // ------------------------------------------------------------ comments

    public record CommentRequest(@NotBlank String body, Long activityId) {
    }

    public record CommentDto(Long id, String body, UserRef author, Instant createdAt, Long activityId) {
        public static CommentDto of(Comment c) {
            return new CommentDto(c.getId(), c.getBody(), UserRef.of(c.getAuthor()), c.getCreatedAt(),
                    c.getActivity() == null ? null : c.getActivity().getId());
        }
    }

    // ------------------------------------------------------------ purchases

    public record PurchaseItemRequest(
            Long productId,
            @Size(max = 200) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice) {
    }

    public record PurchaseRequest(
            @NotNull LocalDate purchaseDate,
            String notes,
            @NotEmpty @Valid List<PurchaseItemRequest> items) {
    }

    public record PurchaseItemDto(Long id, Long productId, String description, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal lineTotal) {
        public static PurchaseItemDto of(PurchaseItem i) {
            return new PurchaseItemDto(i.getId(), i.getProduct() == null ? null : i.getProduct().getId(), i.getDescription(),
                    i.getQuantity(), i.getUnitPrice(), i.getLineTotal());
        }
    }

    public record PurchaseDto(Long id, Long businessId, String businessName, LocalDate purchaseDate, BigDecimal total,
            String notes, UserRef user, List<PurchaseItemDto> items, Instant createdAt) {
        public static PurchaseDto of(Purchase p, boolean withItems) {
            return new PurchaseDto(p.getId(), p.getBusiness().getId(), p.getBusiness().getName(), p.getPurchaseDate(),
                    p.getTotal(), p.getNotes(), UserRef.of(p.getUser()),
                    withItems ? p.getItems().stream().map(PurchaseItemDto::of).toList() : List.of(), p.getCreatedAt());
        }
    }

    // ------------------------------------------------------------ timeline

    /**
     * One line of a business's history. {@code kind} is ACTIVITY, COMMENT, STATUS, PURCHASE, TASK_DONE,
     * TASK_CANCELLED or CREATED; the fields that do not apply to a kind are null.
     */
    public record TimelineItem(String kind, Long refId, Instant at, UserRef user, String type, String result,
            String title, String notes, BusinessStatus fromStatus, BusinessStatus toStatus, BigDecimal amount,
            ContactRef contact, List<CommentDto> comments, boolean imported) {

        public TimelineItem(String kind, Long refId, Instant at, UserRef user, String type, String result,
                String title, String notes, BusinessStatus fromStatus, BusinessStatus toStatus, BigDecimal amount,
                ContactRef contact, List<CommentDto> comments) {
            this(kind, refId, at, user, type, result, title, notes, fromStatus, toStatus, amount, contact, comments, false);
        }
    }
}
