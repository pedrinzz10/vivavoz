package br.com.fiap.vozviva.ai;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AssinadorRequisicaoOciTest {

	private static final String URL = "https://inference.generativeai.sa-saopaulo-1.oci.oraclecloud.com/openai/v1/chat/completions";
	private static final String KEY_ID = "ocid1.tenancy.oc1..aaaa/ocid1.user.oc1..bbbb/12:34:56";
	private static final String COMPARTIMENTO = "ocid1.compartment.oc1..cccc";
	private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-18T20:30:05Z"), ZoneOffset.UTC);

	private static KeyPair chaves;

	@BeforeAll
	static void gerarChaves() throws Exception {
		KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
		gerador.initialize(2048);
		chaves = gerador.generateKeyPair();
	}

	private AssinadorRequisicaoOci assinador() {
		return new AssinadorRequisicaoOci(KEY_ID, chaves.getPrivate(), COMPARTIMENTO, RELOGIO);
	}

	@Test
	void assinaOPostComOCorpoETiraOBearer() throws Exception {
		String json = "{\"model\":\"meta.llama-3.3-70b-instruct\",\"messages\":[]}";
		Request original = new Request.Builder().url(URL)
			.header("Authorization", "Bearer no-auth-placeholder")
			.post(RequestBody.create(json, MediaType.get("application/json; charset=utf-8")))
			.build();

		Request assinada = assinador().assinar(original);

		String autorizacao = assinada.header("Authorization");
		assertThat(autorizacao).startsWith("Signature version=\"1\"").doesNotContain("Bearer");
		Map<String, String> campos = campos(autorizacao);
		assertThat(campos).containsEntry("keyId", KEY_ID).containsEntry("algorithm", "rsa-sha256")
			.containsEntry("headers", "date (request-target) host content-length content-type x-content-sha256");
		assertThat(assinada.header("date")).isEqualTo("Wed, 18 Nov 2026 20:30:05 GMT");
		assertThat(assinada.header("host")).isEqualTo("inference.generativeai.sa-saopaulo-1.oci.oraclecloud.com");
		assertThat(assinada.header("content-length")).isEqualTo(Integer.toString(json.getBytes(StandardCharsets.UTF_8).length));
		assertThat(assinada.header("x-content-sha256")).isEqualTo(Base64.getEncoder()
			.encodeToString(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))));
		assertThat(assinada.header(AssinadorRequisicaoOci.CABECALHO_COMPARTIMENTO)).isEqualTo(COMPARTIMENTO);
		assertThat(corpo(assinada)).isEqualTo(json);
		assertThat(confere(assinada, campos)).isTrue();
	}

	@Test
	void semCorpoAssinaSoDataAlvoEHost() throws Exception {
		Request original = new Request.Builder().url("https://exemplo.oraclecloud.com:8443/20231130/models?limit=5").get().build();

		Request assinada = assinador().assinar(original);

		Map<String, String> campos = campos(assinada.header("Authorization"));
		assertThat(campos).containsEntry("headers", "date (request-target) host");
		assertThat(assinada.header("host")).isEqualTo("exemplo.oraclecloud.com:8443");
		assertThat(AssinadorRequisicaoOci.alvo(original)).isEqualTo("get /20231130/models?limit=5");
		assertThat(assinada.header("x-content-sha256")).isNull();
		assertThat(confere(assinada, campos)).isTrue();
	}

	@Test
	void funcionaComoInterceptorDoOkHttp() throws Exception {
		AtomicReference<Request> enviada = new AtomicReference<>();
		OkHttpClient cliente = new OkHttpClient.Builder()
			.addInterceptor(assinador())
			.addInterceptor(chain -> {
				enviada.set(chain.request());
				return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
					.body(ResponseBody.create("{}", MediaType.get("application/json"))).build();
			})
			.build();

		try (Response resposta = cliente.newCall(new Request.Builder().url(URL)
			.post(RequestBody.create("{}", MediaType.get("application/json"))).build()).execute()) {
			assertThat(resposta.code()).isEqualTo(200);
		}

		assertThat(enviada.get().header("Authorization")).startsWith("Signature ");
		assertThat(confere(enviada.get(), campos(enviada.get().header("Authorization")))).isTrue();
	}

	/** Refaz a string assinada a partir dos cabeçalhos enviados e confere com a chave pública. */
	private static boolean confere(Request requisicao, Map<String, String> campos) throws Exception {
		String texto = Arrays.stream(campos.get("headers").split(" "))
			.map(nome -> nome + ": " + (nome.equals("(request-target)") ? AssinadorRequisicaoOci.alvo(requisicao)
					: requisicao.header(nome)))
			.collect(Collectors.joining("\n"));
		Signature verificador = Signature.getInstance("SHA256withRSA");
		verificador.initVerify(chaves.getPublic());
		verificador.update(texto.getBytes(StandardCharsets.UTF_8));
		return verificador.verify(Base64.getDecoder().decode(campos.get("signature")));
	}

	private static Map<String, String> campos(String autorizacao) {
		Map<String, String> campos = new LinkedHashMap<>();
		Matcher m = Pattern.compile("(\\w+)=\"([^\"]*)\"").matcher(autorizacao);
		while (m.find()) {
			campos.put(m.group(1), m.group(2));
		}
		return campos;
	}

	private static String corpo(Request requisicao) throws Exception {
		Buffer buffer = new Buffer();
		requisicao.body().writeTo(buffer);
		return buffer.readUtf8();
	}

}
