package io.github.aiyer8594.almanac.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskStatusTest {

	/**
	 * Lists every from/to pair, not just the allowed ones, so a rule that is
	 * accidentally widened (including same-state moves) fails here, not only one
	 * that is narrowed.
	 */
	@ParameterizedTest(name = "{0} -> {1}: {2}")
	@CsvSource(textBlock = """
			TODO,        TODO,        false
			TODO,        IN_PROGRESS, true
			TODO,        DONE,        true
			IN_PROGRESS, TODO,        false
			IN_PROGRESS, IN_PROGRESS, false
			IN_PROGRESS, DONE,        true
			DONE,        TODO,        true
			DONE,        IN_PROGRESS, false
			DONE,        DONE,        false
			""")
	void canTransitionTo(TaskStatus from, TaskStatus to, boolean allowed) {
		assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
	}

}
