package io.github.aiyer8594.almanac.tasks;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The client-editable fields of a task, used to create one and to replace its details.
 * Status is not here: it changes only through the start, done, and reopen operations.
 * The constraints mirror the {@code tasks} columns so bad input fails as a 400 before
 * it reaches the database.
 */
public record TaskRequest(
		@NotBlank @Size(max = 200) String title,
		String description,
		LocalDate dueDate) {
}
