package io.github.aiyer8594.almanac.tasks;

import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task use cases and the transaction boundary for them. Each public method is one
 * transaction: it loads the entity, applies the change through the entity's own
 * methods, and returns a {@link TaskResponse}. Updates need no explicit save; Hibernate
 * writes the changed fields when the transaction commits.
 *
 * <p>The class default is read-only, which lets Hibernate skip dirty checking on
 * queries; methods that write opt back in.
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

	private final TaskRepository repository;

	TaskService(TaskRepository repository) {
		this.repository = repository;
	}

	public Page<TaskResponse> list(Pageable pageable) {
		return repository.findAll(pageable).map(TaskResponse::from);
	}

	public TaskResponse get(long id) {
		return TaskResponse.from(find(id));
	}

	@Transactional
	public TaskResponse create(TaskRequest request) {
		Task task = new Task(request.title(), request.description(), request.dueDate());
		return TaskResponse.from(repository.save(task));
	}

	@Transactional
	public TaskResponse update(long id, TaskRequest request) {
		return change(id, task -> task.changeDetails(request.title(), request.description(),
				request.dueDate()));
	}

	@Transactional
	public TaskResponse start(long id) {
		return change(id, Task::start);
	}

	@Transactional
	public TaskResponse markDone(long id) {
		return change(id, Task::markDone);
	}

	@Transactional
	public TaskResponse reopen(long id) {
		return change(id, Task::reopen);
	}

	@Transactional
	public void delete(long id) {
		repository.delete(find(id));
	}

	/**
	 * Applies {@code change} and flushes before mapping, so the response carries the
	 * version and timestamp the update produced rather than the values before it.
	 */
	private TaskResponse change(long id, Consumer<Task> change) {
		Task task = find(id);
		change.accept(task);
		repository.flush();
		return TaskResponse.from(task);
	}

	private Task find(long id) {
		return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
	}

}
