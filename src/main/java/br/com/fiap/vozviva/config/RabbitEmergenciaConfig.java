package br.com.fiap.vozviva.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do RabbitMQ para as emergências:
 * <pre>
 * exchange vozviva.emergencias --(emergencia.acionada)--> fila vozviva.emergencias.acionadas
 *                                                              | rejeitada após as tentativas
 *                                                              v
 * exchange vozviva.emergencias.dlx ----------------------> fila vozviva.emergencias.dlq
 * </pre>
 * Mensagens que falham em todas as tentativas vão para a DLQ, onde podem ser analisadas e reprocessadas.
 */
@Configuration
public class RabbitEmergenciaConfig {

	public static final String EXCHANGE = "vozviva.emergencias";
	public static final String ROTA_ACIONADA = "emergencia.acionada";
	public static final String FILA_ACIONADAS = "vozviva.emergencias.acionadas";
	public static final String EXCHANGE_DLX = "vozviva.emergencias.dlx";
	public static final String FILA_DLQ = "vozviva.emergencias.dlq";

	@Bean
	DirectExchange exchangeEmergencias() {
		return new DirectExchange(EXCHANGE, true, false);
	}

	@Bean
	DirectExchange exchangeEmergenciasDlx() {
		return new DirectExchange(EXCHANGE_DLX, true, false);
	}

	@Bean
	Queue filaEmergenciasAcionadas() {
		return QueueBuilder.durable(FILA_ACIONADAS)
			.deadLetterExchange(EXCHANGE_DLX)
			.deadLetterRoutingKey(ROTA_ACIONADA)
			.build();
	}

	@Bean
	Queue filaEmergenciasDlq() {
		return QueueBuilder.durable(FILA_DLQ).build();
	}

	@Bean
	Binding ligacaoAcionadas() {
		return BindingBuilder.bind(filaEmergenciasAcionadas()).to(exchangeEmergencias()).with(ROTA_ACIONADA);
	}

	@Bean
	Binding ligacaoDlq() {
		return BindingBuilder.bind(filaEmergenciasDlq()).to(exchangeEmergenciasDlx()).with(ROTA_ACIONADA);
	}

	/** Mensagens em JSON (legíveis no painel do RabbitMQ). */
	@Bean
	MessageConverter conversorJson() {
		return new JacksonJsonMessageConverter();
	}

}
