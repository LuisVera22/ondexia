"""Oráculo de este lote sobre una salida completa de pnpm audit --json."""

import json
import sys
from pathlib import Path

AVISOS_OBJETIVO = frozenset({
    "GHSA-x5fp-wj9c-mxmx",
    "GHSA-4mjr-xmp4-gh2g",
    "GHSA-q2hr-2g5m-vwhr",
    "GHSA-qhr7-859c-m2p7",
    "GHSA-6j4f-fj2g-mc7p",
    "GHSA-2gc4-cqfq-p2gv",
})


def comprobar(ruta):
    datos = json.loads(Path(ruta).read_text())
    avisos = datos["advisories"]
    cantidades = datos["metadata"]["vulnerabilities"]
    if not isinstance(avisos, dict) or sum(cantidades.values()) != len(avisos):
        raise ValueError("La auditoría está filtrada o incompleta")
    presentes = {aviso["github_advisory_id"] for aviso in avisos.values()}
    pendientes = sorted(AVISOS_OBJETIVO & presentes)
    print(json.dumps({"avisos_objetivo_pendientes": pendientes,
                      "cantidades_completas": cantidades}, ensure_ascii=False))
    return 1 if pendientes or cantidades["critical"] else 0


if __name__ == "__main__":
    try:
        sys.exit(comprobar(sys.argv[1]))
    except (OSError, ValueError, KeyError, TypeError, IndexError) as error:
        print(f"Auditoría no verificable: {error}", file=sys.stderr)
        sys.exit(2)
