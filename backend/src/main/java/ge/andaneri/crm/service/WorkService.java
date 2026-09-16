package ge.andaneri.crm.service;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.common.Text;
import ge.andaneri.crm.domain.Activity;
import ge.andaneri.crm.domain.ActivityRepository;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.Comment;
import ge.andaneri.crm.domain.CommentRepository;
import ge.andaneri.crm.domain.Contact;
import ge.andaneri.crm.domain.ContactRepository;
import ge.andaneri.crm.domain.Flavor;
import ge.andaneri.crm.domain.Interest;
import ge.andaneri.crm.domain.InterestRepository;
import ge.andaneri.crm.domain.InterestStatus;
import ge.andaneri.crm.domain.Priority;
import ge.andaneri.crm.domain.Product;
import ge.andaneri.crm.domain.ProductRepository;
import ge.andaneri.crm.domain.Purchase;
import ge.andaneri.crm.domain.PurchaseItem;
import ge.andaneri.crm.domain.PurchaseRepository;
import ge.andaneri.crm.domain.StatusChange;
import ge.andaneri.crm.domain.StatusChangeRepository;
import ge.andaneri.crm.domain.Task;
import ge.andaneri.crm.domain.TaskRepository;
import ge.andaneri.crm.domain.TaskStatus;
import ge.andaneri.crm.domain.TaskType;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.web.BusinessDtos.CategoryUsageRequest;
import ge.andaneri.crm.web.BusinessDtos.UsageRequest;
import ge.andaneri.crm.web.UserDtos.UserRef;
import ge.andaneri.crm.web.WorkDtos.ActivityDto;
import ge.andaneri.crm.web.WorkDtos.ActivityRequest;
import ge.andaneri.crm.web.WorkDtos.CommentDto;
import ge.andaneri.crm.web.WorkDtos.CommentRequest;
import ge.andaneri.crm.web.WorkDtos.ContactRef;
import ge.andaneri.crm.web.WorkDtos.NextTask;
import ge.andaneri.crm.web.WorkDtos.PurchaseDto;
import ge.andaneri.crm.web.WorkDtos.PurchaseItemRequest;
import ge.andaneri.crm.web.WorkDtos.PurchaseRequest;
import ge.andaneri.crm.web.WorkDtos.TaskDto;
import ge.andaneri.crm.web.WorkDtos.TaskRequest;
import ge.andaneri.crm.web.WorkDtos.TimelineItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calls, visits, tasks, comments and purchases: everything that happens with a business over time. */
@Service
public class WorkService {

    private final BusinessService businessService;
    private final ActivityRepository activities;
    private final TaskRepository tasks;
    private final CommentRepository comments;
    private final PurchaseRepository purchases;
    private final StatusChangeRepository statusChanges;
    private final ContactRepository contacts;
    private final ProductRepository products;
    private final InterestRepository interests;
    private final UserRepository users;
    private final ge.andaneri.crm.domain.FlavorRepository flavors;
    private final AuditService audit;

    public WorkService(BusinessService businessService, ActivityRepository activities, TaskRepository tasks,
            CommentRepository comments, PurchaseRepository purchases, StatusChangeRepository statusChanges,
            ContactRepository contacts, ProductRepository products, InterestRepository interests, UserRepository users,
            ge.andaneri.crm.domain.FlavorRepository flavors,
            AuditService audit) {
        this.businessService = businessService;
        this.activities = activities;
        this.tasks = tasks;
        this.comments = comments;
        this.purchases = purchases;
        this.statusChanges = statusChanges;
        this.contacts = contacts;
        this.products = products;
        this.interests = interests;
        this.users = users;
        this.flavors = flavors;
        this.audit = audit;
    }

    // ================================================================== activities

