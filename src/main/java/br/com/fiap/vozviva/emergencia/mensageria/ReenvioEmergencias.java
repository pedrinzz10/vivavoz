package br.com.fiap.vozviva.emergencia.mensageria;

import br.com.fiap.vozviva.emergencia.Emergencia;
import br.com.fiap.vozviva.emergencia.EmergenciaRepository;
import br.com.fiap.vozviva.emergencia.StatusEmergencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Rede de segurança da mensageria: emergências que continuam ACIONADAS depois de um tempo (por
 * exemplo, porque o RabbitMQ estava fora do ar na hora) são publicadas de novo. O consumidor é
 * idempotente, então um reenvio nunca avisa os contatos em dobro.
 */
@Component
@ConditionalOnBooleanProperty(name = "vozviva.emergencias.reenvio.habilitado", matchIfMissing = true)
public class ReenvioEmergencias {

	static final Duration ESPERA_ANTES_DE_REENVIAR = Duration.ofSeconds(30);

	private static final Logger log = LoggerFactory.getLogger(ReenvioEmergencias.class);

	private final EmergenciaRepository emergencias;
	private final PublicadorEmergencia publicador;

	public ReenvioEmergencias(EmergenciaRepository emergencias, PublicadorEmergencia publicador) {
		this.emergencias = emergencias;
		this.publicador = publicador;
	}

	@Scheduled(fixedDelayString = "${vozviva.emergencias.reenvio.intervalo:30s}")
	@Transactional(readOnly = true)
	public void reenviarPendentes() {
		Instant limite = Instant.now().minus(ESPERA_ANTES_DE_REENVIAR);
		for (Emergencia pendente : emergencias.findTop50ByStatusAndAcionadaEmBeforeOrderByAcionadaEmAsc(
				StatusEmergencia.ACIONADA, limite)) {
			log.warn("Reenviando emergência {} que ainda não foi processada", pendente.getId());
			if (!publicador.publicar(new EmergenciaAcionadaEvento(pendente.getId()))) {
				return; // broker ainda fora do ar: tenta de novo no próximo ciclo
			}
		}
	}

}
