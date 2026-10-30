package io.github.aiyer8594.almanac.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import io.github.aiyer8594.almanac.TestcontainersConfiguration;

/**
 * Covers what only a real database can prove: the {@link Task} mapping round-trips
 * against the Flyway schema, and the timestamps and {@code @Version} column behave on
 * update. Runs against Postgres in Testcontainers, not an embedded database.
 *
 * <p>Each test flushes and clears the persistence context before reading back, so the
 * assertions see what is in the table rather than the cached Java object.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TaskRepositoryTest {

	private final TaskRepository repository;

	private final TestEntityManager entityManager;

	@Autowired
	TaskRepositoryTest(TaskRepository repository, TestEntityManager entityManager) {
		this.repository = repository;
		this.entityManager = entityManager;
	}

	@Test
	void saveAssignsIdAndRoundTripsAllFields() {
		LocalDate due = LocalDate.of(2026, 11, 15);
		Long id = repository.save(new Task("Write tests", "Repository slice", due)).getId();
		flushAndClear();

		Task loaded = repository.findById(id).orElseThrow();

		assertThat(loaded.getId()).isNotNull();
		assertThat(loaded.getTitle()).isEqualTo("Write tests");
		assertThat(loaded.getDescription()).isEqualTo("Repository slice");
		assertThat(loaded.getDueDate()).isEqualTo(due);
		assertThat(loaded.getStatus()).isEqualTo(TaskStatus.TODO);
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(loaded.getUpdatedAt()).isNotNull();
		assertThat(loaded.getVersion()).isZero();
	}

	@Test
	void updateIncrementsVersionAndMovesUpdatedAt() {
		Long id = repository.save(new Task("Write tests", null, null)).getId();
		flushAndClear();
		Task before = repository.findById(id).orElseThrow();
		Instant createdAt = before.getCreatedAt();
		Instant updatedAt = before.getUpdatedAt();

		before.start();
		flushAndClear();
		Task after = repository.findById(id).orElseThrow();

		assertThat(after.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(after.getVersion()).isEqualTo(1);
		assertThat(after.getUpdatedAt()).isAfter(updatedAt);
		assertThat(after.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void saveOfStaleCopyFailsWithOptimisticLock() {
		Long id = repository.save(new Task("Write tests", null, null)).getId();
		flushAndClear();
		Task stale = repository.findById(id).orElseThrow();
		entityManager.clear();

		// Another writer updates the row first, bumping its version to 1.
		repository.findById(id).orElseThrow().start();
		flushAndClear();

		stale.markDone();

		assertThatThrownBy(() -> repository.saveAndFlush(stale))
			.isInstanceOf(OptimisticLockingFailureException.class);
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

}
