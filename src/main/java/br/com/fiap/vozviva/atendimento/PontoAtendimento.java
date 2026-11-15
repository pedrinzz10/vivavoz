package br.com.fiap.vozviva.atendimento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Local de atendimento importado do OpenStreetMap. Somente leitura pela API: a carga vem da migração
 * gerada por {@code ./gradlew importarOsm}.
 */
@Entity
@Immutable
@Table(name = "pontos_atendimento")
public class PontoAtendimento {

	@Id
	private Long id;

	@Column(name = "osm_id", nullable = false, length = 30)
	private String osmId;

	@Column(nullable = false, length = 200)
	private String nome;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoAtendimento tipo;

	@Column(length = 300)
	private String endereco;

	@Column(length = 40)
	private String telefone;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal longitude;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Acessibilidade acessibilidade;

	@Column(name = "banheiro_adaptado")
	private Boolean banheiroAdaptado;

	@Column(name = "funciona_24h")
	private Boolean funciona24h;

	@Column(nullable = false, length = 60)
	private String fonte;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected PontoAtendimento() {
		// exigido pelo JPA
	}

	public Long getId() {
		return id;
	}

	public String getOsmId() {
		return osmId;
	}

	public String getNome() {
		return nome;
	}

	public TipoAtendimento getTipo() {
		return tipo;
	}

	public String getEndereco() {
		return endereco;
	}

	public String getTelefone() {
		return telefone;
	}

	public double getLatitude() {
		return latitude.doubleValue();
	}

	public double getLongitude() {
		return longitude.doubleValue();
	}

	public Acessibilidade getAcessibilidade() {
		return acessibilidade;
	}

	public Boolean getBanheiroAdaptado() {
		return banheiroAdaptado;
	}

	public Boolean getFunciona24h() {
		return funciona24h;
	}

	public String getFonte() {
		return fonte;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

}
