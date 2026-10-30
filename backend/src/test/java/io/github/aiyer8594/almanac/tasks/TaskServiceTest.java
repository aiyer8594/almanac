package io.github.aiyer8594.almanac.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.aiyer8594.almanac.TestcontainersConfiguration;

/**
 * Covers {@link TaskService} against Postgres. The test-managed transaction that
 * {@code @DataJpaTest} normally wraps around each test is turned off, so every service
 * call runs in its own transaction and commits, the same as in production. That is
 * what proves updates persist without an explicit save and that a rejected change
 * leaves the row untouched. Rows are removed after each test instead of rolled back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, TaskService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TaskServiceTest {

	private final TaskService service;

	private final TaskRepository repository;

	@Autowired
	TaskServiceTest(TaskService service, TaskRepository repository) {
		this.service = service;
		this.repository = repository;
	}

	@AfterEach
	void deleteTasks() {
		repository.deleteAll();
	}

	@Test
	void createPersistsTaskAsTodo() {
		TaskResponse created = service.create(new TaskRequest("Write tests", "Service", null));

		Task stored = repository.findById(created.id()).orElseThrow();
		assertThat(stored.getTitle()).isEqualTo("Write tests");
		assertThat(stored.getStatus()).isEqualTo(TaskStatus.TODO);
		assertThat(created.status()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	void getMissingTaskThrowsNotFound() {
		assertThatThrownBy(() -> service.get(999_999))
			.isInstanceOf(TaskNotFoundException.class)
			.hasMessage("Task 999999 not found");
	}

	@Test
	void updateReplacesDetailsAndReturnsNewVersion() {
		long id = service.create(new TaskRequest("Write tests", null, null)).id();
		LocalDate due = LocalDate.of(2026, 11, 15);

		TaskResponse updated = service.update(id, new TaskRequest("Write more tests", "Now", due));

		assertThat(updated.version()).isEqualTo(1);
		Task stored = repository.findById(id).orElseThrow();
		assertThat(stored.getTitle()).isEqualTo("Write more tests");
		assertThat(stored.getDescription()).isEqualTo("Now");
		assertThat(stored.getDueDate()).isEqualTo(due);
	}

	@Test
	void startPersistsWithoutExplicitSave() {
		long id = service.create(new TaskRequest("Write tests", null, null)).id();

		TaskResponse started = service.start(id);

		assertThat(started.status()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(repository.findById(id).orElseThrow().getStatus())
			.isEqualTo(TaskStatus.IN_PROGRESS);
	}

	@Test
	void rejectedTransitionLeavesRowUnchanged() {
		long id = service.create(new TaskRequest("Write tests", null, null)).id();
		service.markDone(id);

		assertThatThrownBy(() -> service.start(id))
			.isInstanceOf(InvalidStatusTransitionException.class);

		Task stored = repository.findById(id).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(TaskStatus.DONE);
		assertThat(stored.getVersion()).isEqualTo(1);
	}

	@Test
	void deleteRemovesTask() {
		long id = service.create(new TaskRequest("Write tests", null, null)).id();

		service.delete(id);

		assertThat(repository.existsById(id)).isFalse();
	}

	@Test
	void deleteMissingTaskThrowsNotFound() {
		assertThatThrownBy(() -> service.delete(999_999))
			.isInstanceOf(TaskNotFoundException.class);
	}

}
