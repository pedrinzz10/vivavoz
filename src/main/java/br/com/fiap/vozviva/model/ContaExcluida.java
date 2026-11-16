package br.com.fiap.vozviva.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de que uma conta foi excluída: só o id, sem dados pessoais. Serve para recusar tokens que ainda não
 * expiraram dessa conta.
 */
@Entity
@Table(name = "contas_excluidas")
public class ContaExcluida {

	@Id
	@Column(name = "usuario_id")
	private Long usuarioId;

	@Column(name = "excluida_em", nullable = false)
	private Instant excluidaEm;

	protected ContaExcluida() {
		// exigido pelo JPA
	}

	public ContaExcluida(Long usuarioId, Instant excluidaEm) {
		this.usuarioId = usuarioId;
		this.excluidaEm = excluidaEm;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Instant getExcluidaEm() {
		return excluidaEm;
	}

}
