"""Confere se os links oficiais da base de serviços públicos (src/main/resources/conhecimento/servicos-publicos.json)
continuam no ar. Sai com erro se algum responder 4xx/5xx ou não responder: aí alguém confere a página nova e atualiza a
base (e o revisadoEm). Roda toda semana no GitHub Actions (.github/workflows/links-oficiais.yml).

Uso: python scripts/verificar-links-oficiais.py
"""
import json
import pathlib
import sys
import urllib.error
import urllib.request

RAIZ = pathlib.Path(__file__).resolve().parent.parent
BASE = RAIZ / "src" / "main" / "resources" / "conhecimento" / "servicos-publicos.json"
# Alguns portais do governo recusam clientes sem cara de navegador.
CABECALHOS = {"User-Agent": "Mozilla/5.0 (VozViva; verificação de links oficiais)"}


def responde(link):
    for tentativa in range(2):
        try:
            pedido = urllib.request.Request(link, headers=CABECALHOS)
            with urllib.request.urlopen(pedido, timeout=30) as resposta:
                return resposta.status
        except urllib.error.HTTPError as erro:
            if erro.code < 500 or tentativa == 1:
                return erro.code
        except (urllib.error.URLError, TimeoutError) as erro:
            if tentativa == 1:
                return str(erro)
    return None


def main():
    dados = json.loads(BASE.read_text(encoding="utf-8"))
    servicos = dados["servicos"] + [dados["geral"]]
    quebrados = []
    for servico in servicos:
        status = responde(servico["link"])
        ok = isinstance(status, int) and status < 400
        print(f"{'ok ' if ok else 'ERRO'} {status} {servico['id']}: {servico['link']}")
        if not ok:
            quebrados.append(servico["id"])
    if quebrados:
        print(f"\n{len(quebrados)} link(s) fora do ar: {', '.join(quebrados)}. Confira a página nova no gov.br e "
              "atualize a base e o revisadoEm.")
        sys.exit(1)
    print(f"\nTodos os {len(servicos)} links oficiais respondem.")


if __name__ == "__main__":
    main()
