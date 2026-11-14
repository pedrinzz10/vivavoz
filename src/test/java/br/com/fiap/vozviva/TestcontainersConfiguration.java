package br.com.fiap.vozviva;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Containers reais de SQL Server (mesmo motor do Azure SQL) e RabbitMQ para testes de integração.
 * O {@link ServiceConnection} sobrescreve as propriedades de conexão do perfil de teste.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MSSQLServerContainer sqlServerContainer() {
		return new MSSQLServerContainer(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
			.acceptLicense();
	}

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitMqContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
	}

}
