package br.com.fiap.vozviva.emergencia;

import br.com.fiap.vozviva.comum.erro.ConflitoException;
import br.com.fiap.vozviva.perfil.PerfilEmergencia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Um acionamento do botão de emergência. As transições de status ficam aqui, para que nenhuma
 * parte do sistema consiga, por exemplo, cancelar uma emergência já atendida.
 */
@Entity
@Table(name = "emergencias")
public class Emergencia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "perfil_id", nullable = false)
	private PerfilEmergencia perfil;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatusEmergencia status;

	@Column(precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(precision = 9, scale = 6)
	private BigDecimal longitude;

	@Column(name = "texto_original", length = 300)
	private String textoOriginal;

	@Column(name = "contatos_notificados", nullable = false)
	private Integer contatosNotificados = 0;

	@Column(name = "acionada_em", nullable = false, updatable = false)
	private Instant acionadaEm;

	@Column(name = "notificada_em")
	private Instant notificadaEm;

	@Column(name = "encerrada_em")
	private Instant encerradaEm;

	protected Emergencia() {
		// exigido pelo JPA
	}

	public Emergencia(PerfilEmergencia perfil, BigDecimal latitude, BigDecimal longitude, String textoOriginal,
			Instant acionadaEm) {
		this.perfil = perfil;
		this.latitude = latitude;
		this.longitude = longitude;
		this.textoOriginal = textoOriginal;
		this.acionadaEm = acionadaEm;
		this.status = StatusEmergencia.ACIONADA;
	}

	/** Chamado pelo consumidor da fila depois de avisar os contatos. */
	public void marcarNotificada(int contatos, Instant quando) {
		if (status != StatusEmergencia.ACIONADA) {
			throw new ConflitoException("Só uma emergência acionada pode ser marcada como notificada.");
		}
		this.status = StatusEmergencia.NOTIFICADA;
		this.contatosNotificados = contatos;
		this.notificadaEm = quando;
	}

	public void cancelar(Instant quando) {
		encerrar(StatusEmergencia.CANCELADA, quando);
	}

	public void atender(Instant quando) {
		encerrar(StatusEmergencia.ATENDIDA, quando);
	}

	private void encerrar(StatusEmergencia novo, Instant quando) {
		if (!status.aberta()) {
			throw new ConflitoException("Esta emergência já foi encerrada.");
		}
		this.status = novo;
		this.encerradaEm = quando;
	}

	public boolean temLocalizacao() {
		return latitude != null && longitude != null;
	}

	public Long getId() {
		return id;
	}

	public PerfilEmergencia getPerfil() {
		return perfil;
	}

	public StatusEmergencia getStatus() {
		return status;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public String getTextoOriginal() {
		return textoOriginal;
	}

	public Integer getContatosNotificados() {
		return contatosNotificados;
	}

	public Instant getAcionadaEm() {
		return acionadaEm;
	}

	public Instant getNotificadaEm() {
		return notificadaEm;
	}

	public Instant getEncerradaEm() {
		return encerradaEm;
	}

}
