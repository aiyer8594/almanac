package io.github.aiyer8594.almanac;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AlmanacApplicationTests {

	@Test
	void contextLoads() {
	}

}
