package br.com.fiap.vozviva.golpe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface VerificacaoGolpeRepository extends JpaRepository<VerificacaoGolpe, Long> {

	/** Golpes detectados (provável golpe ou suspeita) por tipo, do mais para o menos frequente. */
	@Query("""
			select v.tipoGolpe as tipoGolpe, count(v) as total
			from VerificacaoGolpe v
			where v.criadaEm >= :desde
			  and v.classificacao in (br.com.fiap.vozviva.golpe.ClassificacaoGolpe.PROVAVEL_GOLPE,
			                          br.com.fiap.vozviva.golpe.ClassificacaoGolpe.SUSPEITA)
			group by v.tipoGolpe
			order by count(v) desc
			""")
	List<TotalPorTipo> contarGolpesPorTipo(@Param("desde") Instant desde);

	interface TotalPorTipo {

		TipoGolpe getTipoGolpe();

		long getTotal();

	}

}
