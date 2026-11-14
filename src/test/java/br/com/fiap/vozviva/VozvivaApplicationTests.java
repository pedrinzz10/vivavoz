package br.com.fiap.vozviva;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VozvivaApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context).isNotNull();
	}

	@Test
	void flywayConfiguradoParaAsMigracoesDoProjeto() {
		Flyway flyway = context.getBean(Flyway.class);

		assertThat(flyway.getConfiguration().getLocations())
			.extracting(Object::toString)
			.containsExactly("classpath:db/migration");
	}

}
