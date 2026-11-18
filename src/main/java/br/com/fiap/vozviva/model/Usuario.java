package br.com.fiap.vozviva.model;

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
 * Pessoa que usa o ecossistema: o cidadão (idoso ou PcD), o cuidador (familiar) ou o gestor da prefeitura, que
 * atende a zeladoria na API .NET. A senha nunca é guardada, só o hash (BCrypt).
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

	/** Órgão atendido; só existe para o gestor. */
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private AreaOrgao orgao;

	@Column(nullable = false)
	private boolean ativo = true;

	/** Aceite dos termos de uso e privacidade no cadastro (prova do consentimento, LGPD). */
	@Column(nullable = false)
	private boolean consentimento;

	@Column(name = "consentimento_em")
	private Instant consentimentoEm;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected Usuario() {
		// exigido pelo JPA
	}

	public Usuario(String nome, String email, String senhaHash, Papel papel) {
		if (papel == Papel.GESTOR) {
			throw new IllegalArgumentException("Gestor precisa de órgão: use Usuario.gestor(...)");
		}
		this.nome = nome;
		this.email = email;
		this.senhaHash = senhaHash;
		this.papel = papel;
	}

	public static Usuario gestor(String nome, String email, String senhaHash, AreaOrgao orgao) {
		if (orgao == null) {
			throw new IllegalArgumentException("Gestor precisa de órgão");
		}
		Usuario gestor = new Usuario();
		gestor.nome = nome;
		gestor.email = email;
		gestor.senhaHash = senhaHash;
		gestor.papel = Papel.GESTOR;
		gestor.orgao = orgao;
		return gestor;
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

	public void alterarNome(String novoNome) {
		this.nome = novoNome;
	}

	/** Recebe o hash (BCrypt) da senha nova, nunca a senha em texto. */
	public void alterarSenha(String novoHash) {
		this.senhaHash = novoHash;
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

	public AreaOrgao getOrgao() {
		return orgao;
	}

	public void registrarConsentimento(Instant quando) {
		this.consentimento = true;
		this.consentimentoEm = quando;
	}

	public boolean isConsentimento() {
		return consentimento;
	}

	public Instant getConsentimentoEm() {
		return consentimentoEm;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
