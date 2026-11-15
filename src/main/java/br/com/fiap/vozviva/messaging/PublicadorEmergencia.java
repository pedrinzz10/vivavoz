package br.com.fiap.vozviva.messaging;

import br.com.fiap.vozviva.config.RabbitEmergenciaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica no RabbitMQ somente depois que a emergência foi gravada no banco (após o commit).
 * Assim o consumidor nunca procura uma emergência que ainda não existe.
 * <p>
 * A publicação roda em outra thread ({@code @Async}): o 202 ao cidadão não espera o broker, nem
 * quando ele está lento ou fora do ar. Nesse caso a emergência continua gravada como ACIONADA e o
 * {@link ReenvioEmergencias} tenta publicar de novo: o cidadão não recebe erro por isso.
 */
@Component
public class PublicadorEmergencia {

	private static final Logger log = LoggerFactory.getLogger(PublicadorEmergencia.class);

	private final RabbitTemplate rabbit;

	public PublicadorEmergencia(RabbitTemplate rabbit) {
		this.rabbit = rabbit;
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void aoAcionar(EmergenciaAcionadaEvento evento) {
		publicar(evento);
	}

	/**
	 * @return {@code true} se a mensagem foi entregue ao broker
	 */
	public boolean publicar(EmergenciaAcionadaEvento evento) {
		try {
			rabbit.convertAndSend(RabbitEmergenciaConfig.EXCHANGE, RabbitEmergenciaConfig.ROTA_ACIONADA, evento);
			log.info("Emergência {} publicada na fila", evento.emergenciaId());
			return true;
		}
		catch (AmqpException ex) {
			log.error("Não foi possível publicar a emergência {}; será reenviada automaticamente",
					evento.emergenciaId(), ex);
			return false;
		}
	}

}
