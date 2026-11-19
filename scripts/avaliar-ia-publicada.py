"""Avalia a IA na API publicada (modelo real da OCI + regras) com os mesmos conjuntos rotulados e as mesmas métricas
do ./gradlew avaliarIa: 30 mensagens de golpe/legítimas e 32 falas de intenção por voz.

Cria um usuário de teste, chama a API para cada item, grava os relatórios em docs/avaliacao/ e apaga o usuário no fim.
Atenção: cada verificação de golpe entra nas estatísticas anônimas da API (tabela verificacoes_golpe, sem dono), que
não saem com o usuário. No fim, o script mostra o SQL que apaga só os registros da janela desta avaliação.
Uso: python scripts/avaliar-ia-publicada.py [URL da API]   (padrão: a API da equipe na Azure)
"""
import json
import pathlib
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from datetime import datetime, timezone

RAIZ = pathlib.Path(__file__).resolve().parent.parent
API = (sys.argv[1] if len(sys.argv) > 1 else "https://app-vozviva-rm562312.azurewebsites.net").rstrip("/") + "/api/v1"
SAIDA = RAIZ / "docs" / "avaliacao"


def chamar(metodo, caminho, corpo=None, token=None):
    dados = json.dumps(corpo, ensure_ascii=False).encode("utf-8") if corpo is not None else None
    pedido = urllib.request.Request(API + caminho, data=dados, method=metodo)
    pedido.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    for tentativa in range(3):
        try:
            with urllib.request.urlopen(pedido, timeout=120) as resposta:
                texto = resposta.read().decode("utf-8")
                return json.loads(texto) if texto else None
        except urllib.error.HTTPError as erro:
            if erro.code >= 500 and tentativa < 2:
                time.sleep(3)
                continue
            raise


def porcento(valor):
    return f"{valor * 100:.1f}%".replace(".", ",")


def avaliar_golpes(token):
    mensagens = json.loads((RAIZ / "src/test/resources/avaliacao/mensagens-rotuladas.json").read_text(encoding="utf-8"))
    vp = fp = vn = fn = 0
    erros, origem = [], Counter()
    for m in mensagens:
        r = chamar("POST", "/verificacoes", {"texto": m["texto"], "canal": m["canal"]}, token)
        origem[r.get("analisadoPor")] += 1
        detectou = r.get("classificacao") in ("PROVAVEL_GOLPE", "SUSPEITA")
        golpe = m["rotulo"] == "GOLPE"
        if golpe and detectou:
            vp += 1
        elif golpe:
            fn += 1
            erros.append(f"- #{m['id']} golpe não detectado ({r.get('classificacao')}): {m['texto']}")
        elif detectou:
            fp += 1
            erros.append(f"- #{m['id']} falso alarme ({r.get('classificacao')}): {m['texto']}")
        else:
            vn += 1
    total = vp + fp + vn + fn
    return {
        "total": total, "acuracia": (vp + vn) / total, "recall": vp / (vp + fn),
        "precisao": vp / (vp + fp) if vp + fp else 0, "falsos": fp, "erros": erros, "origem": origem,
    }


def avaliar_voz(token):
    frases = json.loads((RAIZ / "src/test/resources/avaliacao/frases-intencoes.json").read_text(encoding="utf-8"))["frases"]
    acertos = socorros = reconhecidos = falsos = 0
    erros, origem = [], Counter()
    for f in frases:
        r = chamar("POST", "/voz/intencoes", {"texto": f["texto"], "idioma": "pt-BR"}, token)
        origem[r.get("analisadoPor")] += 1
        obtida = r.get("intencao")
        if obtida == f["intencao"]:
            acertos += 1
        else:
            erros.append(f"- \"{f['texto']}\": esperado {f['intencao']}, obtido {obtida}")
        if f["intencao"] == "SOCORRO":
            socorros += 1
            reconhecidos += obtida == "SOCORRO"
        elif obtida == "SOCORRO":
            falsos += 1
    return {
        "total": len(frases), "acuracia": acertos / len(frases), "recall_socorro": reconhecidos / socorros if socorros else 1,
        "falsos": falsos, "erros": erros, "origem": origem,
    }


