package br.com.fiap.vozviva.perfil;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Pessoa avisada quando o cidadão aciona a emergência. A prioridade define a ordem de contato.
 */
@Entity
@Table(name = "contatos_emergencia")
public class ContatoEmergencia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "perfil_id", nullable = false)
	private PerfilEmergencia perfil;

	@Column(nullable = false, length = 120)
	private String nome;

	@Column(nullable = false, length = 20)
	private String telefone;

	@Column(length = 60)
	private String parentesco;

	@Column(nullable = false)
	private Integer prioridade;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected ContatoEmergencia() {
		// exigido pelo JPA
	}

	public ContatoEmergencia(PerfilEmergencia perfil) {
		this.perfil = perfil;
	}

	public void atualizar(String nome, String telefone, String parentesco, int prioridade) {
		this.nome = nome;
		this.telefone = telefone;
		this.parentesco = parentesco;
		this.prioridade = prioridade;
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

	public Long getId() {
		return id;
	}

	public PerfilEmergencia getPerfil() {
		return perfil;
	}

	public String getNome() {
		return nome;
	}

	public String getTelefone() {
		return telefone;
	}

	public String getParentesco() {
		return parentesco;
	}

	public Integer getPrioridade() {
		return prioridade;
	}

}
