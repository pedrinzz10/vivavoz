package br.com.fiap.vozviva.golpe.dto;

import br.com.fiap.vozviva.golpe.TipoGolpe;

public record EstatisticaGolpeResponse(TipoGolpe tipoGolpe, long total) {
}
