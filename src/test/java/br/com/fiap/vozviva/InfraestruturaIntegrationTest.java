package br.com.fiap.vozviva;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-001/SPEC-022: valida a conexão real com SQL Server (mesmo motor do Azure SQL), onde o
 * Flyway aplica as migrações ao subir o contexto, e com o RabbitMQ. É ignorado automaticamente sem Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class InfraestruturaIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Test
	void conexaoSqlServer() {
		String versao = jdbc.queryForObject("SELECT @@VERSION", String.class);

		assertThat(versao).contains("Microsoft SQL Server");
	}

	@Test
	void conexaoRabbit() {
		String fila = amqpAdmin.declareQueue(new Queue("infra.teste", true, false, true));

		rabbitTemplate.convertAndSend(fila, "alerta de teste");
		Object recebido = rabbitTemplate.receiveAndConvert(fila, 5_000);

		assertThat(recebido).isEqualTo("alerta de teste");
	}

}
