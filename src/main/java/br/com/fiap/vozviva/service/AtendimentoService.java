package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.dto.AtendimentoProximoResponse;
import br.com.fiap.vozviva.dto.AtendimentosProximosResponse;
import br.com.fiap.vozviva.dto.PaginaResponse;
import br.com.fiap.vozviva.dto.PontoAtendimentoResponse;
import br.com.fiap.vozviva.exception.RecursoNaoEncontradoException;
import br.com.fiap.vozviva.model.Acessibilidade;
import br.com.fiap.vozviva.model.PontoAtendimento;
import br.com.fiap.vozviva.model.TipoAtendimento;
import br.com.fiap.vozviva.repository.PontoAtendimentoRepository;
import br.com.fiap.vozviva.util.Geo;
import br.com.fiap.vozviva.util.LinksRota;
import br.com.fiap.vozviva.util.Ordenacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Ir até lá": encontra o atendimento mais próximo do usuário, de preferência acessível.
 * A localização do usuário só é usada no cálculo e nunca é gravada (LGPD).
 */
@Service
@Transactional(readOnly = true)
public class AtendimentoService {

	static final String ORIENTACAO_192 = "Em caso de urgência, ligue 192 (SAMU) em vez de ir sozinho.";
	static final String OBSERVACAO_ROTA = "O local foi escolhido pela acessibilidade, mas a rota do app de navegação "
			+ "pode ter calçadas ou travessias sem acessibilidade.";
	static final String CREDITO_OSM = "Dados © colaboradores do OpenStreetMap (licença ODbL)";

	private static final Set<String> ORDENACOES = Set.of("nome", "tipo");
	private static final Locale PT_BR = Locale.of("pt", "BR");

	private final PontoAtendimentoRepository pontos;

	public AtendimentoService(PontoAtendimentoRepository pontos) {
		this.pontos = pontos;
	}

	public AtendimentosProximosResponse proximos(TipoAtendimento tipo, double latitude, double longitude,
			boolean acessivel, int raioMetros, int limite) {
		Geo.Area area = Geo.areaAoRedor(latitude, longitude, raioMetros);
		List<Candidato> noRaio = pontos.findByTipoAndLatitudeBetweenAndLongitudeBetween(tipo,
					BigDecimal.valueOf(area.latitudeMinima()), BigDecimal.valueOf(area.latitudeMaxima()),
					BigDecimal.valueOf(area.longitudeMinima()), BigDecimal.valueOf(area.longitudeMaxima()))
			.stream()
			.map(p -> new Candidato(p, Geo.distanciaMetros(latitude, longitude, p.getLatitude(), p.getLongitude())))
			.filter(c -> c.distancia() <= raioMetros)
			.sorted(Comparator.comparingDouble(Candidato::distancia).thenComparing(c -> c.ponto().getId()))
			.toList();

		List<Candidato> escolhidos = noRaio;
		boolean semAcessivel = false;
		if (acessivel) {
			escolhidos = noRaio.stream().filter(c -> c.ponto().getAcessibilidade().confirmada()).toList();
			if (escolhidos.isEmpty()) {
				// Nunca apresenta como acessível: devolve os sem informação, sinalizados, e orienta a ligar antes.
				escolhidos = noRaio.stream()
					.filter(c -> c.ponto().getAcessibilidade() == Acessibilidade.DESCONHECIDA)
					.toList();
				semAcessivel = !escolhidos.isEmpty();
			}
		}
		List<AtendimentoProximoResponse> atendimentos = escolhidos.stream().limit(limite).map(AtendimentoService::paraItem).toList();

		return new AtendimentosProximosResponse(tipo, atendimentos, semAcessivel,
				mensagemFalada(tipo, atendimentos, acessivel, semAcessivel, raioMetros),
				tipo.urgencia() ? ORIENTACAO_192 : null,
				tipo.urgencia() ? "192" : null,
				acessivel ? OBSERVACAO_ROTA : null,
				CREDITO_OSM);
	}

