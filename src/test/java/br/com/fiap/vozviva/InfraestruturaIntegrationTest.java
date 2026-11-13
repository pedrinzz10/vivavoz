package br.com.fiap.vozviva;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-001: valida a conexão real com MongoDB e RabbitMQ.
 * É ignorado automaticamente quando não há Docker disponível na máquina.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class InfraestruturaIntegrationTest {

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Test
	void conexaoMongo() {
		Document salvo = mongoTemplate.insert(new Document("mensagem", "olá, VozViva"), "infra_teste");

		Document lido = mongoTemplate.findById(salvo.getObjectId("_id"), Document.class, "infra_teste");

		assertThat(lido).isNotNull();
		assertThat(lido.getString("mensagem")).isEqualTo("olá, VozViva");
	}

	@Test
	void conexaoRabbit() {
		String fila = amqpAdmin.declareQueue(new Queue("infra.teste", true, false, true));

		rabbitTemplate.convertAndSend(fila, "alerta de teste");
		Object recebido = rabbitTemplate.receiveAndConvert(fila, 5_000);

		assertThat(recebido).isEqualTo("alerta de teste");
	}

}
