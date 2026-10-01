package com.financetracker.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "recurring.scheduler.enabled=false")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
