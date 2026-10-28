"""Normalisation des numéros béninois (même règle que `PhoneNumbers.kt` côté Android)."""
import re


def normalize(raw: str) -> str:
    """Garde les chiffres, retire l'indicatif +229 / 00229.

    « +229 01 97 12 34 56 » → « 0197123456 ». Retourne "" si le résultat n'est pas plausible.
    """
    d = re.sub(r"\D", "", raw or "")
    if d.startswith("00229"):
        d = d[5:]
    elif d.startswith("229") and len(d) > 10:
        d = d[3:]
    return d if 3 <= len(d) <= 15 else ""
