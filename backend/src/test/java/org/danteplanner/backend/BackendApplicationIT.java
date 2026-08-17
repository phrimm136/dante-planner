package org.danteplanner.backend;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class BackendApplicationIT extends SharedMySqlContainerSupport {

	@Test
	void applicationContext_WhenStarted_LoadsSuccessfully() {
	}

}
