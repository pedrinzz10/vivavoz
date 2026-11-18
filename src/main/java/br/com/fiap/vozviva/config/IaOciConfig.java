package br.com.fiap.vozviva.config;

import br.com.fiap.vozviva.ai.AssinadorRequisicaoOci;
import br.com.fiap.vozviva.ai.OciAssinaturaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Autenticação do Spring AI no OCI Generative AI. Com a assinatura do usuário da OCI configurada
 * (vozviva.ia.oci.*), cada chamada ao modelo é assinada com a chave do usuário, e a API key não é usada. Sem
 * ela, vale a API key do Generative AI (spring.ai.openai.api-key), enviada como "Authorization: Bearer".
 */
@Configuration
@EnableConfigurationProperties(OciAssinaturaProperties.class)
public class IaOciConfig {

	private static final Logger log = LoggerFactory.getLogger(IaOciConfig.class);

	@Bean
	OpenAiHttpClientBuilderCustomizer assinaturaOci(OciAssinaturaProperties propriedades) {
		if (!propriedades.configurada()) {
			return builder -> { };
		}
		// Lê a chave já na subida: uma chave inválida aparece no log de início, não na primeira verificação.
		AssinadorRequisicaoOci assinador = new AssinadorRequisicaoOci(propriedades);
		log.info("IA: chamadas ao OCI Generative AI assinadas com o usuário da OCI (sem API key).");
		return builder -> builder.interceptor(assinador);
	}

}
