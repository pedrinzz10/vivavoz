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
 * <p>
 * Os reenvios têm limite: só contam os que chegaram ao broker (com o broker fora do ar, nada é
 * gasto). Esgotado o limite (por exemplo, a mensagem foi para a DLQ várias vezes), a emergência vira
 * FALHA_NO_AVISO, sai do reenvio automático e o app orienta a ligar 192.
 */
@Component
@ConditionalOnBooleanProperty(name = "vozviva.emergencias.reenvio.habilitado", matchIfMissing = true)
public class ReenvioEmergencias {

	static final Duration ESPERA_ANTES_DE_REENVIAR = Duration.ofSeconds(30);
	static final int MAXIMO_REENVIOS = 5;

	private static final Logger log = LoggerFactory.getLogger(ReenvioEmergencias.class);

	private final EmergenciaRepository emergencias;
	private final PublicadorEmergencia publicador;

	public ReenvioEmergencias(EmergenciaRepository emergencias, PublicadorEmergencia publicador) {
		this.emergencias = emergencias;
		this.publicador = publicador;
	}

	@Scheduled(fixedDelayString = "${vozviva.emergencias.reenvio.intervalo:30s}")
	@Transactional
	public void reenviarPendentes() {
		Instant limite = Instant.now().minus(ESPERA_ANTES_DE_REENVIAR);
		for (Emergencia pendente : emergencias.findTop50ByStatusAndAcionadaEmBeforeOrderByAcionadaEmAsc(
				StatusEmergencia.ACIONADA, limite)) {
			if (pendente.getReenvios() >= MAXIMO_REENVIOS) {
				if (emergencias.marcarFalhaNoAviso(pendente.getId(), StatusEmergencia.ACIONADA,
						StatusEmergencia.FALHA_NO_AVISO) > 0) {
					log.error("Emergência {} sem aviso aos contatos depois de {} reenvios; verifique a DLQ",
							pendente.getId(), MAXIMO_REENVIOS);
				}
				continue;
			}
			log.warn("Reenviando emergência {} que ainda não foi processada", pendente.getId());
			if (!publicador.publicar(new EmergenciaAcionadaEvento(pendente.getId()))) {
				return; // broker ainda fora do ar: tenta de novo no próximo ciclo, sem gastar reenvio
			}
			emergencias.contarReenvio(pendente.getId(), StatusEmergencia.ACIONADA);
		}
	}

}
