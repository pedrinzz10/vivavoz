package br.com.fiap.vozviva.emergencia;

import br.com.fiap.vozviva.comum.Ordenacao;
import br.com.fiap.vozviva.comum.dto.PaginaResponse;
import br.com.fiap.vozviva.comum.erro.RecursoNaoEncontradoException;
import br.com.fiap.vozviva.comum.erro.RequisicaoInvalidaException;
import br.com.fiap.vozviva.emergencia.dto.AcionarEmergenciaRequest;
import br.com.fiap.vozviva.emergencia.dto.AvisoEmergenciaResponse;
import br.com.fiap.vozviva.emergencia.dto.EmergenciaResponse;
import br.com.fiap.vozviva.emergencia.mensageria.EmergenciaAcionadaEvento;
import br.com.fiap.vozviva.perfil.PerfilEmergencia;
import br.com.fiap.vozviva.perfil.PerfilEmergenciaRepository;
import br.com.fiap.vozviva.seguranca.Papel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class EmergenciaService {

	/** Um segundo toque dentro deste intervalo devolve a emergência já aberta, sem criar outra. */
	static final Duration JANELA_TOQUE_DUPLO = Duration.ofMinutes(2);

	private static final Set<String> ORDENACAO = Set.of("id", "status", "acionadaEm");

	private final EmergenciaRepository emergencias;
	private final AvisoEmergenciaRepository avisos;
	private final PerfilEmergenciaRepository perfis;
	private final ApplicationEventPublisher eventos;

	public EmergenciaService(EmergenciaRepository emergencias, AvisoEmergenciaRepository avisos,
			PerfilEmergenciaRepository perfis, ApplicationEventPublisher eventos) {
		this.emergencias = emergencias;
		this.avisos = avisos;
		this.perfis = perfis;
		this.eventos = eventos;
	}

	/**
	 * Grava a emergência e agenda a publicação do evento para depois do commit. A resposta não
	 * espera o aviso aos contatos, que acontece de forma assíncrona pelo RabbitMQ.
	 */
	@Transactional
	public Acionamento acionar(Long cidadaoId, AcionarEmergenciaRequest request) {
		if ((request.latitude() == null) != (request.longitude() == null)) {
			throw new RequisicaoInvalidaException("Informe latitude e longitude juntas, ou nenhuma delas.");
		}
		PerfilEmergencia perfil = perfis.findByCidadaoId(cidadaoId)
			.orElseThrow(() -> new RequisicaoInvalidaException(
					"Você ainda não tem perfil de emergência. Peça ao seu cuidador para cadastrar os contatos."));

		Instant agora = Instant.now();
		Optional<Emergencia> aberta = emergencias.findFirstByPerfilIdAndStatusInAndAcionadaEmAfterOrderByAcionadaEmDesc(
				perfil.getId(), EnumSet.of(StatusEmergencia.ACIONADA, StatusEmergencia.NOTIFICADA),
				agora.minus(JANELA_TOQUE_DUPLO));
		if (aberta.isPresent()) {
			return new Acionamento(EmergenciaMapper.paraResponse(aberta.get()), false);
		}

		Emergencia emergencia = emergencias.save(new Emergencia(perfil, decimal(request.latitude()),
				decimal(request.longitude()), request.textoOriginal(), agora));
		eventos.publishEvent(new EmergenciaAcionadaEvento(emergencia.getId()));
		return new Acionamento(EmergenciaMapper.paraResponse(emergencia), true);
	}

	@Transactional(readOnly = true)
	public EmergenciaResponse buscar(Long id, Long usuarioId) {
		return EmergenciaMapper.paraResponse(visivelPara(id, usuarioId));
	}

	/** Cidadão: as próprias emergências. Cuidador: as dos perfis que ele gerencia. */
	@Transactional(readOnly = true)
	public PaginaResponse<EmergenciaResponse> listar(Long usuarioId, Set<Papel> papeis, Pageable pageable) {
		Ordenacao.validar(pageable, ORDENACAO);
		var pagina = papeis.contains(Papel.CUIDADOR)
				? emergencias.findByPerfilCuidadorId(usuarioId, pageable)
				: emergencias.findByPerfilCidadaoId(usuarioId, pageable);
		return PaginaResponse.de(pagina, EmergenciaMapper::paraResponse);
	}

	@Transactional(readOnly = true)
	public List<AvisoEmergenciaResponse> avisos(Long id, Long usuarioId) {
		visivelPara(id, usuarioId);
		return avisos.findByEmergenciaIdOrderByIdAsc(id).stream().map(EmergenciaMapper::paraResponse).toList();
	}

	@Transactional
	public EmergenciaResponse cancelar(Long id, Long cidadaoId) {
		Emergencia emergencia = carregar(id);
		if (!emergencia.getPerfil().pertenceAo(cidadaoId)) {
			throw new AccessDeniedException("Só o próprio cidadão cancela a emergência.");
		}
		emergencia.cancelar(Instant.now());
		return EmergenciaMapper.paraResponse(emergencia);
	}

	@Transactional
	public EmergenciaResponse atender(Long id, Long cuidadorId) {
		Emergencia emergencia = carregar(id);
		if (!emergencia.getPerfil().ehGerenciadoPor(cuidadorId)) {
			throw new AccessDeniedException("Só o cuidador responsável marca a emergência como atendida.");
		}
		emergencia.atender(Instant.now());
		return EmergenciaMapper.paraResponse(emergencia);
	}

	private Emergencia carregar(Long id) {
		return emergencias.findWithPerfilById(id)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Emergência não encontrada."));
	}

	private Emergencia visivelPara(Long id, Long usuarioId) {
		Emergencia emergencia = carregar(id);
		PerfilEmergencia perfil = emergencia.getPerfil();
		if (!perfil.pertenceAo(usuarioId) && !perfil.ehGerenciadoPor(usuarioId)) {
			throw new AccessDeniedException("Emergência de outro cidadão.");
		}
		return emergencia;
	}

	private static BigDecimal decimal(Double valor) {
		return valor == null ? null : BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP);
	}

	/**
	 * @param nova {@code false} quando um toque duplo devolveu a emergência que já estava aberta
	 */
	public record Acionamento(EmergenciaResponse emergencia, boolean nova) {
	}

}
