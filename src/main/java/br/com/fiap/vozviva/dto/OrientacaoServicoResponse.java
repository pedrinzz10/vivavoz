package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.ai.BaseServicosPublicos.ServicoPublico;
import br.com.fiap.vozviva.model.TipoAtendimento;

import java.util.List;

/**
 * Orientação sobre um serviço público, tirada da base curada (nunca do modelo).
 *
 * @param passos          o que fazer, em passos curtos
 * @param link            página oficial do serviço, para o app abrir; {@code null} numa resposta aberta (sem serviço da
 *                        base)
 * @param telefone        central oficial gratuita, quando houver (ex.: 135 do INSS)
 * @param localPresencial onde a pessoa pode ser atendida perto de casa (ex.: CRAS); o app pode buscar o mais perto
 */
public record OrientacaoServicoResponse(String titulo, String resumo, List<String> passos, String link,
		String telefone, String orgao, TipoAtendimento localPresencial) {

	static final String TITULO_RESPOSTA_ABERTA = "Resposta do assistente";
	static final String FONTE_RESPOSTA_ABERTA = "Assistente VozViva. Confirme nos canais oficiais";

	public static OrientacaoServicoResponse de(ServicoPublico servico) {
		return de(servico, servico.resumo());
	}

	/** Orientação da base com a resposta à pergunta da pessoa no lugar do resumo; passos, link e telefone da base. */
	public static OrientacaoServicoResponse de(ServicoPublico servico, String resposta) {
		return new OrientacaoServicoResponse(servico.titulo(), resposta, List.copyOf(servico.passos()), servico.link(),
				servico.telefone(), servico.orgao(), servico.localPresencial());
	}

	/** Resposta a uma dúvida que a base não cobre: só o texto, sem link nem telefone (nada que o modelo possa inventar). */
	public static OrientacaoServicoResponse aberta(String resposta) {
		return new OrientacaoServicoResponse(TITULO_RESPOSTA_ABERTA, resposta, List.of(), null, null,
				FONTE_RESPOSTA_ABERTA, null);
	}

}
