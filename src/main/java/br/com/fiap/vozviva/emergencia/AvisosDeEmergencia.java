package br.com.fiap.vozviva.emergencia;

import br.com.fiap.vozviva.perfil.ContatoEmergencia;
import br.com.fiap.vozviva.perfil.ContatoEmergenciaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Avisa os contatos de uma emergência, em ordem de prioridade, e registra cada aviso.
 * <p>
 * Idempotente: só processa emergências ACIONADAS. Se a mesma mensagem chegar de novo (reenvio ou
 * redelivery do broker), a emergência já estará NOTIFICADA e nada é enviado em dobro.
 */
@Service
public class AvisosDeEmergencia {

	private static final Logger log = LoggerFactory.getLogger(AvisosDeEmergencia.class);

	private final EmergenciaRepository emergencias;
	private final ContatoEmergenciaRepository contatos;
	private final AvisoEmergenciaRepository avisos;
	private final NotificadorContatos notificador;

	public AvisosDeEmergencia(EmergenciaRepository emergencias, ContatoEmergenciaRepository contatos,
			AvisoEmergenciaRepository avisos, NotificadorContatos notificador) {
		this.emergencias = emergencias;
		this.contatos = contatos;
		this.avisos = avisos;
		this.notificador = notificador;
	}

	@Transactional
	public void avisarContatos(Long emergenciaId) {
		Emergencia emergencia = emergencias.findParaProcessarById(emergenciaId).orElse(null);
		if (emergencia == null || emergencia.getStatus() != StatusEmergencia.ACIONADA) {
			log.info("Emergência {} já processada ou inexistente; nada a fazer", emergenciaId);
			return;
		}
		String mensagem = MensagemEmergencia.montar(emergencia);
		List<ContatoEmergencia> destinatarios = contatos
			.findByPerfilIdOrderByPrioridadeAscIdAsc(emergencia.getPerfil().getId());
		for (ContatoEmergencia contato : destinatarios) {
			notificador.notificar(contato.getNome(), contato.getTelefone(), mensagem);
			avisos.save(new AvisoEmergencia(emergencia, contato.getNome(), contato.getTelefone(), mensagem, Instant.now()));
		}
		if (destinatarios.isEmpty()) {
			log.warn("Emergência {} acionada por um perfil sem contatos cadastrados", emergenciaId);
		}
		emergencia.marcarNotificada(destinatarios.size(), Instant.now());
	}

}
