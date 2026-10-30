package io.github.aiyer8594.almanac.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link Task}. Spring Data generates the implementation at startup.
 *
 * <p>No custom query methods yet: CRUD by id covers the current API. Add derived or
 * {@code @Query} methods when a use case needs them, so each one has a caller and a test.
 */
interface TaskRepository extends JpaRepository<Task, Long> {
}
