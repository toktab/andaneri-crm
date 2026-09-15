package ge.andaneri.crm.web;

import ge.andaneri.crm.auth.CurrentUser;
import ge.andaneri.crm.common.ApiException;
import ge.andaneri.crm.common.Text;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.Comment;
import ge.andaneri.crm.domain.CommentRepository;
import ge.andaneri.crm.domain.QuickNote;
import ge.andaneri.crm.domain.QuickNoteRepository;
import ge.andaneri.crm.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
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

/** Each person's own quick notes. Nobody else sees them until one is saved onto a business. */
@RestController
@RequestMapping("/api/notes")
public class QuickNoteController {

    public record NoteRequest(@NotBlank String body, Long businessId, Instant remindAt, Boolean done) {
    }

    public record NoteDto(Long id, String body, Long businessId, String businessName, Instant remindAt, boolean done,
            Instant createdAt) {
        static NoteDto of(QuickNote n) {
            return new NoteDto(n.getId(), n.getBody(), n.getBusiness() == null ? null : n.getBusiness().getId(),
                    n.getBusiness() == null ? null : n.getBusiness().getName(), n.getRemindAt(), n.isDone(), n.getCreatedAt());
        }
    }

    private final QuickNoteRepository notes;
    private final BusinessRepository businesses;
    private final CommentRepository comments;
    private final CurrentUser currentUser;

    public QuickNoteController(QuickNoteRepository notes, BusinessRepository businesses, CommentRepository comments,
            CurrentUser currentUser) {
        this.notes = notes;
        this.businesses = businesses;
        this.comments = comments;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<NoteDto> mine() {
        return notes.findForUser(currentUser.require().getId()).stream().map(NoteDto::of).toList();
    }

    @PostMapping
    @Transactional
    public NoteDto create(@Valid @RequestBody NoteRequest request) {
        QuickNote note = new QuickNote();
        note.setUser(currentUser.require());
        apply(note, request);
        return NoteDto.of(notes.save(note));
    }

    @PutMapping("/{id}")
    @Transactional
    public NoteDto update(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        QuickNote note = own(id);
        apply(note, request);
        return NoteDto.of(note);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        notes.delete(own(id));
        return ResponseEntity.noContent().build();
    }

    /** Copies the note onto its business's history as a comment, where the team can see it, and ticks it off. */
    @PostMapping("/{id}/to-comment")
    @Transactional
    public NoteDto toComment(@PathVariable Long id) {
        QuickNote note = own(id);
        if (note.getBusiness() == null) {
            throw ApiException.field("businessId", "required");
        }
        Comment comment = new Comment();
        comment.setBusiness(note.getBusiness());
        comment.setAuthor(note.getUser());
        comment.setBody(note.getBody());
        comments.save(comment);
        note.setDone(true);
        note.setUpdatedAt(Instant.now());
        return NoteDto.of(note);
    }

    private QuickNote own(Long id) {
        User user = currentUser.require();
        return notes.findById(id).filter(n -> n.getUser().getId().equals(user.getId())).orElseThrow(ApiException::notFound);
    }

    private void apply(QuickNote note, NoteRequest request) {
        note.setBody(request.body().trim());
        note.setBusiness(request.businessId() == null ? null
                : businesses.findById(request.businessId()).orElseThrow(() -> ApiException.field("businessId", "invalid")));
        if (!java.util.Objects.equals(note.getRemindAt(), request.remindAt())) {
            note.setRemindedAt(null);
        }
        note.setRemindAt(request.remindAt());
        if (request.done() != null) {
            note.setDone(request.done());
        }
        note.setUpdatedAt(Instant.now());
        if (Text.blankToNull(note.getBody()) == null) {
            throw ApiException.field("body", "required");
        }
    }
}
