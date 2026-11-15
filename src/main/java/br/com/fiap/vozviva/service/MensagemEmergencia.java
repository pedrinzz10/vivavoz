package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.model.Emergencia;
import br.com.fiap.vozviva.model.Mobilidade;
import br.com.fiap.vozviva.model.PerfilEmergencia;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Texto curto enviado aos contatos: quem precisa de ajuda, quando, onde e o que considerar no socorro.
 */
public final class MensagemEmergencia {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(FUSO);

	private static final Map<Mobilidade, String> MOBILIDADE = Map.of(
			Mobilidade.CADEIRANTE, "usa cadeira de rodas",
			Mobilidade.ANDADOR, "usa andador",
			Mobilidade.BENGALA, "usa bengala",
			Mobilidade.DEFICIENCIA_VISUAL, "tem deficiência visual",
			Mobilidade.DEFICIENCIA_AUDITIVA, "tem deficiência auditiva");

	private MensagemEmergencia() {
	}

	static String montar(Emergencia emergencia) {
		PerfilEmergencia perfil = emergencia.getPerfil();
		StringBuilder texto = new StringBuilder()
			.append("VozViva: ").append(perfil.getCidadao().getNome())
			.append(" acionou o botão de emergência às ").append(HORA.format(emergencia.getAcionadaEm())).append('.');
		if (emergencia.temLocalizacao()) {
			texto.append(" Localização: https://www.google.com/maps?q=")
				.append(emergencia.getLatitude().toPlainString()).append(',')
				.append(emergencia.getLongitude().toPlainString()).append('.');
		}
		String mobilidade = MOBILIDADE.get(perfil.getMobilidade());
		if (mobilidade != null) {
			texto.append(" Atenção: ").append(mobilidade).append('.');
		}
		texto.append(" Ligue para ela ou para o SAMU (192).");
		return texto.toString();
	}

}
