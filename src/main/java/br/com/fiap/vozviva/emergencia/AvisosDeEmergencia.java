package br.com.fiap.vozviva.emergencia;

import br.com.fiap.vozviva.perfil.ContatoEmergencia;
import br.com.fiap.vozviva.perfil.ContatoEmergenciaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Avisa os contatos de uma emergência, em ordem de prioridade, e registra cada aviso.
 * <p>
 * Idempotente: só processa emergências ACIONADAS, e a linha da emergência fica travada durante o
 * processamento (dois consumidores nunca avisam ao mesmo tempo). Cada aviso é gravado na hora, em
 * transação própria: se outro contato falhar e a mensagem for tentada de novo, quem já foi avisado
 * não recebe de novo. A falha de um contato também não impede o aviso aos demais.
 * <p>
 * Garantia: pelo menos uma vez por contato. Só uma queda exatamente entre o envio e a gravação do
 * aviso pode repetir aquele contato.
 */
@Service
public class AvisosDeEmergencia {

	private static final Logger log = LoggerFactory.getLogger(AvisosDeEmergencia.class);

	private final EmergenciaRepository emergencias;
	private final ContatoEmergenciaRepository contatos;
	private final AvisoEmergenciaRepository avisos;
	private final NotificadorContatos notificador;
	private final TransactionTemplate transacaoPropria;

	public AvisosDeEmergencia(EmergenciaRepository emergencias, ContatoEmergenciaRepository contatos,
			AvisoEmergenciaRepository avisos, NotificadorContatos notificador, PlatformTransactionManager transacoes) {
		this.emergencias = emergencias;
		this.contatos = contatos;
		this.avisos = avisos;
		this.notificador = notificador;
		this.transacaoPropria = new TransactionTemplate(transacoes);
		this.transacaoPropria.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
		Set<String> jaAvisados = avisos.findByEmergenciaIdOrderByIdAsc(emergenciaId).stream()
			.map(a -> chave(a.getContatoNome(), a.getTelefone()))
			.collect(Collectors.toSet());

		int avisados = jaAvisados.size();
		int falhas = 0;
		for (ContatoEmergencia contato : destinatarios) {
			if (jaAvisados.contains(chave(contato.getNome(), contato.getTelefone()))) {
				continue;
			}
			try {
				notificador.notificar(contato.getNome(), contato.getTelefone(), mensagem);
			}
			catch (RuntimeException ex) {
				falhas++;
				log.warn("Falha ao avisar o contato {} da emergência {}: {}", contato.getId(), emergenciaId,
						ex.getClass().getSimpleName());
				continue;
			}
			transacaoPropria.executeWithoutResult(status -> avisos.save(new AvisoEmergencia(emergencia,
					contato.getNome(), contato.getTelefone(), mensagem, Instant.now())));
			avisados++;
		}
		if (falhas > 0) {
			// A emergência continua ACIONADA e a mensagem volta a ser tentada só para quem falhou.
			throw new IllegalStateException(falhas + " contato(s) da emergência " + emergenciaId + " não foram avisados");
		}
		if (destinatarios.isEmpty()) {
			log.warn("Emergência {} acionada por um perfil sem contatos cadastrados", emergenciaId);
		}
		emergencia.marcarNotificada(avisados, Instant.now());
	}

	private static String chave(String nome, String telefone) {
		return nome + "|" + telefone;
	}

}
