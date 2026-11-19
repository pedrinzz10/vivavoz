package br.com.fiap.vozviva.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Aviso de exclusão de conta para a API .NET de zeladoria, com um servidor HTTP local no lugar dela. */
class ZeladoriaClienteTest {

	private final List<String> recebidas = new CopyOnWriteArrayList<>();
	private HttpServer servidor;

	@BeforeEach
	void iniciar() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		servidor.createContext("/", troca -> {
			recebidas.add(troca.getRequestMethod() + " " + troca.getRequestURI() + " "
					+ troca.getRequestHeaders().getFirst("Authorization"));
			troca.sendResponseHeaders(204, -1);
			troca.close();
		});
		servidor.start();
	}

	@AfterEach
	void parar() {
		servidor.stop(0);
	}

	private ZeladoriaCliente cliente(String url) {
		return new ZeladoriaCliente(RestClient.builder(), url);
	}

	@Test
	void avisaAZeladoriaComOTokenDeQuemExcluiAConta() {
		cliente("http://localhost:" + servidor.getAddress().getPort()).excluirConta("token-do-cidadao");

		assertThat(recebidas).containsExactly("DELETE /api/usuarios/eu Bearer token-do-cidadao");
	}

	@Test
	void zeladoriaForaDoArNaoImpedeAExclusao() throws IOException {
		int portaFechada;
		try (ServerSocket socket = new ServerSocket(0)) {
			portaFechada = socket.getLocalPort();
		}

		assertThatCode(() -> cliente("http://localhost:" + portaFechada).excluirConta("token"))
			.doesNotThrowAnyException();
	}

	@Test
	void semUrlConfiguradaNaoChamaNada() {
		cliente("").excluirConta("token");
		cliente("http://localhost:" + servidor.getAddress().getPort()).excluirConta(" ");

		assertThat(recebidas).isEmpty();
	}

}
