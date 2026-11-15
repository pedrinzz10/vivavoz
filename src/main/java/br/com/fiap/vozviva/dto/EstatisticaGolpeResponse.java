package br.com.fiap.vozviva.dto;

import br.com.fiap.vozviva.model.TipoGolpe;

public record EstatisticaGolpeResponse(TipoGolpe tipoGolpe, long total) {
}
