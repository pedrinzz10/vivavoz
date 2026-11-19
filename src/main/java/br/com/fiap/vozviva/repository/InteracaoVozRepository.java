package br.com.fiap.vozviva.repository;

import br.com.fiap.vozviva.model.InteracaoVoz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InteracaoVozRepository extends JpaRepository<InteracaoVoz, Long> {

	Page<InteracaoVoz> findByUsuarioId(Long usuarioId, Pageable pageable);

	/** As falas mais recentes da pessoa, da mais nova para a mais antiga (contexto da conversa por voz). */
	List<InteracaoVoz> findTop3ByUsuarioIdAndCriadaEmAfterOrderByCriadaEmDesc(Long usuarioId, Instant desde);

	/** Apaga o histórico mais velho que o prazo de retenção (LGPD: o texto falado pode ter dados de saúde). */
	@Modifying
	@Query("delete from InteracaoVoz i where i.criadaEm < :limite")
	int apagarAnterioresA(@Param("limite") Instant limite);

}
