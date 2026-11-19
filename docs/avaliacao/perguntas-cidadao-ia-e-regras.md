# Avaliação: perguntas do cidadão (49 perguntas)

Executada em 19/11/2026 com o código do PR #55, localmente, contra o modelo real (`meta.llama-3.3-70b-instruct` na OCI, São Paulo), com `./gradlew avaliarIa`. Conjunto `src/test/resources/avaliacao/perguntas-cidadao.json` (49 perguntas), diferente dos exemplos do RAG. Sem IA, "resultado do jogo" não tem resposta (fica DESCONHECIDA), por isso a linha de base caiu de 95,9% para 93,9% quando qualquer pergunta passou a ser TIRAR_DUVIDA.

| Configuração | Intenção certa | Serviço certo nas dúvidas | Socorros reconhecidos | Socorros falsos | Respostas com IA |
|---|---|---|---|---|---|
| Sem IA (regras e base de serviços) | 93,9% (46/49) | 100,0% (27/27) | 100,0% | 0 | 0 de 49 |
| IA + regras (modelo real) | 100,0% (49/49) | 100,0% (27/27) | 100,0% | 0 | 49 de 49 |

## Erros sem IA

- "a calçada da minha rua ta toda quebrada": esperado ZELADORIA_REPORT, obtido DESCONHECIDA
- "tem um carro parado em cima da rampa e não passo com a cadeira": esperado ZELADORIA_REPORT, obtido DESCONHECIDA
- "qual é o resultado do jogo de ontem": esperado TIRAR_DUVIDA, obtido DESCONHECIDA

## Erros com IA

Nenhum.
