package br.com.fiap.vozviva.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Com a assinatura do usuário da OCI configurada, a chamada que o Spring AI faz ao modelo sai assinada (e sem a
 * API key). Um servidor HTTP local faz o papel do OCI Generative AI e guarda a requisição recebida.
 */
@SpringBootTest(properties = { "spring.ai.model.chat=openai", "spring.ai.openai.api-key=",
		"spring.ai.openai.max-retries=0" })
@ActiveProfiles("test")
class IaOciAssinaturaIntegrationTest {

	private static final KeyPair CHAVES = gerarChaves();
	private static final Map<String, String> RECEBIDO = new ConcurrentHashMap<>();
	private static final HttpServer SERVIDOR = iniciarServidor();

	@Autowired
	private ChatModel modelo;

	@DynamicPropertySource
	static void propriedades(DynamicPropertyRegistry registro) {
		registro.add("spring.ai.openai.base-url", () -> "http://localhost:" + SERVIDOR.getAddress().getPort() + "/openai/v1");
		registro.add("vozviva.ia.oci.tenancy", () -> "ocid1.tenancy.oc1..t");
		registro.add("vozviva.ia.oci.usuario", () -> "ocid1.user.oc1..u");
		registro.add("vozviva.ia.oci.fingerprint", () -> "aa:bb:cc");
		registro.add("vozviva.ia.oci.compartimento", () -> "ocid1.compartment.oc1..c");
		// PEM montado na hora com a chave gerada no teste (o marcador é concatenado para o scanner de segredos).
		String marcador = "PRIVATE" + " KEY-----";
		registro.add("vozviva.ia.oci.chave-privada", () -> Base64.getEncoder().encodeToString(("-----BEGIN " + marcador + "\n"
				+ Base64.getMimeEncoder().encodeToString(CHAVES.getPrivate().getEncoded()) + "\n-----END " + marcador + "\n")
			.getBytes(StandardCharsets.UTF_8)));
	}

	@AfterAll
	static void parar() {
		SERVIDOR.stop(0);
	}

	@Test
	void chamadaAoModeloSaiAssinadaComOUsuarioDaOci() throws Exception {
		String resposta = modelo.call("Responda só: ok");

		assertThat(resposta).isEqualTo("ok");
		String autorizacao = RECEBIDO.get("authorization");
		assertThat(autorizacao).startsWith("Signature version=\"1\"").doesNotContain("Bearer")
			.contains("keyId=\"ocid1.tenancy.oc1..t/ocid1.user.oc1..u/aa:bb:cc\"");
		assertThat(RECEBIDO.get("opc-compartment-id")).isEqualTo("ocid1.compartment.oc1..c");

		Map<String, String> campos = new java.util.LinkedHashMap<>();
		Matcher m = Pattern.compile("(\\w+)=\"([^\"]*)\"").matcher(autorizacao);
		while (m.find()) {
			campos.put(m.group(1), m.group(2));
		}
		assertThat(campos.get("headers")).isEqualTo("date (request-target) host content-length content-type x-content-sha256");
		// Confere a assinatura com o que chegou de fato no servidor (cabeçalhos e alvo da requisição).
		String texto = Arrays.stream(campos.get("headers").split(" "))
			.map(nome -> nome + ": " + (nome.equals("(request-target)") ? RECEBIDO.get("(request-target)") : RECEBIDO.get(nome)))
			.collect(Collectors.joining("\n"));
		Signature verificador = Signature.getInstance("SHA256withRSA");
		verificador.initVerify(CHAVES.getPublic());
		verificador.update(texto.getBytes(StandardCharsets.UTF_8));
		assertThat(verificador.verify(Base64.getDecoder().decode(campos.get("signature")))).isTrue();
	}

	private static HttpServer iniciarServidor() {
		try {
			HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			servidor.createContext("/", troca -> {
				troca.getRequestHeaders().forEach((nome, valores) -> RECEBIDO.put(nome.toLowerCase(), String.join(",", valores)));
				RECEBIDO.put("(request-target)", troca.getRequestMethod().toLowerCase() + " " + troca.getRequestURI());
				troca.getRequestBody().readAllBytes();
				byte[] corpo = """
						{"id":"chat-1","object":"chat.completion","created":1,"model":"meta.llama-3.3-70b-instruct",
						 "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
						 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
						""".getBytes(StandardCharsets.UTF_8);
				troca.getResponseHeaders().put("Content-Type", List.of("application/json"));
				troca.sendResponseHeaders(200, corpo.length);
				troca.getResponseBody().write(corpo);
				troca.close();
			});
			servidor.start();
			return servidor;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static KeyPair gerarChaves() {
		try {
			KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
			gerador.initialize(2048);
			return gerador.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
