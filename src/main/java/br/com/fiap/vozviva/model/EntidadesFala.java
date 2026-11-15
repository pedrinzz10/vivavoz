package br.com.fiap.vozviva.model;


/**
 * O que foi extraído da fala para o app já abrir a tela preenchida. Campos que não se aplicam à
 * intenção ficam {@code null}.
 *
 * @param tipoAtendimento     o que procurar perto do cidadão (BUSCAR_ATENDIMENTO)
 * @param exigeAcessibilidade a pessoa mencionou cadeira de rodas, andador, rampa ou acessibilidade
 * @param problemaRua         o problema relatado (ZELADORIA_REPORT)
 * @param impedeCadeirante    o problema impede a passagem de cadeira de rodas ou andador
 * @param referenciaLocal     referência de lugar dita na fala (ex.: "na frente da padaria")
 */
public record EntidadesFala(
		TipoAtendimento tipoAtendimento,
		Boolean exigeAcessibilidade,
		ProblemaRua problemaRua,
		Boolean impedeCadeirante,
		String referenciaLocal) {

	public static final EntidadesFala NENHUMA = new EntidadesFala(null, null, null, null, null);

	/** Completa os campos vazios com os de {@code outras} (as regras completam o que o modelo não trouxe). */
	public EntidadesFala completarCom(EntidadesFala outras) {
		if (outras == null) {
			return this;
		}
		return new EntidadesFala(
				tipoAtendimento != null ? tipoAtendimento : outras.tipoAtendimento,
				exigeAcessibilidade != null ? exigeAcessibilidade : outras.exigeAcessibilidade,
				problemaRua != null ? problemaRua : outras.problemaRua,
				impedeCadeirante != null ? impedeCadeirante : outras.impedeCadeirante,
				referenciaLocal != null ? referenciaLocal : outras.referenciaLocal);
	}

}
