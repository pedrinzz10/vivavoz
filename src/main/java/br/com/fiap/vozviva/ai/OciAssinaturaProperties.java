package br.com.fiap.vozviva.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Credenciais para chamar o OCI Generative AI assinando cada requisição com a chave de assinatura (API signing
 * key) de um usuário da OCI, no lugar de uma API key do Generative AI. É o caminho para contas em que o limite
 * de API keys do serviço é zero.
 *
 * @param tenancy       OCID da tenancy
 * @param usuario       OCID do usuário dono da chave de assinatura
 * @param fingerprint   fingerprint da chave pública cadastrada no usuário
 * @param chavePrivada  chave privada RSA em PEM (PKCS#8 ou PKCS#1), em texto ou em Base64 do PEM inteiro
 * @param compartimento OCID do compartimento onde as chamadas ao modelo são cobradas
 */
@ConfigurationProperties("vozviva.ia.oci")
public record OciAssinaturaProperties(
		String tenancy,
		String usuario,
		String fingerprint,
		String chavePrivada,
		String compartimento) {

	/** Só assina quando todos os campos estão preenchidos. */
	public boolean configurada() {
		return StringUtils.hasText(tenancy) && StringUtils.hasText(usuario) && StringUtils.hasText(fingerprint)
				&& StringUtils.hasText(chavePrivada) && StringUtils.hasText(compartimento);
	}

	/** Identificador da chave no cabeçalho de assinatura: tenancy/usuário/fingerprint. */
	public String keyId() {
		return tenancy.trim() + "/" + usuario.trim() + "/" + fingerprint.trim();
	}

	/**
	 * Lê a chave privada. Aceita o PEM com quebras de linha reais, com "\n" literais (comum em variáveis de
	 * ambiente) ou o PEM inteiro em Base64 (o jeito mais seguro de colocá-lo numa configuração do App Service).
	 */
	public PrivateKey lerChavePrivada() {
		String pem = chavePrivada.trim();
		if (!pem.contains("-----BEGIN")) {
			pem = new String(Base64.getMimeDecoder().decode(pem), StandardCharsets.UTF_8).trim();
		}
		pem = pem.replace("\\n", "\n");
		boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
		if (pem.contains("ENCRYPTED")) {
			throw new IllegalArgumentException("A chave privada da OCI não pode ter senha");
		}
		String base64 = pem.replaceAll("-----(BEGIN|END)[A-Z ]+-----", "").replaceAll("\\s", "");
		byte[] der = Base64.getDecoder().decode(base64);
		try {
			return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? pkcs1ParaPkcs8(der) : der));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalArgumentException("Chave privada da OCI inválida", ex);
		}
	}

	/** Embrulha uma chave RSA PKCS#1 na estrutura PKCS#8 que o Java lê. */
	static byte[] pkcs1ParaPkcs8(byte[] pkcs1) {
		// AlgorithmIdentifier de rsaEncryption (1.2.840.113549.1.1.1) com parâmetros NULL.
		byte[] algoritmo = { 0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01,
				0x01, 0x05, 0x00 };
		byte[] versao = { 0x02, 0x01, 0x00 };
		byte[] chave = der((byte) 0x04, pkcs1);
		byte[] conteudo = new byte[versao.length + algoritmo.length + chave.length];
		System.arraycopy(versao, 0, conteudo, 0, versao.length);
		System.arraycopy(algoritmo, 0, conteudo, versao.length, algoritmo.length);
		System.arraycopy(chave, 0, conteudo, versao.length + algoritmo.length, chave.length);
		return der((byte) 0x30, conteudo);
	}

	private static byte[] der(byte tipo, byte[] conteudo) {
		int n = conteudo.length;
		byte[] tamanho;
		if (n < 0x80) {
			tamanho = new byte[] { (byte) n };
		}
		else if (n <= 0xff) {
			tamanho = new byte[] { (byte) 0x81, (byte) n };
		}
		else if (n <= 0xffff) {
			tamanho = new byte[] { (byte) 0x82, (byte) (n >> 8), (byte) n };
		}
		else {
			tamanho = new byte[] { (byte) 0x83, (byte) (n >> 16), (byte) (n >> 8), (byte) n };
		}
		byte[] saida = new byte[1 + tamanho.length + n];
		saida[0] = tipo;
		System.arraycopy(tamanho, 0, saida, 1, tamanho.length);
		System.arraycopy(conteudo, 0, saida, 1 + tamanho.length, n);
		return saida;
	}

}
