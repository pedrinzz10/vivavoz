package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.model.ContaExcluida;
import br.com.fiap.vozviva.repository.ContaExcluidaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Lista de contas excluídas. O JWT é sem estado e continua válido até expirar; esta lista é o que impede que o
 * token de uma conta excluída siga funcionando (consultada pelo validador do token a cada requisição).
 */
@Service
public class ContasExcluidas {

	private final ContaExcluidaRepository repositorio;

	public ContasExcluidas(ContaExcluidaRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Transactional
	public void registrar(Long usuarioId) {
		repositorio.save(new ContaExcluida(usuarioId, Instant.now()));
	}

	@Transactional(readOnly = true)
	public boolean foiExcluida(Long usuarioId) {
		return repositorio.existsById(usuarioId);
	}

}
