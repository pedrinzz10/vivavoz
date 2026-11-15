package br.com.fiap.vozviva.messaging;

import br.com.fiap.vozviva.config.RabbitEmergenciaConfig;
import br.com.fiap.vozviva.service.AvisosDeEmergencia;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome os eventos de emergência da fila de forma assíncrona. Falhas são tentadas de novo com
 * espera crescente (spring.rabbitmq.listener.simple.retry) e, esgotadas as tentativas, a mensagem
 * vai para a DLQ.
 */
@Component
public class ConsumidorEmergencia {

	private final AvisosDeEmergencia avisos;

	public ConsumidorEmergencia(AvisosDeEmergencia avisos) {
		this.avisos = avisos;
	}

	@RabbitListener(queues = RabbitEmergenciaConfig.FILA_ACIONADAS)
	public void consumir(EmergenciaAcionadaEvento evento) {
		avisos.avisarContatos(evento.emergenciaId());
	}

}
