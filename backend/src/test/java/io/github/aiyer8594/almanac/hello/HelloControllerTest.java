package io.github.aiyer8594.almanac.hello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(HelloController.class)
class HelloControllerTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void helloReturnsGreeting() {
		assertThat(mvc.get().uri("/hello"))
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.TEXT_PLAIN)
				.hasBodyTextEqualTo("Hello, world!");
	}

	@Test
	void unknownPathReturnsNotFound() {
		assertThat(mvc.get().uri("/nope"))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

}
