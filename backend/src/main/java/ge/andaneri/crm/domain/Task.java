package ge.andaneri.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Something that should happen: a meeting on Thursday at 15:00, a call back, samples to send.
 * The calendar is nothing but these rows, so a meeting is never typed in twice. Completing a
 * task records what happened as an {@link Activity} and links it here.
 */
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;

    private String title;

    @Column(nullable = false)
    private Instant dueAt;

    private Instant endAt;

    private boolean allDay;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_id")
    private User completedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Minutes before {@link #dueAt} to remind the assignee. Null: their own default. 0: no reminder. */
    private Integer remindMinutes;

    /** When the reminder went out; cleared whenever the time or the reminder changes. */
    private Instant remindedAt;

    public Long getId() { return id; }
    public Integer getRemindMinutes() { return remindMinutes; }
    public void setRemindMinutes(Integer remindMinutes) { this.remindMinutes = remindMinutes; }
    public Instant getRemindedAt() { return remindedAt; }
    public void setRemindedAt(Instant remindedAt) { this.remindedAt = remindedAt; }
    public Business getBusiness() { return business; }
    public void setBusiness(Business business) { this.business = business; }
    public Contact getContact() { return contact; }
    public void setContact(Contact contact) { this.contact = contact; }
    public User getAssignedTo() { return assignedTo; }
    public void setAssignedTo(User assignedTo) { this.assignedTo = assignedTo; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public boolean isAllDay() { return allDay; }
    public void setAllDay(boolean allDay) { this.allDay = allDay; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public User getCompletedBy() { return completedBy; }
    public void setCompletedBy(User completedBy) { this.completedBy = completedBy; }
    public Activity getActivity() { return activity; }
    public void setActivity(Activity activity) { this.activity = activity; }
    public Instant getCreatedAt() { return createdAt; }
}
