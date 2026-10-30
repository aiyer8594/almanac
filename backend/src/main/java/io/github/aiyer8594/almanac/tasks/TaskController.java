package io.github.aiyer8594.almanac.tasks;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The HTTP interface to tasks. Status changes are POSTs to named actions rather than a
 * writable {@code status} field, because each one is an operation with rules of its own
 * and not a value a client sets. Failures are mapped by {@link TaskExceptionHandler}.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskService service;

	TaskController(TaskService service) {
		this.service = service;
	}

	/**
	 * Lists tasks in id order by default, because without a sort Postgres does not
	 * guarantee a row order and pages could repeat or skip rows. {@link PagedModel} is
	 * used instead of returning a {@code Page}, whose JSON form Spring does not keep stable.
	 */
	@GetMapping
	public PagedModel<TaskResponse> list(@PageableDefault(sort = "id") Pageable pageable) {
		return new PagedModel<>(service.list(pageable));
	}

	@GetMapping("/{id}")
	public TaskResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@PostMapping
	public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
		TaskResponse created = service.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(created.id())
			.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public TaskResponse update(@PathVariable long id, @Valid @RequestBody TaskRequest request) {
		return service.update(id, request);
	}

	@PostMapping("/{id}/start")
	public TaskResponse start(@PathVariable long id) {
		return service.start(id);
	}

	@PostMapping("/{id}/done")
	public TaskResponse markDone(@PathVariable long id) {
		return service.markDone(id);
	}

	@PostMapping("/{id}/reopen")
	public TaskResponse reopen(@PathVariable long id) {
		return service.reopen(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		service.delete(id);
	}

}
