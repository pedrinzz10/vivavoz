package br.com.fiap.vozviva.emergencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de cada aviso enviado a um contato. Guarda nome e telefone do momento do envio,
 * para o histórico não mudar se o contato for editado depois.
 */
@Entity
@Table(name = "avisos_emergencia")
public class AvisoEmergencia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "emergencia_id", nullable = false)
	private Emergencia emergencia;

	@Column(name = "contato_nome", nullable = false, length = 120)
	private String contatoNome;

	@Column(nullable = false, length = 20)
	private String telefone;

	@Column(nullable = false, length = 500)
	private String mensagem;

	@Column(name = "enviado_em", nullable = false)
	private Instant enviadoEm;

	protected AvisoEmergencia() {
		// exigido pelo JPA
	}

	public AvisoEmergencia(Emergencia emergencia, String contatoNome, String telefone, String mensagem,
			Instant enviadoEm) {
		this.emergencia = emergencia;
		this.contatoNome = contatoNome;
		this.telefone = telefone;
		this.mensagem = mensagem;
		this.enviadoEm = enviadoEm;
	}

	public Long getId() {
		return id;
	}

	public String getContatoNome() {
		return contatoNome;
	}

	public String getTelefone() {
		return telefone;
	}

	public String getMensagem() {
		return mensagem;
	}

	public Instant getEnviadoEm() {
		return enviadoEm;
	}

}
