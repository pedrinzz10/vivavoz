"""Confere se os links oficiais da base de serviços públicos (src/main/resources/conhecimento/servicos-publicos.json)
continuam no ar. Sai com erro se algum responder 4xx/5xx: aí alguém confere a página nova e atualiza a base (e o
revisadoEm). Falha de rede do próprio ambiente só gera aviso, a não ser que nenhum link responda. Roda toda semana no GitHub Actions (.github/workflows/links-oficiais.yml).

Uso: python scripts/verificar-links-oficiais.py
"""
import json
import pathlib
import socket
import sys
import time
import urllib.error
import urllib.request

RAIZ = pathlib.Path(__file__).resolve().parent.parent
BASE = RAIZ / "src" / "main" / "resources" / "conhecimento" / "servicos-publicos.json"
# Os runners do GitHub às vezes não têm rota IPv6 ("Network is unreachable"): a conferência usa só IPv4.
_getaddrinfo = socket.getaddrinfo
socket.getaddrinfo = lambda host, port, family=0, *args, **kwargs: _getaddrinfo(
    host, port, socket.AF_INET, *args, **kwargs)

TENTATIVAS = 3
# Alguns portais do governo recusam clientes sem cara de navegador.
CABECALHOS = {"User-Agent": "Mozilla/5.0 (VozViva; verificação de links oficiais)"}


def responde(link):
    """Status HTTP, ou o texto do erro de rede depois de todas as tentativas (com espera crescente)."""
    for tentativa in range(1, TENTATIVAS + 1):
        try:
            pedido = urllib.request.Request(link, headers=CABECALHOS)
            with urllib.request.urlopen(pedido, timeout=30) as resposta:
                return resposta.status
        except urllib.error.HTTPError as erro:
            if erro.code < 500 or tentativa == TENTATIVAS:
                return erro.code
        except (urllib.error.URLError, TimeoutError, OSError) as erro:
            if tentativa == TENTATIVAS:
                return str(erro)
        time.sleep(5 * tentativa)
    return None


def main():
    dados = json.loads(BASE.read_text(encoding="utf-8"))
    servicos = dados["servicos"] + [dados["geral"]]
    quebrados, sem_rede = [], []
    for servico in servicos:
        status = responde(servico["link"])
        if isinstance(status, int):
            ok = status < 400
            print(f"{'ok ' if ok else 'ERRO'} {status} {servico['id']}: {servico['link']}")
            if not ok:
                quebrados.append(servico["id"])
        else:
            # Falha de rede do próprio runner não é link quebrado: avisa, sem derrubar a verificação.
            print(f"aviso sem resposta ({status}) {servico['id']}: {servico['link']}")
            sem_rede.append(servico["id"])
    if len(sem_rede) == len(servicos):
        print()
        print("Nenhum link respondeu: a rede do ambiente parece fora do ar. Rode de novo mais tarde.")
        sys.exit(1)
    if quebrados:
        print(f"\n{len(quebrados)} link(s) fora do ar: {', '.join(quebrados)}. Confira a página nova no gov.br e "
              "atualize a base e o revisadoEm.")
        sys.exit(1)
    print(f"\nTodos os {len(servicos)} links oficiais respondem.")


if __name__ == "__main__":
    main()
