package br.com.fiap.vozviva.perfil;

import br.com.fiap.vozviva.usuario.Usuario;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Informações vitais do cidadão para emergências. Cada cidadão tem no máximo um perfil,
 * mantido pelo cuidador que o criou.
 */
@Entity
@Table(name = "perfis_emergencia")
public class PerfilEmergencia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cidadao_id", nullable = false, unique = true)
	private Usuario cidadao;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cuidador_id", nullable = false)
	private Usuario cuidador;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Mobilidade mobilidade;

	@Column(name = "condicoes_saude", length = 500)
	private String condicoesSaude;

	@Column(length = 500)
	private String medicamentos;

	@Column(length = 500)
	private String observacoes;

	@OneToMany(mappedBy = "perfil", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ContatoEmergencia> contatos = new ArrayList<>();

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected PerfilEmergencia() {
		// exigido pelo JPA
	}

	public PerfilEmergencia(Usuario cidadao, Usuario cuidador) {
		this.cidadao = cidadao;
		this.cuidador = cuidador;
	}

	public void atualizar(Mobilidade mobilidade, String condicoesSaude, String medicamentos, String observacoes) {
		this.mobilidade = mobilidade;
		this.condicoesSaude = condicoesSaude;
		this.medicamentos = medicamentos;
		this.observacoes = observacoes;
	}

	public boolean ehGerenciadoPor(Long usuarioId) {
		return cuidador.getId().equals(usuarioId);
	}

	public boolean pertenceAo(Long usuarioId) {
		return cidadao.getId().equals(usuarioId);
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

	public Usuario getCidadao() {
		return cidadao;
	}

	public Usuario getCuidador() {
		return cuidador;
	}

	public Mobilidade getMobilidade() {
		return mobilidade;
	}

	public String getCondicoesSaude() {
		return condicoesSaude;
	}

	public String getMedicamentos() {
		return medicamentos;
	}

	public String getObservacoes() {
		return observacoes;
	}

	public List<ContatoEmergencia> getContatos() {
		return contatos;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

}
