package io.github.aiyer8594.almanac.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.aiyer8594.almanac.TestcontainersConfiguration;

/**
 * Drives the API through the real controller, service, and Postgres. These are the
 * behaviors a slice test cannot show: that a failure raised inside a transaction reaches
 * the client as the right status, and what the paged JSON looks like over real rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskApiIntegrationTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoSpyBean
	private TaskRepository repository;

	@AfterEach
	void deleteTasks() {
		repository.deleteAll();
	}

	@Test
	void taskMovesThroughItsLifecycle() {
		String path = create("Write tests");

		assertThat(mvc.get().uri(path)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("TODO");
		assertThat(mvc.put().uri(path).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Write more tests"}
					"""))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.title")
			.isEqualTo("Write more tests");
		assertThat(mvc.post().uri(path + "/start")).hasStatusOk()
			.bodyJson().extractingPath("$.status").isEqualTo("IN_PROGRESS");
		assertThat(mvc.post().uri(path + "/done")).hasStatusOk()
			.bodyJson().extractingPath("$.status").isEqualTo("DONE");
		assertThat(mvc.post().uri(path + "/reopen")).hasStatusOk()
			.bodyJson().extractingPath("$.version").isEqualTo(4);
		assertThat(mvc.delete().uri(path)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri(path)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void rejectedTransitionReturnsConflictAndLeavesTaskUnchanged() {
		String path = create("Write tests");
		assertThat(mvc.post().uri(path + "/done")).hasStatusOk();

		assertThat(mvc.post().uri(path + "/start")).hasStatus(HttpStatus.CONFLICT);

		assertThat(mvc.get().uri(path)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("DONE");
	}

	@Test
	void concurrentWriteReturnsConflictAndKeepsTheOtherWritersChange() {
		String path = create("Write tests");
		long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
		TransactionTemplate otherWriter = new TransactionTemplate(transactionManager);
		otherWriter.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		doAnswer(flush -> {
			// Another writer commits after this request loaded the task but before it flushes.
			otherWriter.executeWithoutResult(status -> jdbc
				.update("update tasks set title = 'Theirs', version = version + 1 where id = ?", id));
			// The spy wraps a JDK proxy, so the real call goes through its default answer.
			return mockingDetails(repository).getMockCreationSettings().getDefaultAnswer().answer(flush);
		}).when(repository).flush();

		assertThat(mvc.put().uri(path).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Mine"}
					"""))
			.hasStatus(HttpStatus.CONFLICT);

		assertThat(mvc.get().uri(path)).hasStatusOk().bodyJson().extractingPath("$.title").isEqualTo("Theirs");
	}

	@Test
	void listReturnsPagedJson() {
		create("First");
		create("Second");

		var response = assertThat(mvc.get().uri("/api/tasks?size=1")).hasStatusOk();

		response.bodyJson().extractingPath("$.content[0].title").isEqualTo("First");
		response.bodyJson().extractingPath("$.page.totalElements").isEqualTo(2);
		response.bodyJson().extractingPath("$.page.totalPages").isEqualTo(2);
	}

	@Test
	void pageSizeIsCapped() {
		assertThat(mvc.get().uri("/api/tasks?size=100000")).hasStatusOk()
			.bodyJson().extractingPath("$.page.size").isEqualTo(100);
	}

	@Test
	void unknownSortPropertyReturnsBadRequest() {
		assertThat(mvc.get().uri("/api/tasks?sort=nope"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
	}

	/** Creates a task and returns its path, taken from the {@code Location} header. */
	private String create(String title) {
		var result = mvc.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "%s"}
					""".formatted(title))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION)).getPath();
	}

}
