package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.ai.BaseServicosPublicos.ServicoPublico;
import br.com.fiap.vozviva.model.TipoAtendimento;

import java.util.List;

/**
 * Orientação sobre um serviço público, tirada da base curada (nunca do modelo).
 *
 * @param passos          o que fazer, em passos curtos
 * @param link            página oficial do serviço, para o app abrir
 * @param telefone        central oficial gratuita, quando houver (ex.: 135 do INSS)
 * @param localPresencial onde a pessoa pode ser atendida perto de casa (ex.: CRAS); o app pode buscar o mais perto
 */
public record OrientacaoServicoResponse(String titulo, String resumo, List<String> passos, String link,
		String telefone, String orgao, TipoAtendimento localPresencial) {

	public static OrientacaoServicoResponse de(ServicoPublico servico) {
		return new OrientacaoServicoResponse(servico.titulo(), servico.resumo(), List.copyOf(servico.passos()),
				servico.link(), servico.telefone(), servico.orgao(), servico.localPresencial());
	}

}
