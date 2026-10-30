package io.github.aiyer8594.almanac.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Covers the web layer only: routing, validation, JSON, and exception mapping. The
 * service is mocked, so each failure can be raised directly.
 * {@link TaskApiIntegrationTest} covers the same paths through the real stack.
 */
@WebMvcTest(TaskController.class)
class TaskControllerTest {

	private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

	private static final TaskResponse TASK = new TaskResponse(1, "Write tests", null, TaskStatus.TODO,
			null, NOW, NOW, 0);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private TaskService service;

	@Test
	void getReturnsTask() {
		given(service.get(1)).willReturn(TASK);

		assertThat(mvc.get().uri("/api/tasks/1"))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.title")
			.isEqualTo("Write tests");
	}

	@Test
	void listDefaultsToIdOrder() {
		given(service.list(any())).willReturn(new PageImpl<>(List.of(TASK), PageRequest.of(0, 20), 1));

		assertThat(mvc.get().uri("/api/tasks")).hasStatusOk();

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(pageable.capture());
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("id"));
	}

	@Test
	void createReturnsCreatedWithLocation() {
		given(service.create(any())).willReturn(TASK);

		assertThat(mvc.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Write tests"}
					"""))
			.hasStatus(HttpStatus.CREATED)
			.hasHeader(HttpHeaders.LOCATION, "http://localhost/api/tasks/1");
	}

	@Test
	void blankTitleReturnsBadRequestNamingTheField() {
		var response = assertThat(mvc.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": " "}
					"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

		response.bodyJson().extractingPath("$.errors.title").isEqualTo("must not be blank");
		verifyNoInteractions(service);
	}

	@Test
	void malformedJsonReturnsBadRequest() {
		assertThat(mvc.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void nonNumericIdReturnsBadRequest() {
		assertThat(mvc.get().uri("/api/tasks/abc"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void missingTaskReturnsNotFound() {
		given(service.get(1)).willThrow(new TaskNotFoundException(1));

		assertThat(mvc.get().uri("/api/tasks/1"))
			.hasStatus(HttpStatus.NOT_FOUND)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Task 1 not found");
	}

	@Test
	void invalidTransitionReturnsConflictWithStates() {
		given(service.start(1))
			.willThrow(new InvalidStatusTransitionException(1L, TaskStatus.DONE, TaskStatus.IN_PROGRESS));

		var response = assertThat(mvc.post().uri("/api/tasks/1/start"))
			.hasStatus(HttpStatus.CONFLICT)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

		response.bodyJson().extractingPath("$.from").isEqualTo("DONE");
		response.bodyJson().extractingPath("$.to").isEqualTo("IN_PROGRESS");
	}

	@Test
	void concurrentUpdateReturnsConflictWithoutLeakingTheCause() {
		given(service.update(eq(1L), any()))
			.willThrow(new OptimisticLockingFailureException("update tasks set secret_sql"));

		assertThat(mvc.put().uri("/api/tasks/1").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Write tests"}
					"""))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyText()
			.doesNotContain("secret_sql");
	}

	@Test
	void deleteReturnsNoContent() {
		assertThat(mvc.delete().uri("/api/tasks/1")).hasStatus(HttpStatus.NO_CONTENT);

		verify(service).delete(1);
	}

	@Test
	void deleteMissingTaskReturnsNotFound() {
		willThrow(new TaskNotFoundException(1)).given(service).delete(1);

		assertThat(mvc.delete().uri("/api/tasks/1")).hasStatus(HttpStatus.NOT_FOUND);
	}

}
