package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.repository.InteracaoVozRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Prazo de retenção do histórico de voz (LGPD, princípio da necessidade): o texto falado pode ter dados de saúde e
 * só é útil para a pessoa consultar, para dar contexto à conversa (15 minutos) e para avaliar a IA. Uma vez por dia,
 * apaga o que passou do prazo (padrão: 90 dias, {@code VOZ_RETENCAO_DIAS}).
 */
@Component
public class RetencaoHistoricoVoz {

	private static final Logger log = LoggerFactory.getLogger(RetencaoHistoricoVoz.class);

	private final InteracaoVozRepository interacoes;
	private final Duration retencao;
	private final Clock clock;

	@Autowired
	public RetencaoHistoricoVoz(InteracaoVozRepository interacoes,
			@Value("${vozviva.voz.retencao-dias:90}") int retencaoDias) {
		this(interacoes, retencaoDias, Clock.systemUTC());
	}

	RetencaoHistoricoVoz(InteracaoVozRepository interacoes, int retencaoDias, Clock clock) {
		this.interacoes = interacoes;
		this.retencao = Duration.ofDays(retencaoDias);
		this.clock = clock;
	}

	@Scheduled(cron = "${vozviva.voz.retencao-cron:0 30 3 * * *}")
	@Transactional
	public int apagarVencidas() {
		int apagadas = interacoes.apagarAnterioresA(Instant.now(clock).minus(retencao));
		if (apagadas > 0) {
			log.info("Histórico de voz: {} interações com mais de {} dias apagadas.", apagadas, retencao.toDays());
		}
		return apagadas;
	}

}
