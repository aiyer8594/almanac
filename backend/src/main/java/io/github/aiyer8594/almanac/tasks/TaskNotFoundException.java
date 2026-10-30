package io.github.aiyer8594.almanac.tasks;

/**
 * Thrown when no task exists for the requested id. Its own type so the API can map it
 * to 404 Not Found.
 */
public class TaskNotFoundException extends RuntimeException {

	private final long taskId;

	public TaskNotFoundException(long taskId) {
		super("Task %d not found".formatted(taskId));
		this.taskId = taskId;
	}

	public long getTaskId() {
		return taskId;
	}

}
