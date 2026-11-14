package br.com.fiap.vozviva.emergencia;

import br.com.fiap.vozviva.emergencia.dto.AvisoEmergenciaResponse;
import br.com.fiap.vozviva.emergencia.dto.EmergenciaResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

final class EmergenciaMapper {

	private static final Map<StatusEmergencia, String> MENSAGENS = Map.of(
			StatusEmergencia.ACIONADA, "Pedido de ajuda enviado. Estamos avisando seus contatos.",
			StatusEmergencia.NOTIFICADA, "Seus contatos foram avisados. Fique onde está, se puder.",
			StatusEmergencia.ATENDIDA, "Seu pedido de ajuda foi atendido.",
			StatusEmergencia.CANCELADA, "Pedido de ajuda cancelado.");

	private EmergenciaMapper() {
	}

	static EmergenciaResponse paraResponse(Emergencia emergencia) {
		return new EmergenciaResponse(
				emergencia.getId(),
				emergencia.getPerfil().getId(),
				emergencia.getPerfil().getCidadao().getNome(),
				emergencia.getStatus(),
				numero(emergencia.getLatitude()),
				numero(emergencia.getLongitude()),
				emergencia.getTextoOriginal(),
				emergencia.getContatosNotificados(),
				texto(emergencia.getAcionadaEm()),
				texto(emergencia.getNotificadaEm()),
				texto(emergencia.getEncerradaEm()),
				MENSAGENS.get(emergencia.getStatus()));
	}

	static AvisoEmergenciaResponse paraResponse(AvisoEmergencia aviso) {
		return new AvisoEmergenciaResponse(aviso.getId(), aviso.getContatoNome(), aviso.getTelefone(),
				aviso.getMensagem(), texto(aviso.getEnviadoEm()));
	}

	private static Double numero(BigDecimal valor) {
		return valor == null ? null : valor.doubleValue();
	}

	private static String texto(Instant instante) {
		return instante == null ? null : instante.toString();
	}

}
