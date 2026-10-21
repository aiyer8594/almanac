package io.github.aiyer8594.almanac.tasks;

/**
 * Lifecycle state of a {@link Task}. Persisted by name, so renaming a constant
 * requires a migration that updates existing rows and the {@code tasks_status_check}
 * constraint.
 */
public enum TaskStatus {

	TODO,
	IN_PROGRESS,
	DONE;

	/**
	 * The allowed transitions, kept next to the states so adding a constant forces a
	 * decision here: the switch has no default and stops compiling until it is covered.
	 */
	boolean canTransitionTo(TaskStatus target) {
		return switch (this) {
			case TODO -> target == IN_PROGRESS || target == DONE;
			case IN_PROGRESS -> target == DONE;
			case DONE -> target == TODO;
		};
	}

}
