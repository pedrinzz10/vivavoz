package br.com.fiap.vozviva.usuario;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protege o login contra tentativa e erro de senhas: cada e-mail tem {@value #MAXIMO_TENTATIVAS}
 * tentativas por janela; esgotadas, o e-mail fica bloqueado por {@link #BLOQUEIO}. Um login correto
 * devolve as tentativas.
 * <p>
 * A tentativa é consumida de forma atômica <b>antes</b> de a senha ser conferida. Assim, requisições
 * simultâneas não conseguem passar todas pela verificação antes de alguma falha ser registrada.
 * <p>
 * Guarda o estado em memória, o que basta para uma instância. Com várias instâncias, o contador
 * deveria ir para um armazenamento compartilhado (ex.: Redis).
 */
@Component
public class LimitadorTentativasLogin {

	static final int MAXIMO_TENTATIVAS = 5;
	static final Duration JANELA = Duration.ofMinutes(15);
	static final Duration BLOQUEIO = Duration.ofMinutes(15);
	private static final int LIMITE_ENTRADAS = 10_000;

	private final Map<String, Registro> registros = new ConcurrentHashMap<>();
	private final Clock clock;

	public LimitadorTentativasLogin() {
		this(Clock.systemUTC());
	}

	LimitadorTentativasLogin(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Reserva uma tentativa para o e-mail. A última tentativa disponível é permitida e já ativa o
	 * bloqueio para as próximas.
	 *
	 * @throws MuitasTentativasException se o e-mail estiver bloqueado
	 */
	public void consumirTentativa(String email) {
		Instant agora = Instant.now(clock);
		limparExpirados(agora);
		Registro resultado = registros.compute(email, (chave, atual) -> {
			if (atual != null && atual.bloqueadoEm(agora)) {
				return atual.recusado();
			}
			if (atual == null || atual.janelaVencida(agora)) {
				return Registro.primeira(agora);
			}
			int tentativas = atual.tentativas() + 1;
			Instant bloqueadoAte = tentativas >= MAXIMO_TENTATIVAS ? agora.plus(BLOQUEIO) : null;
			return new Registro(tentativas, atual.inicioJanela(), bloqueadoAte, false);
		});
		if (resultado.recusada()) {
			throw new MuitasTentativasException();
		}
	}

	/** Login correto: zera as tentativas do e-mail. */
	public void registrarSucesso(String email) {
		registros.remove(email);
	}

	private void limparExpirados(Instant agora) {
		if (registros.size() < LIMITE_ENTRADAS) {
			return;
		}
		registros.entrySet().removeIf(entrada -> entrada.getValue().janelaVencida(agora)
				&& !entrada.getValue().bloqueadoEm(agora));
	}

	/**
	 * @param recusada indica que a última tentativa foi recusada por bloqueio (não altera o estado salvo)
	 */
	private record Registro(int tentativas, Instant inicioJanela, Instant bloqueadoAte, boolean recusada) {

		static Registro primeira(Instant agora) {
			return new Registro(1, agora, null, false);
		}

		boolean bloqueadoEm(Instant agora) {
			return bloqueadoAte != null && agora.isBefore(bloqueadoAte);
		}

		boolean janelaVencida(Instant agora) {
			return agora.isAfter(inicioJanela.plus(JANELA));
		}

		Registro recusado() {
			return new Registro(tentativas, inicioJanela, bloqueadoAte, true);
		}

	}

}