def main():
    inicio = datetime.now(timezone.utc)
    agora = inicio.strftime("%Y-%m-%d %H:%M UTC")
    email = f"avaliacao.ia.{int(time.time())}@vozviva.dev"
    chamar("POST", "/auth/cadastro", {"nome": "Avaliação da IA", "email": email, "senha": "avaliacao2026",
                                      "papel": "CIDADAO", "consentimento": True})
    token = chamar("POST", "/auth/login", {"email": email, "senha": "avaliacao2026"})["accessToken"]
    try:
        g = avaliar_golpes(token)
        v = avaliar_voz(token)
    finally:
        chamar("DELETE", "/auth/eu", token=token)
    fim = datetime.now(timezone.utc)

    SAIDA.mkdir(parents=True, exist_ok=True)
    (SAIDA / "golpes-ia-e-regras.md").write_text(
        f"# Avaliação: assistente contra golpes (IA + regras) na API publicada\n\n"
        f"Executada em {agora} contra `{API}` (modelo `meta.llama-3.3-70b-instruct` na OCI, São Paulo), com o conjunto "
        f"`src/test/resources/avaliacao/mensagens-rotuladas.json`. \"Golpe detectado\" = `PROVAVEL_GOLPE` ou `SUSPEITA`.\n\n"
        f"| Métrica | Valor |\n|---|---|\n| Mensagens | {g['total']} |\n| Acurácia | {porcento(g['acuracia'])} |\n"
        f"| Recall (golpes detectados) | {porcento(g['recall'])} |\n| Precisão | {porcento(g['precisao'])} |\n"
        f"| Falsos alarmes | {g['falsos']} |\n| Respostas com a IA | {g['origem'].get('IA_E_REGRAS', 0)} de {g['total']} |\n\n"
        f"## Erros\n\n" + ("\n".join(g["erros"]) or "Nenhum.") + "\n", encoding="utf-8")
    (SAIDA / "intencoes-ia-e-regras.md").write_text(
        f"# Avaliação: classificação de intenção por voz (IA + regras) na API publicada\n\n"
        f"Executada em {agora} contra `{API}` (modelo `meta.llama-3.3-70b-instruct` na OCI, São Paulo), com o conjunto "
        f"`src/test/resources/avaliacao/frases-intencoes.json`.\n\n"
        f"| Métrica | Valor |\n|---|---|\n| Falas | {v['total']} |\n| Acurácia | {porcento(v['acuracia'])} |\n"
        f"| Recall de socorro | {porcento(v['recall_socorro'])} |\n| Socorros falsos | {v['falsos']} |\n"
        f"| Respostas com a IA | {v['origem'].get('IA_E_REGRAS', 0)} de {v['total']} |\n\n"
        f"## Erros\n\n" + ("\n".join(v["erros"]) or "Nenhum.") + "\n", encoding="utf-8")

    print(f"Golpes: acurácia {porcento(g['acuracia'])}, recall {porcento(g['recall'])}, precisão {porcento(g['precisao'])}, "
          f"falsos alarmes {g['falsos']}, com IA {dict(g['origem'])}")
    for e in g["erros"]:
        print("   ", e)
    print(f"Voz: acurácia {porcento(v['acuracia'])}, recall de socorro {porcento(v['recall_socorro'])}, "
          f"socorros falsos {v['falsos']}, com IA {dict(v['origem'])}")
    for e in v["erros"]:
        print("   ", e)

    print()
    print("As verificações desta avaliação entraram nas estatísticas anônimas de golpes. Para tirá-las, rode no banco:")
    print(f"  DELETE FROM verificacoes_golpe WHERE criada_em BETWEEN '{inicio:%Y-%m-%d %H:%M:%S}' "
          f"AND '{fim:%Y-%m-%d %H:%M:%S}';")


if __name__ == "__main__":
    main()
