package br.com.fiap.vozviva;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Containers reais de MongoDB e RabbitMQ para testes de integração.
 * O {@link ServiceConnection} sobrescreve as propriedades de conexão do perfil de teste.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDbContainer() {
		return new MongoDBContainer(DockerImageName.parse("mongo:8"));
	}

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitMqContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
	}

}