    /**
     * Records a call, visit or meeting and everything it produced, in one transaction: the pipeline
     * stage, what they use and want, the task it completes and the next step. Either all of it is
     * saved or none of it, so a half-recorded visit never happens.
     */
    @Transactional
    public ActivityDto logActivity(Long businessId, ActivityRequest r, User user) {
        Business b = businessService.load(businessId);
        CurrentUser.requireEdit(user, b);

        Activity activity = new Activity();
        activity.setBusiness(b);
        activity.setUser(user);
        activity.setType(r.type());
        activity.setResult(r.result());
        applyResults(activity, r);
        activity.setContact(contactOf(b, r.contactId()));
        activity.setOccurredAt(r.occurredAt() == null ? Instant.now() : r.occurredAt());
        activity.setNotes(Text.blankToNull(r.notes()));
        activity = activities.save(activity);

        if (b.getLastContactAt() == null || activity.getOccurredAt().isAfter(b.getLastContactAt())) {
            b.setLastContactAt(activity.getOccurredAt());
        }
        b.setUpdatedAt(Instant.now());
        businessService.applyStatus(b, r.newStatus(), user, null);

        if (r.usages() != null) {
            for (UsageRequest usage : r.usages()) {
                businessService.addUsagesTo(b, usage, user);
            }
        }
        if (r.interests() != null) {
            businessService.addInterestsTo(b, r.interests(), user);
        }
        if (r.categoryAnswers() != null) {
            for (CategoryUsageRequest answer : r.categoryAnswers()) {
                businessService.upsertCategoryAnswer(b, answer.categoryId(), answer.answer(), answer.notes());
            }
        }
        if (r.completeTaskId() != null) {
            Task task = tasks.findById(r.completeTaskId())
                    .filter(t -> t.getBusiness() == null || t.getBusiness().getId().equals(businessId))
                    .orElseThrow(() -> ApiException.field("completeTaskId", "invalid"));
            if (task.getStatus() == TaskStatus.OPEN) {
                task.setStatus(TaskStatus.DONE);
                task.setCompletedAt(Instant.now());
                task.setCompletedBy(user);
            }
            task.setActivity(activity);
        }
        if (r.nextTask() != null) {
            createNextTask(b, r.nextTask(), user);
        }
        audit.record(businessId, "Activity", activity.getId(), "CREATED", r.type() + " " + r.result(), user);
        return ActivityDto.of(activity);
    }

    /** Changing what was written down about a call or visit: what happened, when, with whom, what they said. */
    @Transactional
    public ActivityDto updateActivity(Long businessId, Long activityId, ActivityRequest r, User user) {
        Business b = businessService.load(businessId);
        CurrentUser.requireEdit(user, b);
        Activity activity = ownActivity(businessId, activityId);
        activity.setType(r.type());
        activity.setResult(r.result());
        applyResults(activity, r);
        activity.setContact(contactOf(b, r.contactId()));
        if (r.occurredAt() != null) {
            activity.setOccurredAt(r.occurredAt());
        }
        activity.setNotes(Text.blankToNull(r.notes()));
        // Written down later, so it is no longer only a guess from the spreadsheet.
        activity.setImported(false);
        businessService.applyStatus(b, r.newStatus(), user, null);
        b.setUpdatedAt(Instant.now());
        audit.record(businessId, "Activity", activityId, "UPDATED", r.type() + " " + r.result(), user);
        return ActivityDto.of(activity);
    }

    /** Removes a call or visit written down by mistake. Any task completed by it keeps standing on its own. */
    @Transactional
    public void deleteActivity(Long businessId, Long activityId, User user) {
        Business b = businessService.load(businessId);
        CurrentUser.requireEdit(user, b);
        Activity activity = ownActivity(businessId, activityId);
        for (Task task : tasks.findForBusiness(businessId)) {
            if (task.getActivity() != null && task.getActivity().getId().equals(activityId)) {
                task.setActivity(null);
            }
        }
        comments.findForActivity(activityId).forEach(comment -> comment.setActivity(null));
        activities.delete(activity);
        audit.record(businessId, "Activity", activityId, "DELETED", activity.getType() + " " + activity.getResult(), user);
    }

    /** One entry, so it can be opened again and corrected. */
    @Transactional(readOnly = true)
    public ActivityDto activity(Long businessId, Long activityId, User user) {
        businessService.load(businessId);
        return ActivityDto.of(ownActivity(businessId, activityId));
    }

    private Activity ownActivity(Long businessId, Long activityId) {
        return activities.findById(activityId)
                .filter(a -> a.getBusiness().getId().equals(businessId))
                .orElseThrow(ApiException::notFound);
    }

    private static void applyResults(Activity activity, ActivityRequest r) {
        activity.getResults().clear();
        if (r.results() != null) {
            r.results().stream().filter(extra -> extra != null && extra != r.result()).forEach(activity.getResults()::add);
        }
        activity.setResultNote(Text.blankToNull(r.resultNote()));
    }

