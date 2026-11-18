package br.com.fiap.vozviva.ai;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assina as chamadas ao OCI Generative AI com a chave de assinatura de um usuário da OCI (assinatura HTTP
 * "Signature version 1" da OCI, rsa-sha256), no lugar do cabeçalho "Authorization: Bearer" da API key.
 * <p>
 * Assina date, (request-target) e host e, quando há corpo, também content-length, content-type e
 * x-content-sha256. Também envia o compartimento onde a chamada é cobrada (opc-compartment-id), exigido pelo
 * endpoint compatível com a OpenAI quando não há API key.
 */
public final class AssinadorRequisicaoOci implements Interceptor {

	static final String CABECALHO_COMPARTIMENTO = "opc-compartment-id";

	private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US);
	private static final MediaType JSON = MediaType.get("application/json");

	private final String keyId;
	private final PrivateKey chave;
	private final String compartimento;
	private final Clock relogio;

	public AssinadorRequisicaoOci(OciAssinaturaProperties propriedades) {
		this(propriedades.keyId(), propriedades.lerChavePrivada(), propriedades.compartimento().trim(), Clock.systemUTC());
	}

	AssinadorRequisicaoOci(String keyId, PrivateKey chave, String compartimento, Clock relogio) {
		this.keyId = keyId;
		this.chave = chave;
		this.compartimento = compartimento;
		this.relogio = relogio;
	}

	@Override
	public Response intercept(Chain chain) throws IOException {
		return chain.proceed(assinar(chain.request()));
	}

	Request assinar(Request original) throws IOException {
		Request.Builder saida = original.newBuilder().removeHeader("Authorization");
		if (StringUtils.hasText(compartimento)) {
			saida.header(CABECALHO_COMPARTIMENTO, compartimento);
		}

		// Ordem dos cabeçalhos assinados; o valor de cada um entra na string assinada como "nome: valor".
		Map<String, String> assinados = new LinkedHashMap<>();
		assinados.put("date", FORMATO_DATA.format(ZonedDateTime.now(relogio).withZoneSameInstant(ZoneOffset.UTC)));
		assinados.put("(request-target)", alvo(original));
		assinados.put("host", host(original.url()));

		RequestBody corpo = original.body();
		if (corpo != null) {
			Buffer buffer = new Buffer();
			corpo.writeTo(buffer);
			byte[] bytes = buffer.readByteArray();
			MediaType tipo = corpo.contentType() != null ? corpo.contentType() : JSON;
			// O corpo é refeito a partir dos bytes lidos, para o que vai na rede ser exatamente o que foi assinado.
			saida.method(original.method(), RequestBody.create(bytes, tipo));
			assinados.put("content-length", Integer.toString(bytes.length));
			assinados.put("content-type", tipo.toString());
			assinados.put("x-content-sha256", Base64.getEncoder().encodeToString(sha256(bytes)));
		}

		List<String> linhas = new ArrayList<>();
		assinados.forEach((nome, valor) -> {
			linhas.add(nome + ": " + valor);
			if (!nome.startsWith("(")) {
				saida.header(nome, valor);
			}
		});
		String assinatura = Base64.getEncoder().encodeToString(rsaSha256(String.join("\n", linhas)));
		saida.header("Authorization", "Signature version=\"1\",headers=\"" + String.join(" ", assinados.keySet())
				+ "\",keyId=\"" + keyId + "\",algorithm=\"rsa-sha256\",signature=\"" + assinatura + "\"");
		return saida.build();
	}

	static String alvo(Request requisicao) {
		HttpUrl url = requisicao.url();
		String consulta = url.encodedQuery();
		return requisicao.method().toLowerCase(Locale.ROOT) + " " + url.encodedPath()
				+ (consulta == null ? "" : "?" + consulta);
	}

	static String host(HttpUrl url) {
		return url.port() == HttpUrl.defaultPort(url.scheme()) ? url.host() : url.host() + ":" + url.port();
	}

	private byte[] rsaSha256(String texto) {
		try {
			Signature assinador = Signature.getInstance("SHA256withRSA");
			assinador.initSign(chave);
			assinador.update(texto.getBytes(StandardCharsets.UTF_8));
			return assinador.sign();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Não foi possível assinar a chamada à OCI", ex);
		}
	}

	private static byte[] sha256(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
