# Avaliação: perguntas do cidadão (54 perguntas)

Executada em 19/11/2026, localmente, com o código do PR #58 e o modelo real (`meta.llama-3.3-70b-instruct` na OCI, São Paulo), por `./gradlew avaliarIa`. Conjunto `src/test/resources/avaliacao/perguntas-cidadao.json` (54 perguntas, incluindo 5 sobre golpe: quem caiu num golpe, como reconhecer e uma mensagem para conferir), diferente dos exemplos do RAG. "Onde eu vou atualizar o cadastro único" virou busca do CRAS mais perto: é uma resposta aceitável para uma pergunta de "onde", mas está contada como erro.

| Configuração | Intenção certa | Serviço certo nas dúvidas | Socorros reconhecidos | Socorros falsos | Respostas com IA |
|---|---|---|---|---|---|
| Sem IA (regras e base de serviços) | 94,4% (51/54) | 100,0% (31/31) | 100,0% | 0 | 0 de 54 |
| IA + regras (modelo real) | 98,1% (53/54) | 96,8% (30/31) | 100,0% | 0 | 54 de 54 |

## Erros sem IA

- "a calçada da minha rua ta toda quebrada": esperado ZELADORIA_REPORT, obtido DESCONHECIDA
- "tem um carro parado em cima da rampa e não passo com a cadeira": esperado ZELADORIA_REPORT, obtido DESCONHECIDA
- "qual é o resultado do jogo de ontem": esperado TIRAR_DUVIDA, obtido DESCONHECIDA

## Erros com IA

- "preciso atualizar o cadastro unico onde eu vou": esperado TIRAR_DUVIDA, obtido BUSCAR_ATENDIMENTO
