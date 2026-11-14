package br.com.fiap.vozviva.voz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteracaoVozRepository extends JpaRepository<InteracaoVoz, Long> {

	Page<InteracaoVoz> findByUsuarioId(Long usuarioId, Pageable pageable);

}
