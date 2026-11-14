package br.com.fiap.vozviva.usuario;

import br.com.fiap.vozviva.seguranca.Papel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Pessoa que usa o ecossistema: o cidadão (idoso ou PcD) ou o cuidador (familiar).
 * A senha nunca é guardada, só o hash (BCrypt).
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nome;

	@Column(nullable = false, length = 160, unique = true)
	private String email;

	@Column(name = "senha_hash", nullable = false, length = 100)
	private String senhaHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Papel papel;

	@Column(nullable = false)
	private boolean ativo = true;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected Usuario() {
		// exigido pelo JPA
	}

	public Usuario(String nome, String email, String senhaHash, Papel papel) {
		this.nome = nome;
		this.email = email;
		this.senhaHash = senhaHash;
		this.papel = papel;
	}

	@PrePersist
	void aoCriar() {
		criadoEm = Instant.now();
		atualizadoEm = criadoEm;
	}

	@PreUpdate
	void aoAtualizar() {
		atualizadoEm = Instant.now();
	}

	public void desativar() {
		ativo = false;
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public Papel getPapel() {
		return papel;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