	public PaginaResponse<PontoAtendimentoResponse> listar(TipoAtendimento tipo, Pageable pageable) {
		Ordenacao.validar(pageable, ORDENACOES);
		Page<PontoAtendimento> pagina = tipo == null ? pontos.findAll(pageable) : pontos.findByTipo(tipo, pageable);
		return PaginaResponse.de(pagina, AtendimentoService::paraDetalhe);
	}

	public PontoAtendimentoResponse buscar(Long id) {
		return pontos.findById(id)
			.map(AtendimentoService::paraDetalhe)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Local de atendimento não encontrado."));
	}

	static String mensagemFalada(TipoAtendimento tipo, List<AtendimentoProximoResponse> atendimentos,
			boolean acessivel, boolean semAcessivel, int raioMetros) {
		String urgencia = tipo.urgencia() ? " Se for urgência, ligue 192." : "";
		if (atendimentos.isEmpty()) {
			return "Não encontrei " + tipo.nomeFalado() + (acessivel ? " com acessibilidade" : "") + " a até "
					+ distanciaFalada(raioMetros) + " de você." + urgencia;
		}
		AtendimentoProximoResponse maisPerto = atendimentos.getFirst();
		String distancia = distanciaFalada(maisPerto.distanciaMetros());
		String frase;
		if (semAcessivel) {
			frase = "Não achei " + tipo.nomeFalado() + " com acessibilidade confirmada. " + tipo.comArtigo()
					+ " mais perto fica a " + distancia + "; ligue antes para confirmar.";
		}
		else if (acessivel) {
			String comAcesso = maisPerto.acessibilidade() == Acessibilidade.SIM ? " com acessibilidade" : " com acessibilidade parcial";
			frase = tipo.comArtigo() + " mais perto" + comAcesso + " fica a " + distancia + ".";
		}
		else {
			frase = tipo.comArtigo() + " mais perto fica a " + distancia + ".";
		}
		return frase + urgencia + " Quer que eu abra o caminho?";
	}

	/** "350 metros", "1,2 km". */
	static String distanciaFalada(long metros) {
		long arredondado = Math.max(10, Math.round(metros / 10.0) * 10);
		if (arredondado < 1000) {
			return arredondado + " metros";
		}
		return new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(PT_BR)).format(metros / 1000.0) + " km";
	}

	static String aviso(Acessibilidade acessibilidade) {
		return switch (acessibilidade) {
			case SIM -> null;
			case PARCIAL -> "Acessibilidade parcial: pode haver degraus ou trechos difíceis.";
			case NAO -> "Sem acessibilidade para cadeira de rodas, segundo o OpenStreetMap.";
			case DESCONHECIDA -> "Acessibilidade não informada. Ligue antes para confirmar.";
		};
	}

	private static AtendimentoProximoResponse paraItem(Candidato c) {
		PontoAtendimento p = c.ponto();
		return new AtendimentoProximoResponse(p.getId(), p.getNome(), p.getTipo(), p.getEndereco(), p.getTelefone(),
				p.getLatitude(), p.getLongitude(), Math.round(c.distancia()), p.getAcessibilidade(),
				p.getBanheiroAdaptado(), p.getFunciona24h(), aviso(p.getAcessibilidade()),
				LinksRota.para(p.getLatitude(), p.getLongitude()));
	}

	private static PontoAtendimentoResponse paraDetalhe(PontoAtendimento p) {
		return new PontoAtendimentoResponse(p.getId(), p.getNome(), p.getTipo(), p.getEndereco(), p.getTelefone(),
				p.getLatitude(), p.getLongitude(), p.getAcessibilidade(), p.getBanheiroAdaptado(), p.getFunciona24h(),
				p.getFonte(), p.getAtualizadoEm(), LinksRota.para(p.getLatitude(), p.getLongitude()));
	}

	private record Candidato(PontoAtendimento ponto, double distancia) {
	}

}
