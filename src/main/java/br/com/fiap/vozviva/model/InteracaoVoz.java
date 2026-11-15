package br.com.fiap.vozviva.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Uma fala classificada. Serve de histórico para o usuário e de base para avaliar a IA. */
@Entity
@Table(name = "interacoes_voz")
public class InteracaoVoz {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false)
	private Long usuarioId;

	@Column(nullable = false, length = 500)
	private String texto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Intencao intencao;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AcaoApp acao;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Urgencia urgencia;

	@Column(precision = 4, scale = 3)
	private BigDecimal confianca;

	@Column(name = "latencia_ms", nullable = false)
	private Integer latenciaMs;

	@Column(name = "analisado_por", nullable = false, length = 20)
	private String analisadoPor;

	@Column(name = "criada_em", nullable = false, updatable = false)
	private Instant criadaEm;

	protected InteracaoVoz() {
		// exigido pelo JPA
	}

	public InteracaoVoz(Long usuarioId, String texto, Intencao intencao, Urgencia urgencia, double confianca,
			int latenciaMs, String analisadoPor, Instant criadaEm) {
		this.usuarioId = usuarioId;
		this.texto = texto;
		this.intencao = intencao;
		this.acao = intencao.acao();
		this.urgencia = urgencia;
		this.confianca = BigDecimal.valueOf(confianca).setScale(3, RoundingMode.HALF_UP);
		this.latenciaMs = latenciaMs;
		this.analisadoPor = analisadoPor;
		this.criadaEm = criadaEm;
	}

	public Long getId() {
		return id;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getTexto() {
		return texto;
	}

	public Intencao getIntencao() {
		return intencao;
	}

	public AcaoApp getAcao() {
		return acao;
	}

	public Urgencia getUrgencia() {
		return urgencia;
	}

	public BigDecimal getConfianca() {
		return confianca;
	}

	public Integer getLatenciaMs() {
		return latenciaMs;
	}

	public String getAnalisadoPor() {
		return analisadoPor;
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

}
