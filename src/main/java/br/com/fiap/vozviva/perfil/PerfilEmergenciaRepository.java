package br.com.fiap.vozviva.perfil;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PerfilEmergenciaRepository extends JpaRepository<PerfilEmergencia, Long> {

	@EntityGraph(attributePaths = { "cidadao", "cuidador" })
	Page<PerfilEmergencia> findByCuidadorId(Long cuidadorId, Pageable pageable);

	@EntityGraph(attributePaths = { "cidadao", "cuidador" })
	Optional<PerfilEmergencia> findByCidadaoId(Long cidadaoId);

	@EntityGraph(attributePaths = { "cidadao", "cuidador" })
	Optional<PerfilEmergencia> findWithUsuariosById(Long id);

	boolean existsByCidadaoId(Long cidadaoId);

	/**
	 * Leitura com trava da linha do perfil até o fim da transação. Usada ao adicionar contatos: duas
	 * inclusões simultâneas passam a acontecer uma depois da outra, e o limite de contatos é respeitado.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PerfilEmergencia p join fetch p.cidadao join fetch p.cuidador where p.id = :id")
	Optional<PerfilEmergencia> findParaAlterarById(@Param("id") Long id);

}
