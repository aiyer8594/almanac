package io.github.aiyer8594.almanac.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A task row in the {@code tasks} table. The schema is owned by Flyway; this mapping
 * must match it, and Hibernate validates that at startup.
 *
 * <p>State changes go through intent methods ({@link #start()}, {@link #markDone()},
 * {@link #reopen()}) rather than setters, so the rules in
 * {@link TaskStatus#canTransitionTo} are enforced in one place. The id, timestamps, and
 * version have no setters: the database assigns the id and Hibernate manages the rest.
 *
 * <p>{@code version} enables optimistic locking: a concurrent update to the same row
 * fails at flush instead of silently overwriting the other writer's changes.
 */
@Entity
@Table(name = "tasks")
public class Task {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TaskStatus status;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	/** Required by JPA; not for application use. */
	protected Task() {
	}

	public Task(String title, String description, LocalDate dueDate) {
		this.title = Objects.requireNonNull(title, "title");
		this.description = description;
		this.dueDate = dueDate;
		this.status = TaskStatus.TODO;
	}

	public void changeDetails(String title, String description, LocalDate dueDate) {
		this.title = Objects.requireNonNull(title, "title");
		this.description = description;
		this.dueDate = dueDate;
	}

	public void start() {
		transitionTo(TaskStatus.IN_PROGRESS);
	}

	public void markDone() {
		transitionTo(TaskStatus.DONE);
	}

	public void reopen() {
		transitionTo(TaskStatus.TODO);
	}

	/**
	 * Moves this task to {@code target} if {@link TaskStatus#canTransitionTo} allows it.
	 * For example, a {@code TODO} task can be started ({@code TODO -> IN_PROGRESS}), but a
	 * {@code DONE} task must be reopened before it can be started again
	 * ({@code DONE -> IN_PROGRESS} is rejected). Same-state moves are rejected too, so a
	 * caller acting on stale state gets an error instead of a silent no-op.
	 *
	 * <p>This only changes the field in memory. The row is updated when the surrounding
	 * transaction commits and Hibernate detects the change.
	 *
	 * @throws InvalidStatusTransitionException if the transition is not allowed
	 */
	private void transitionTo(TaskStatus target) {
		if (!status.canTransitionTo(target)) {
			throw new InvalidStatusTransitionException(id, status, target);
		}
		this.status = target;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}

}
