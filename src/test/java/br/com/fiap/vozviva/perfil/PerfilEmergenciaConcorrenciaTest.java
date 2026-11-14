package br.com.fiap.vozviva.perfil;

import br.com.fiap.vozviva.comum.erro.ConflitoException;
import br.com.fiap.vozviva.perfil.dto.ContatoEmergenciaRequest;
import br.com.fiap.vozviva.perfil.dto.PerfilEmergenciaRequest;
import br.com.fiap.vozviva.seguranca.Papel;
import br.com.fiap.vozviva.usuario.Usuario;
import br.com.fiap.vozviva.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras do perfil sob requisições simultâneas (apontamentos da revisão do PR #14).
 */
@SpringBootTest
@ActiveProfiles("test")
class PerfilEmergenciaConcorrenciaTest {

	@Autowired
	private PerfilEmergenciaService servico;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ContatoEmergenciaRepository contatos;

	@Test
	void inclusoesSimultaneasNaoPassamDoLimiteDeContatos() throws Exception {
		Usuario cuidador = usuario(Papel.CUIDADOR);
		Usuario cidadao = usuario(Papel.CIDADAO);
		Long perfil = servico.criar(cuidador.getId(), perfilDe(cidadao)).id();
		for (int i = 1; i < PerfilEmergenciaService.MAXIMO_CONTATOS; i++) {
			servico.adicionarContato(perfil, cuidador.getId(), contato(i));
		}

		List<Boolean> resultados = emParalelo(10,
				() -> servico.adicionarContato(perfil, cuidador.getId(), contato(10)));

		assertThat(resultados).containsOnlyOnce(true);
		assertThat(contatos.countByPerfilId(perfil)).isEqualTo(PerfilEmergenciaService.MAXIMO_CONTATOS);
	}

	@Test
	void criacoesSimultaneasDoMesmoPerfilViram409EmVezDe500() throws Exception {
		Usuario cidadao = usuario(Papel.CIDADAO);
		Usuario cuidadorA = usuario(Papel.CUIDADOR);
		Usuario cuidadorB = usuario(Papel.CUIDADOR);

		List<Boolean> resultados = emParalelo(2, new Callable<>() {
			private int vez;

			@Override
			public Object call() {
				Usuario cuidador = (vez++ % 2 == 0) ? cuidadorA : cuidadorB;
				return servico.criar(cuidador.getId(), perfilDe(cidadao));
			}
		});

		assertThat(resultados).containsOnlyOnce(true);
	}

	/**
	 * Dispara a mesma ação em várias threads ao mesmo tempo. Cada posição é {@code true} se a ação
	 * funcionou e {@code false} se recebeu um conflito; qualquer outro erro falha o teste.
	 */
	private static List<Boolean> emParalelo(int vezes, Callable<?> acao) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(vezes);
		CountDownLatch largada = new CountDownLatch(1);
		List<Future<Boolean>> futuros = new ArrayList<>();
		for (int i = 0; i < vezes; i++) {
			futuros.add(executor.submit(() -> {
				largada.await();
				try {
					acao.call();
					return true;
				}
				catch (ConflitoException ex) {
					return false;
				}
			}));
		}
		largada.countDown();
		List<Boolean> resultados = new ArrayList<>();
		for (Future<Boolean> futuro : futuros) {
			resultados.add(futuro.get());
		}
		executor.shutdown();
		return resultados;
	}

	private Usuario usuario(Papel papel) {
		return usuarios.save(new Usuario("Pessoa", "u-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", papel));
	}

	private static PerfilEmergenciaRequest perfilDe(Usuario cidadao) {
		return new PerfilEmergenciaRequest(cidadao.getEmail(), Mobilidade.CADEIRANTE, null, null, null);
	}

	private static ContatoEmergenciaRequest contato(int prioridade) {
		return new ContatoEmergenciaRequest("Contato " + prioridade, "11912345678", null, prioridade);
	}

}
