# Avaliação: perguntas do cidadão (49 perguntas)

Executada em 19/11/2026 com o código do PR #55, localmente, contra o modelo real (`meta.llama-3.3-70b-instruct` na OCI, São Paulo), com `./gradlew avaliarIa`. Conjunto `src/test/resources/avaliacao/perguntas-cidadao.json` (49 perguntas: 27 dúvidas sobre serviços públicos, 5 socorros, 6 buscas de atendimento, 5 golpes, 3 problemas na rua e 3 falas fora do tema), diferente dos exemplos do RAG.

| Configuração | Intenção certa | Serviço certo nas dúvidas | Socorros reconhecidos | Socorros falsos | Respostas com IA |
|---|---|---|---|---|---|
| Sem IA (regras e base de serviços) | 95,9% (47/49) | 100,0% (27/27) | 100,0% | 0 | 0 de 49 |
| IA + regras (modelo real) | 100,0% (49/49) | 100,0% (27/27) | 100,0% | 0 | 49 de 49 |

## Erros sem IA

- "a calçada da minha rua ta toda quebrada": esperado ZELADORIA_REPORT, obtido DESCONHECIDA
- "tem um carro parado em cima da rampa e não passo com a cadeira": esperado ZELADORIA_REPORT, obtido DESCONHECIDA

## Erros com IA

Nenhum.
