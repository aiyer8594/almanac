package io.github.aiyer8594.almanac.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Covers the intent methods on {@link Task}. The full transition table is in
 * {@link TaskStatusTest}; this checks that each method applies the allowed move and that
 * a rejected move throws without changing the status.
 */
class TaskTest {

	@Test
	void newTaskStartsAsTodo() {
		Task task = new Task("Write tests", null, null);

		assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	void startMovesTodoToInProgress() {
		Task task = new Task("Write tests", null, null);

		task.start();

		assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
	}

	@Test
	void markDoneClosesTodoTaskDirectly() {
		Task task = new Task("Write tests", null, null);

		task.markDone();

		assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	void markDoneClosesInProgressTask() {
		Task task = new Task("Write tests", null, null);
		task.start();

		task.markDone();

		assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	void reopenReturnsDoneTaskToTodo() {
		Task task = new Task("Write tests", null, null);
		task.markDone();

		task.reopen();

		assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	void startOnDoneTaskThrowsAndLeavesStatusUnchanged() {
		Task task = new Task("Write tests", null, null);
		task.markDone();

		assertThatThrownBy(task::start)
			.isInstanceOfSatisfying(InvalidStatusTransitionException.class, ex -> {
				assertThat(ex.getFrom()).isEqualTo(TaskStatus.DONE);
				assertThat(ex.getTo()).isEqualTo(TaskStatus.IN_PROGRESS);
			});
		assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
	}

}