    /**
     * Cancels the open tasks that came out of the old spreadsheet's "next step" column. They are guesses,
     * so one press clears them from the call list and the calendar; supervisors clear the whole team's.
     */
    @Transactional
    public int cancelImportedTasks(User user) {
        List<Task> guesses = tasks.findImportedWithStatus(TaskStatus.OPEN, user.isSupervisor() ? null : user.getId());
        Instant now = Instant.now();
        for (Task task : guesses) {
            task.setStatus(TaskStatus.CANCELLED);
            task.setCompletedAt(now);
            task.setCompletedBy(user);
        }
        if (!guesses.isEmpty()) {
            audit.record(null, "Task", null, "CANCELLED", "Excel: " + guesses.size(), user);
        }
        return guesses.size();
    }

    private void setFlavors(Task task, List<Long> flavorIds) {
        task.getFlavors().clear();
        if (flavorIds != null) {
            flavorIds.stream().filter(java.util.Objects::nonNull).distinct()
                    .forEach(id -> flavors.findById(id).ifPresent(task.getFlavors()::add));
        }
    }

    private void createNextTask(Business b, NextTask next, User user) {
        Task task = new Task();
        task.setBusiness(b);
        task.setContact(contactOf(b, next.contactId()));
        task.setType(next.type());
        task.setDueAt(next.dueAt());
        task.setEndAt(next.endAt());
        task.setAllDay(Boolean.TRUE.equals(next.allDay()));
        task.setTitle(Text.blankToNull(next.title()));
        setFlavors(task, next.flavorIds());
        task.setLocation(Text.blankToNull(next.location()));
        task.setNotes(Text.blankToNull(next.notes()));
        task.setPriority(next.priority() == null ? Priority.NORMAL : next.priority());
        task.setAssignedTo(assignee(next.assignedToId(), b.getAssignedTo() != null ? b.getAssignedTo() : user, user));
        task.setCreatedBy(user);
        tasks.save(task);
    }

    /** The team's calls and visits in a period, newest first: what a supervisor reviews. */
    @Transactional(readOnly = true)
    public List<ActivityDto> activitiesBetween(Instant from, Instant to, Long userId) {
        return activities.findInRange(from, to).stream()
                .filter(a -> userId == null || a.getUser().getId().equals(userId))
                .sorted(Comparator.comparing(Activity::getOccurredAt).reversed())
                .map(ActivityDto::of)
                .toList();
    }

    // ================================================================== tasks

