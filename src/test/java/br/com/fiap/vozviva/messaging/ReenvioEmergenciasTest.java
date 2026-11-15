package br.com.fiap.vozviva.messaging;

import br.com.fiap.vozviva.model.Emergencia;
import br.com.fiap.vozviva.model.Mobilidade;
import br.com.fiap.vozviva.model.Papel;
import br.com.fiap.vozviva.model.PerfilEmergencia;
import br.com.fiap.vozviva.model.StatusEmergencia;
import br.com.fiap.vozviva.model.Usuario;
import br.com.fiap.vozviva.repository.EmergenciaRepository;
import br.com.fiap.vozviva.repository.PerfilEmergenciaRepository;
import br.com.fiap.vozviva.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reenvio automático com limite (apontamento da revisão do PR #15): uma emergência que sempre cai na DLQ
 * não é reenviada para sempre. No perfil de teste o agendamento fica desligado, então o reenvio é chamado
 * direto, com o publicador simulado.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReenvioEmergenciasTest {

	@Autowired
	private EmergenciaRepository emergencias;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PerfilEmergenciaRepository perfis;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transacoes;

	private final PublicadorEmergencia publicador = mock(PublicadorEmergencia.class);

	@BeforeEach
	void soAEmergenciaDesteTesteFicaPendente() {
		jdbc.update("update emergencias set status = 'CANCELADA' where status = 'ACIONADA'");
	}

	@Test
	void reenviaEContaSoQuandoOBrokerRecebe() {
		Long id = emergenciaPendente(0);
		when(publicador.publicar(any())).thenReturn(true);

		reenviar();

		assertThat(emergencias.findById(id).orElseThrow().getReenvios()).isEqualTo(1);
		verify(publicador).publicar(new EmergenciaAcionadaEvento(id));
	}

	@Test
	void brokerForaDoArNaoGastaReenvio() {
		Long id = emergenciaPendente(0);
		when(publicador.publicar(any())).thenReturn(false);

		reenviar();

		assertThat(emergencias.findById(id).orElseThrow().getReenvios()).isZero();
	}

	@Test
	void esgotadosOsReenviosViraFalhaNoAvisoESaiDoReenvio() {
		Long id = emergenciaPendente(ReenvioEmergencias.MAXIMO_REENVIOS);

		reenviar();
		reenviar();

		Emergencia emergencia = emergencias.findById(id).orElseThrow();
		assertThat(emergencia.getStatus()).isEqualTo(StatusEmergencia.FALHA_NO_AVISO);
		assertThat(emergencia.getStatus().aberta()).isTrue();
		verify(publicador, never()).publicar(any());
	}

	private void reenviar() {
		new TransactionTemplate(transacoes).executeWithoutResult(
				status -> new ReenvioEmergencias(emergencias, publicador).reenviarPendentes());
	}

	private Long emergenciaPendente(int reenvios) {
		Usuario cidadao = usuarios.save(new Usuario("Maria", "c-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", Papel.CIDADAO));
		Usuario cuidador = usuarios.save(new Usuario("João", "k-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", Papel.CUIDADOR));
		PerfilEmergencia perfil = new PerfilEmergencia(cidadao, cuidador);
		perfil.atualizar(Mobilidade.CADEIRANTE, null, null, null);
		perfis.save(perfil);
		Instant haUmMinuto = Instant.now().minus(Duration.ofMinutes(1));
		Long id = emergencias.save(new Emergencia(perfil, null, null, null, haUmMinuto)).getId();
		jdbc.update("update emergencias set reenvios = ? where id = ?", reenvios, id);
		return id;
	}

}
