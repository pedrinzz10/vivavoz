package br.com.fiap.vozviva.emergencia;

import br.com.fiap.vozviva.comum.erro.ConflitoException;
import br.com.fiap.vozviva.emergencia.dto.AcionarEmergenciaRequest;
import br.com.fiap.vozviva.perfil.Mobilidade;
import br.com.fiap.vozviva.perfil.PerfilEmergencia;
import br.com.fiap.vozviva.perfil.PerfilEmergenciaRepository;
import br.com.fiap.vozviva.seguranca.Papel;
import br.com.fiap.vozviva.usuario.Usuario;
import br.com.fiap.vozviva.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Botão de emergência sob requisições simultâneas (apontamentos da revisão do PR #15).
 */
@SpringBootTest
@ActiveProfiles("test")
class EmergenciaConcorrenciaTest {

	@Autowired
	private EmergenciaService servico;

	@Autowired
	private EmergenciaRepository emergencias;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PerfilEmergenciaRepository perfis;

	@Test
	void toquesSimultaneosAbremUmaSoEmergencia() {
		PerfilEmergencia perfil = perfil();
		Long cidadao = perfil.getCidadao().getId();

		List<Object> resultados = emParalelo(10, () -> servico.acionar(cidadao, new AcionarEmergenciaRequest(null, null, null)));

		assertThat(resultados).extracting(r -> ((EmergenciaService.Acionamento) r).nova()).containsOnlyOnce(true);
		assertThat(resultados).extracting(r -> ((EmergenciaService.Acionamento) r).emergencia().id()).containsOnly(
				((EmergenciaService.Acionamento) resultados.getFirst()).emergencia().id());
		assertThat(emergencias.findAll()).filteredOn(e -> e.getPerfil().getId().equals(perfil.getId())).hasSize(1);
	}

	@Test
	void cancelarEAtenderAoMesmoTempoSoUmVence() {
		PerfilEmergencia perfil = perfil();
		Long cidadao = perfil.getCidadao().getId();
		Long cuidador = perfil.getCuidador().getId();
		Long id = servico.acionar(cidadao, new AcionarEmergenciaRequest(null, null, null)).emergencia().id();

		List<Object> resultados = emParalelo(List.of(() -> servico.cancelar(id, cidadao), () -> servico.atender(id, cuidador)));

		assertThat(resultados).filteredOn(r -> r instanceof ConflitoException).hasSize(1);
		StatusEmergencia finalStatus = emergencias.findById(id).orElseThrow().getStatus();
		assertThat(finalStatus).isIn(StatusEmergencia.CANCELADA, StatusEmergencia.ATENDIDA);
	}

	private static List<Object> emParalelo(int vezes, Callable<?> acao) {
		return emParalelo(Collections.nCopies(vezes, acao));
	}

	/**
	 * Dispara as ações ao mesmo tempo. Cada posição traz o retorno da ação ou a {@link ConflitoException}
	 * recebida; qualquer outro erro falha o teste.
	 */
	private static List<Object> emParalelo(List<Callable<?>> acoes) {
		ExecutorService executor = Executors.newFixedThreadPool(acoes.size());
		try {
			CountDownLatch largada = new CountDownLatch(1);
			List<Future<Object>> futuros = new ArrayList<>();
			for (Callable<?> acao : acoes) {
				futuros.add(executor.submit(() -> {
					largada.await();
					try {
						return acao.call();
					}
					catch (ConflitoException ex) {
						return ex;
					}
				}));
			}
			largada.countDown();
			List<Object> resultados = new ArrayList<>();
			for (Future<Object> futuro : futuros) {
				resultados.add(futuro.get());
			}
			return resultados;
		}
		catch (Exception ex) {
			throw new AssertionError("Falha inesperada em uma das requisições simultâneas", ex);
		}
		finally {
			executor.shutdown();
		}
	}

	private PerfilEmergencia perfil() {
		Usuario cidadao = usuarios.save(new Usuario("Maria", "c-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", Papel.CIDADAO));
		Usuario cuidador = usuarios.save(new Usuario("João", "k-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", Papel.CUIDADOR));
		PerfilEmergencia perfil = new PerfilEmergencia(cidadao, cuidador);
		perfil.atualizar(Mobilidade.CADEIRANTE, null, null, null);
		return perfis.save(perfil);
	}

}
