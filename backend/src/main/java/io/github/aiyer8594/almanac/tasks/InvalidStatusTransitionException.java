package io.github.aiyer8594.almanac.tasks;

/**
 * Thrown when a task is asked to move to a status its current status does not allow.
 * A dedicated type rather than {@link IllegalStateException}, so the API can map it to
 * 409 Conflict without also turning genuine programming errors into client errors.
 */
public class InvalidStatusTransitionException extends RuntimeException {

	private final TaskStatus from;

	private final TaskStatus to;

	public InvalidStatusTransitionException(Long taskId, TaskStatus from, TaskStatus to) {
		super("Task %d cannot move from %s to %s".formatted(taskId, from, to));
		this.from = from;
		this.to = to;
	}

	public TaskStatus getFrom() {
		return from;
	}

	public TaskStatus getTo() {
		return to;
	}

}