    @Transactional(readOnly = true)
    public List<TaskDto> tasksBetween(Instant from, Instant to, Long userId, Collection<TaskStatus> statuses) {
        Collection<TaskStatus> wanted = statuses == null || statuses.isEmpty() ? EnumSet.of(TaskStatus.OPEN, TaskStatus.DONE) : statuses;
        return tasks.findInRange(from, to, wanted, userId).stream().map(TaskDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDto> overdue(Long userId) {
        return tasks.findWithStatusBefore(TaskStatus.OPEN, Instant.now(), userId).stream().map(TaskDto::of).toList();
    }

    @Transactional
    public TaskDto createTask(TaskRequest r, User user) {
        Task task = new Task();
        task.setCreatedBy(user);
        applyTask(task, r, user);
        task = tasks.save(task);
        audit.record(task.getBusiness() == null ? null : task.getBusiness().getId(), "Task", task.getId(), "CREATED",
                task.getType() + " " + task.getDueAt(), user);
        return TaskDto.of(task);
    }

    @Transactional
    public TaskDto updateTask(Long id, TaskRequest r, User user) {
        Task task = editableTask(id, user);
        Instant before = task.getDueAt();
        applyTask(task, r, user);
        audit.record(task.getBusiness() == null ? null : task.getBusiness().getId(), "Task", id, "UPDATED",
                before.equals(task.getDueAt()) ? null : "moved " + before + " -> " + task.getDueAt(), user);
        return TaskDto.of(task);
    }

    /** Completes a task. With an activity, the call or meeting is recorded in the same step and linked to it. */
    @Transactional
    public TaskDto completeTask(Long id, ActivityRequest activity, User user) {
        Task task = editableTask(id, user);
        if (activity != null && task.getBusiness() != null) {
            ActivityRequest linked = new ActivityRequest(activity.type(), activity.result(), activity.results(), activity.resultNote(),
                    activity.contactId() != null ? activity.contactId() : task.getContact() == null ? null : task.getContact().getId(),
                    activity.occurredAt(), activity.notes(), activity.newStatus(), activity.nextTask(), id,
                    activity.usages(), activity.interests(), activity.categoryAnswers());
            logActivity(task.getBusiness().getId(), linked, user);
        } else {
            task.setStatus(TaskStatus.DONE);
            task.setCompletedAt(Instant.now());
            task.setCompletedBy(user);
            audit.record(task.getBusiness() == null ? null : task.getBusiness().getId(), "Task", id, "DONE", null, user);
        }
        return TaskDto.of(task);
    }

    @Transactional
    public TaskDto cancelTask(Long id, User user) {
        Task task = editableTask(id, user);
        task.setStatus(TaskStatus.CANCELLED);
        task.setCompletedAt(Instant.now());
        task.setCompletedBy(user);
        audit.record(task.getBusiness() == null ? null : task.getBusiness().getId(), "Task", id, "CANCELLED", null, user);
        return TaskDto.of(task);
    }

    @Transactional
    public TaskDto reopenTask(Long id, User user) {
        Task task = editableTask(id, user);
        task.setStatus(TaskStatus.OPEN);
        task.setCompletedAt(null);
        task.setCompletedBy(null);
        task.setRemindedAt(null);
        audit.record(task.getBusiness() == null ? null : task.getBusiness().getId(), "Task", id, "REOPENED", null, user);
        return TaskDto.of(task);
    }

    private Task editableTask(Long id, User user) {
        Task task = tasks.findById(id).orElseThrow(ApiException::notFound);
        boolean mine = task.getAssignedTo().getId().equals(user.getId()) || task.getCreatedBy().getId().equals(user.getId());
        if (!mine && !user.isSupervisor()) {
            throw ApiException.forbidden();
        }
        return task;
    }

    private void applyTask(Task task, TaskRequest r, User user) {
        Business b = null;
        if (r.businessId() != null) {
            b = businessService.load(r.businessId());
            if (task.getId() == null) {
                CurrentUser.requireEdit(user, b);
            }
        }
        if (r.endAt() != null && r.endAt().isBefore(r.dueAt())) {
            throw ApiException.field("endAt", "range");
        }
        // A moved task, or a changed reminder, is reminded again at its new time.
        if (!r.dueAt().equals(task.getDueAt()) || !java.util.Objects.equals(r.remindMinutes(), task.getRemindMinutes())
                || Boolean.TRUE.equals(r.allDay()) != task.isAllDay()) {
            task.setRemindedAt(null);
        }
        task.setRemindMinutes(r.remindMinutes());
        task.setBusiness(b);
        task.setContact(b == null ? null : contactOf(b, r.contactId()));
        task.setType(r.type());
        task.setTitle(Text.blankToNull(r.title()));
        task.setDueAt(r.dueAt());
        task.setEndAt(r.endAt());
        task.setAllDay(Boolean.TRUE.equals(r.allDay()));
        task.setLocation(Text.blankToNull(r.location()));
        task.setPriority(r.priority() == null ? Priority.NORMAL : r.priority());
        task.setNotes(Text.blankToNull(r.notes()));
        setFlavors(task, r.flavorIds());
        User fallback = task.getAssignedTo() != null ? task.getAssignedTo() : user;
        task.setAssignedTo(assignee(r.assignedToId(), fallback, user));
    }

    /** Salespeople plan their own work; supervisors can put a task on anyone's calendar. */
    private User assignee(Long requestedId, User fallback, User user) {
        if (requestedId == null || requestedId.equals(fallback.getId())) {
            return fallback;
        }
        if (!user.isSupervisor() && !requestedId.equals(user.getId())) {
            throw ApiException.forbidden();
        }
        return users.findById(requestedId).filter(User::isActive).orElseThrow(() -> ApiException.field("assignedToId", "invalid"));
    }

    // ================================================================== comments

    /** Anyone on the team can comment on any business: that is how a supervisor says "follow up with them". */
    @Transactional
    public CommentDto addComment(Long businessId, CommentRequest r, User user) {
        Business b = businessService.load(businessId);
        Comment comment = new Comment();
        comment.setBusiness(b);
        comment.setAuthor(user);
        comment.setBody(r.body().trim());
        if (r.activityId() != null) {
            comment.setActivity(activities.findById(r.activityId())
                    .filter(a -> a.getBusiness().getId().equals(businessId))
                    .orElseThrow(() -> ApiException.field("activityId", "invalid")));
        }
        comment = comments.save(comment);
        audit.record(businessId, "Comment", comment.getId(), "CREATED", comment.getBody(), user);
        return CommentDto.of(comment);
    }

    // ================================================================== purchases

    @Transactional(readOnly = true)
    public List<PurchaseDto> purchasesOf(Long businessId) {
        businessService.load(businessId);
        return purchases.findForBusiness(businessId).stream()
                .sorted(Comparator.comparing(Purchase::getPurchaseDate).thenComparing(Purchase::getId).reversed())
                .map(p -> PurchaseDto.of(p, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PurchaseDto> recentPurchases(Long userId, int limit) {
        return purchases.findRecent(userId, org.springframework.data.domain.PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
                .stream().map(p -> PurchaseDto.of(p, false)).toList();
    }

    /**
     * Records an order, and with it: the business becomes a customer (a repeat customer from the
     * second order), the products join what they use, matching interests become "purchased", and
     * any open "check if they need to reorder" task is done.
     */
    @Transactional
    public PurchaseDto addPurchase(Long businessId, PurchaseRequest r, User user) {
        Business b = businessService.load(businessId);
        CurrentUser.requireEdit(user, b);

        Purchase purchase = new Purchase();
        purchase.setBusiness(b);
        purchase.setUser(user);
        purchase.setPurchaseDate(r.purchaseDate());
        purchase.setNotes(Text.blankToNull(r.notes()));
        BigDecimal total = BigDecimal.ZERO;
        List<Product> bought = new ArrayList<>();
        for (PurchaseItemRequest line : r.items()) {
            Product product = line.productId() == null ? null
                    : products.findById(line.productId()).orElseThrow(() -> ApiException.field("items", "invalid"));
            String description = Text.blankToNull(line.description());
            if (description == null) {
                if (product == null) {
                    throw ApiException.field("items", "required");
                }
                description = product.getNameKa();
            }
            PurchaseItem item = new PurchaseItem();
            item.setPurchase(purchase);
            item.setProduct(product);
            item.setDescription(description);
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setLineTotal(line.quantity().multiply(line.unitPrice()).setScale(2, RoundingMode.HALF_UP));
            total = total.add(item.getLineTotal());
            purchase.getItems().add(item);
            if (product != null) {
                bought.add(product);
            }
        }
        purchase.setTotal(total);
        purchase = purchases.save(purchase);

        b.setPurchaseCount(b.getPurchaseCount() + 1);
        if (b.getLastPurchaseDate() == null || r.purchaseDate().isAfter(b.getLastPurchaseDate())) {
            b.setLastPurchaseDate(r.purchaseDate());
        }
        b.setUpdatedAt(Instant.now());
        BusinessStatus target = b.getPurchaseCount() >= 2 ? BusinessStatus.REPEAT_CUSTOMER : BusinessStatus.CUSTOMER;
        if (b.getStatus() != BusinessStatus.REPEAT_CUSTOMER) {
            businessService.applyStatus(b, target, user, null);
        }

        for (Product product : bought) {
            List<Long> flavorIds = product.getFlavors().stream().map(Flavor::getId).toList();
            businessService.addUsagesTo(b, new UsageRequest(product.getCategory().getId(), product.getBrand().getId(),
                    flavorIds, null, null, null, null), user);
        }
        markInterestsPurchased(b, bought);
        for (Task task : tasks.findForBusiness(businessId)) {
            if (task.getStatus() == TaskStatus.OPEN && task.getType() == TaskType.CHECK_REORDER) {
                task.setStatus(TaskStatus.DONE);
                task.setCompletedAt(Instant.now());
                task.setCompletedBy(user);
            }
        }
        audit.record(businessId, "Purchase", purchase.getId(), "CREATED", total + " GEL", user);
        return PurchaseDto.of(purchase, true);
    }

    private void markInterestsPurchased(Business b, List<Product> bought) {
        if (bought.isEmpty()) {
            return;
        }
        for (Interest interest : interests.findForBusiness(b.getId())) {
            boolean sameProduct = interest.getProduct() != null
                    && bought.stream().anyMatch(p -> p.getId().equals(interest.getProduct().getId()));
            boolean sameFlavor = interest.getProduct() == null && interest.getFlavor() != null
                    && bought.stream().anyMatch(p -> p.getFlavors().stream().anyMatch(f -> f.getId().equals(interest.getFlavor().getId())));
            if ((sameProduct || sameFlavor) && interest.getStatus() != InterestStatus.PURCHASED) {
                interest.setStatus(InterestStatus.PURCHASED);
                interest.setUpdatedAt(Instant.now());
            }
        }
    }

    /** For mistakes only, so supervisors only. Keeps the business's order count and last order date right. */
    @Transactional
    public void deletePurchase(Long businessId, Long purchaseId, User user) {
        if (!user.isSupervisor()) {
            throw ApiException.forbidden();
        }
        Purchase purchase = purchases.findById(purchaseId)
                .filter(p -> p.getBusiness().getId().equals(businessId))
                .orElseThrow(ApiException::notFound);
        Business b = purchase.getBusiness();
        audit.record(businessId, "Purchase", purchaseId, "DELETED", purchase.getPurchaseDate() + " " + purchase.getTotal() + " GEL", user);
        purchases.delete(purchase);
        purchases.flush();
        List<Purchase> remaining = purchases.findForBusiness(businessId);
        b.setPurchaseCount(remaining.size());
        b.setLastPurchaseDate(remaining.stream().map(Purchase::getPurchaseDate).max(LocalDate::compareTo).orElse(null));
        b.setUpdatedAt(Instant.now());
    }

    // ================================================================== timeline

    /** The whole story of one business, newest first. Comments on an activity are nested under it. */
    @Transactional(readOnly = true)
    public List<TimelineItem> timeline(Long businessId) {
        businessService.load(businessId);
        List<TimelineItem> items = new ArrayList<>();

        Map<Long, List<CommentDto>> commentsByActivity = new HashMap<>();
        for (Comment comment : comments.findForBusiness(businessId)) {
            if (comment.getActivity() != null) {
                commentsByActivity.computeIfAbsent(comment.getActivity().getId(), key -> new ArrayList<>()).add(CommentDto.of(comment));
            } else {
                items.add(new TimelineItem("COMMENT", comment.getId(), comment.getCreatedAt(), UserRef.of(comment.getAuthor()),
                        null, null, null, comment.getBody(), null, null, null, null, List.of()));
            }
        }
        for (Activity a : activities.findForBusiness(businessId)) {
            List<CommentDto> nested = commentsByActivity.getOrDefault(a.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(CommentDto::createdAt)).toList();
            items.add(new TimelineItem("ACTIVITY", a.getId(), a.getOccurredAt(), UserRef.of(a.getUser()), a.getType().name(),
                    a.getResult().name(), ActivityDto.of(a).results().stream().map(Enum::name).toList(), a.getResultNote(),
                    null, a.getNotes(), null, null, null, ContactRef.of(a.getContact()), nested, a.isImported()));
        }
        for (StatusChange s : statusChanges.findForBusiness(businessId)) {
            items.add(new TimelineItem(s.getFromStatus() == null ? "CREATED" : "STATUS", s.getId(), s.getChangedAt(),
                    UserRef.of(s.getUser()), null, null, null, s.getNote(), s.getFromStatus(), s.getToStatus(), null, null, List.of()));
        }
        for (Purchase p : purchases.findForBusiness(businessId)) {
            String lines = p.getItems().stream()
                    .map(i -> i.getDescription() + " × " + i.getQuantity().stripTrailingZeros().toPlainString())
                    .collect(Collectors.joining(", "));
            items.add(new TimelineItem("PURCHASE", p.getId(), p.getPurchaseDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
                    .plusSeconds(12 * 3600), UserRef.of(p.getUser()), null, null, lines, p.getNotes(), null, null, p.getTotal(),
                    null, List.of()));
        }
        for (Task t : tasks.findForBusiness(businessId)) {
            // A task completed through an activity is already on the timeline as that activity.
            if (t.getCompletedAt() == null || t.getActivity() != null || t.getStatus() == TaskStatus.OPEN) {
                continue;
            }
            items.add(new TimelineItem(t.getStatus() == TaskStatus.DONE ? "TASK_DONE" : "TASK_CANCELLED", t.getId(),
                    t.getCompletedAt(), UserRef.of(t.getCompletedBy()), t.getType().name(), null, t.getTitle(), t.getNotes(),
                    null, null, null, ContactRef.of(t.getContact()), List.of()));
        }
        items.sort(Comparator.comparing(TimelineItem::at).thenComparing(TimelineItem::refId).reversed());
        return items;
    }

    // ================================================================== helpers

    private Contact contactOf(Business b, Long contactId) {
        if (contactId == null) {
            return null;
        }
        return contacts.findById(contactId)
                .filter(c -> c.getBusiness().getId().equals(b.getId()))
                .orElseThrow(() -> ApiException.field("contactId", "invalid"));
    }
}
