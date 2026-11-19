# Avaliação: assistente contra golpes (IA + regras) na API publicada

Executada em 2026-11-19 13:25 UTC contra `https://app-vozviva-rm562312.azurewebsites.net/api/v1` (modelo `meta.llama-3.3-70b-instruct` na OCI, São Paulo), com o conjunto `src/test/resources/avaliacao/mensagens-rotuladas.json`. "Golpe detectado" = `PROVAVEL_GOLPE` ou `SUSPEITA`.

| Métrica | Valor |
|---|---|
| Mensagens | 30 |
| Acurácia | 100,0% |
| Recall (golpes detectados) | 100,0% |
| Precisão | 100,0% |
| Falsos alarmes | 0 |
| Respostas com a IA | 30 de 30 |

## Erros

Nenhum.
