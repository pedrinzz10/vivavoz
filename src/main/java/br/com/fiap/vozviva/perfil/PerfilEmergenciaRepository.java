package br.com.fiap.vozviva.perfil;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PerfilEmergenciaRepository extends JpaRepository<PerfilEmergencia, Long> {

	@EntityGraph(attributePaths = { "cidadao", "cuidador" })
	Page<PerfilEmergencia> findByCuidadorId(Long cuidadorId, Pageable pageable);

	@EntityGraph(attributePaths = { "cidadao", "cuidador" })
	Optional<PerfilEmergencia> findByCidadaoId(Long cidadaoId);

	@EntityGraph(attributePaths = { "cidadao", "cuidador" })
	Optional<PerfilEmergencia> findWithUsuariosById(Long id);

	boolean existsByCidadaoId(Long cidadaoId);

}
