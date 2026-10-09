package io.github.aiyer8594.almanac;

import org.springframework.boot.SpringApplication;

public class TestAlmanacApplication {

	public static void main(String[] args) {
		SpringApplication.from(AlmanacApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
