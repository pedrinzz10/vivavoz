package br.com.fiap.vozviva.usuario;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protege o login contra tentativa e erro de senhas: depois de {@value #MAXIMO_FALHAS} falhas seguidas
 * no mesmo e-mail dentro da janela, o e-mail fica bloqueado por {@link #BLOQUEIO}.
 * <p>
 * Guarda o estado em memória, o que basta para uma instância. Com várias instâncias, o contador
 * deveria ir para um armazenamento compartilhado (ex.: Redis).
 */
@Component
public class LimitadorTentativasLogin {

	static final int MAXIMO_FALHAS = 5;
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
	 * @throws MuitasTentativasException se o e-mail estiver bloqueado
	 */
	public void verificar(String email) {
		Registro registro = registros.get(email);
		if (registro != null && registro.bloqueadoAte() != null && Instant.now(clock).isBefore(registro.bloqueadoAte())) {
			throw new MuitasTentativasException();
		}
	}

	public void registrarFalha(String email) {
		Instant agora = Instant.now(clock);
		limparExpirados(agora);
		registros.compute(email, (chave, atual) -> {
			if (atual == null || agora.isAfter(atual.inicioJanela().plus(JANELA))) {
				return new Registro(1, agora, null);
			}
			int falhas = atual.falhas() + 1;
			Instant bloqueadoAte = falhas >= MAXIMO_FALHAS ? agora.plus(BLOQUEIO) : null;
			return new Registro(falhas, atual.inicioJanela(), bloqueadoAte);
		});
	}

	public void registrarSucesso(String email) {
		registros.remove(email);
	}

	private void limparExpirados(Instant agora) {
		if (registros.size() < LIMITE_ENTRADAS) {
			return;
		}
		registros.entrySet().removeIf(entrada -> {
			Registro r = entrada.getValue();
			boolean janelaVencida = agora.isAfter(r.inicioJanela().plus(JANELA));
			boolean semBloqueio = r.bloqueadoAte() == null || agora.isAfter(r.bloqueadoAte());
			return janelaVencida && semBloqueio;
		});
	}

	private record Registro(int falhas, Instant inicioJanela, Instant bloqueadoAte) {
	}

}
