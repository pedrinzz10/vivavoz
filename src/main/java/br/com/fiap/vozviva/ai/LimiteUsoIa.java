package br.com.fiap.vozviva.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de chamadas ao modelo de IA por pessoa (padrão: 60 por hora). Cada pergunta por voz pode custar até duas
 * chamadas (classificar e responder), e sem limite uma conta sozinha consumiria o orçamento de IA de todos.
 * <p>
 * Acima do limite a API <b>não recusa</b> o pedido: responde só com as regras e a base curada, como quando a IA está
 * fora do ar. Um pedido de socorro ou uma checagem de golpe nunca pode ser bloqueado por causa de custo.
 * <p>
 * Guarda o estado em memória, o que basta para uma instância (como o limitador do login). Com várias instâncias, o
 * contador deveria ir para um armazenamento compartilhado (ex.: Redis).
 */
@Component
public class LimiteUsoIa {

	private static final Logger log = LoggerFactory.getLogger(LimiteUsoIa.class);

	static final Duration JANELA = Duration.ofHours(1);
	private static final int LIMITE_ENTRADAS = 10_000;

	private final Map<Long, Janela> janelas = new ConcurrentHashMap<>();
	private final int maximoPorJanela;
	private final Clock clock;

	@Autowired
	public LimiteUsoIa(@Value("${vozviva.ia.limite-por-hora:60}") int maximoPorJanela) {
		this(maximoPorJanela, Clock.systemUTC());
	}

	LimiteUsoIa(int maximoPorJanela, Clock clock) {
		this.maximoPorJanela = maximoPorJanela;
		this.clock = clock;
	}

	/**
	 * Reserva uma chamada ao modelo para a pessoa.
	 *
	 * @return {@code false} se ela já usou o limite da hora (a resposta segue só com as regras)
	 */
	public boolean consumir(Long usuarioId) {
		if (usuarioId == null) {
			return true;
		}
		Instant agora = Instant.now(clock);
		limparVencidas(agora);
		Janela janela = janelas.compute(usuarioId, (id, atual) -> atual == null || atual.vencida(agora)
				? new Janela(agora, 1) : new Janela(atual.inicio(), atual.usos() + 1));
		boolean permitido = janela.usos() <= maximoPorJanela;
		if (janela.usos() == maximoPorJanela + 1) {
			log.warn("Limite de uso da IA atingido por um usuário; respondendo só com as regras até a próxima hora.");
		}
		return permitido;
	}

	private void limparVencidas(Instant agora) {
		if (janelas.size() >= LIMITE_ENTRADAS) {
			janelas.values().removeIf(j -> j.vencida(agora));
		}
	}

	private record Janela(Instant inicio, int usos) {

		boolean vencida(Instant agora) {
			return !agora.isBefore(inicio.plus(JANELA));
		}

	}

}
