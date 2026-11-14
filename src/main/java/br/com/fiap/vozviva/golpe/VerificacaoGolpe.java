package br.com.fiap.vozviva.golpe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Estatística anonimizada de uma verificação. De propósito, não guarda o texto da mensagem nem quem
 * perguntou (LGPD): só o que é preciso para saber quais golpes estão circulando.
 */
@Entity
@Table(name = "verificacoes_golpe")
public class VerificacaoGolpe {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_golpe", nullable = false, length = 30)
	private TipoGolpe tipoGolpe;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ClassificacaoGolpe classificacao;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CanalMensagem canal;

	@Column(name = "analisado_por", nullable = false, length = 20)
	private String analisadoPor;

	@Column(name = "criada_em", nullable = false, updatable = false)
	private Instant criadaEm;

	protected VerificacaoGolpe() {
		// exigido pelo JPA
	}

	VerificacaoGolpe(TipoGolpe tipoGolpe, ClassificacaoGolpe classificacao, CanalMensagem canal, String analisadoPor,
			Instant criadaEm) {
		this.tipoGolpe = tipoGolpe;
		this.classificacao = classificacao;
		this.canal = canal;
		this.analisadoPor = analisadoPor;
		this.criadaEm = criadaEm;
	}

	public TipoGolpe getTipoGolpe() {
		return tipoGolpe;
	}

	public ClassificacaoGolpe getClassificacao() {
		return classificacao;
	}

}
