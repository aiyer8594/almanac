package io.github.aiyer8594.almanac.tasks;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A task as the API returns it. Mapped from the entity inside the service's
 * transaction, so callers never hold a managed {@link Task}.
 */
public record TaskResponse(
		long id,
		String title,
		String description,
		TaskStatus status,
		LocalDate dueDate,
		Instant createdAt,
		Instant updatedAt,
		long version) {

	static TaskResponse from(Task task) {
		return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
				task.getStatus(), task.getDueDate(), task.getCreatedAt(), task.getUpdatedAt(),
				task.getVersion());
	}

}
