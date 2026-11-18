package br.com.fiap.vozviva.ai;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OciAssinaturaPropertiesTest {

	private static KeyPair chaves;
	private static String pemPkcs8;

	@BeforeAll
	static void gerarChaves() throws Exception {
		KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
		gerador.initialize(2048);
		chaves = gerador.generateKeyPair();
		pemPkcs8 = pem("PRIVATE KEY", chaves.getPrivate().getEncoded());
	}

	private static OciAssinaturaProperties com(String chave) {
		return new OciAssinaturaProperties("ocid1.tenancy.oc1..t", "ocid1.user.oc1..u", "aa:bb", chave, "ocid1.compartment.oc1..c");
	}

	@Test
	void soFicaConfiguradaComTodosOsCampos() {
		assertThat(com("chave").configurada()).isTrue();
		assertThat(new OciAssinaturaProperties("t", "u", "f", "k", " ").configurada()).isFalse();
		assertThat(new OciAssinaturaProperties(null, null, null, null, null).configurada()).isFalse();
		assertThat(com("chave").keyId()).isEqualTo("ocid1.tenancy.oc1..t/ocid1.user.oc1..u/aa:bb");
	}

	@Test
	void leOPemPkcs8EmTextoEmBase64EComBarraNLiteral() {
		byte[] esperado = chaves.getPrivate().getEncoded();

		assertThat(com(pemPkcs8).lerChavePrivada().getEncoded()).isEqualTo(esperado);
		assertThat(com(Base64.getEncoder().encodeToString(pemPkcs8.getBytes(StandardCharsets.UTF_8))).lerChavePrivada()
			.getEncoded()).isEqualTo(esperado);
		assertThat(com(pemPkcs8.replace("\n", "\\n")).lerChavePrivada().getEncoded()).isEqualTo(esperado);
	}

	@Test
	void leOPemPkcs1() {
		byte[] pkcs8 = chaves.getPrivate().getEncoded();
		// No PKCS#8 de uma chave RSA de 2048 bits, a chave PKCS#1 começa depois de 26 bytes de cabeçalho.
		byte[] pkcs1 = Arrays.copyOfRange(pkcs8, 26, pkcs8.length);

		assertThat(OciAssinaturaProperties.pkcs1ParaPkcs8(pkcs1)).isEqualTo(pkcs8);
		assertThat(com(pem("RSA PRIVATE KEY", pkcs1)).lerChavePrivada().getEncoded()).isEqualTo(pkcs8);
	}

	@Test
	void recusaChaveComSenhaOuInvalida() {
		assertThatThrownBy(() -> com(pem("ENCRYPTED PRIVATE KEY", new byte[] { 0 })).lerChavePrivada())
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("senha");
		assertThatThrownBy(() -> com(pem("PRIVATE KEY", new byte[] { 0, 0, 0 })).lerChavePrivada())
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static String pem(String tipo, byte[] der) {
		return "-----BEGIN " + tipo + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
			.encodeToString(der) + "\n-----END " + tipo + "-----\n";
	}

}
