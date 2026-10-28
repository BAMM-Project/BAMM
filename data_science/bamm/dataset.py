"""Générateur du dataset synthétique d'appels BAMM.

Il n'existe pas de dataset public d'appels frauduleux mobile money au Bénin.
Ce module produit un dataset synthétique réaliste et *reproductible* (graine fixe).
Chaque distribution est paramétrée dans `PARAMS` et justifiée dans le notebook.

Principe : on tire d'abord la classe (≈15 % fraude), puis chaque feature
conditionnellement à la classe. On introduit volontairement des zones de
recouvrement (fraudeurs « neufs » sans signalement, commerçants qui appellent
beaucoup, transferts légitimes après un appel familial…) pour que le problème
ne soit pas trivialement séparable.
"""
from __future__ import annotations

import hashlib

import numpy as np
import pandas as pd

# Les 12 départements du Bénin et un poids proche de leur part de population
# (RGPH-4, INSAE 2013, projeté). Les fraudes sont légèrement sur-représentées
# en zone urbaine (Littoral, Atlantique, Ouémé) où le mobile money est plus dense.
REGIONS = [
    "Alibori", "Atacora", "Atlantique", "Borgou", "Collines", "Couffo",
    "Donga", "Littoral", "Mono", "Oueme", "Plateau", "Zou",
]
REGION_WEIGHTS_LEGIT = np.array([8.7, 7.7, 13.9, 12.0, 7.2, 7.4, 5.4, 7.6, 5.0, 10.9, 6.2, 8.5])
REGION_WEIGHTS_FRAUD = np.array([4.0, 3.5, 20.0, 9.0, 5.0, 4.5, 3.0, 19.0, 4.0, 16.0, 5.0, 7.0])

PARAMS = {
    "n_records": 10_000,
    "fraud_rate": 0.15,
    # Heure : les fraudes se concentrent sur les heures de marché (10h-14h)
    "fraud_hour_peak_share": 0.65, "fraud_hour_mu": 12.0, "fraud_hour_sigma": 1.8,
    # Durée : un fraudeur a besoin de 1 à 5 minutes pour convaincre
    "fraud_short_call_share": 0.12,           # victime qui raccroche vite
    "legit_duration_lognorm": (4.3, 0.9),     # médiane ≈ 74 s, longue traîne
    # Signalements communautaires
    "fraud_new_number_share": 0.50,           # numéro neuf : 0 signalement
    "fraud_reports_nb": (1.2, 0.09),          # binomiale négative (n, p) → moy ≈ 12
    "legit_false_report_share": 0.05,         # signalements abusifs / erreurs
    # Fréquence d'appels sur 7 jours
    "fraud_burst_share": 0.55,                # appels en rafale 50-200/semaine
    "legit_business_share": 0.10,             # commerçants, agents, livreurs
    # Numéros officiels
    "legit_official_share": 0.05,
    "fraud_spoof_official_share": 0.004,      # usurpation d'identifiant (rare)
    # USSD de transfert dans les 5 min suivant l'appel
    "fraud_ussd_share": 0.45,
    "legit_ussd_share": 0.06,
}


def _caller_id(rng: np.random.Generator, n: int) -> list[str]:
    raw = rng.integers(0, 10**10, size=n)
    return [hashlib.sha256(str(x).encode()).hexdigest()[:12] for x in raw]


def generate(n: int | None = None, seed: int = 42, params: dict | None = None) -> pd.DataFrame:
    """Génère `n` appels synthétiques. Reproductible pour une graine donnée."""
    p = {**PARAMS, **(params or {})}
    n = n or p["n_records"]
    rng = np.random.default_rng(seed)

    is_fraud = rng.random(n) < p["fraud_rate"]
    nf, nl = int(is_fraud.sum()), int((~is_fraud).sum())
    df = pd.DataFrame(index=range(n))

    # --- call_hour -------------------------------------------------------
    hour = np.empty(n, dtype=int)
    peak = rng.random(nf) < p["fraud_hour_peak_share"]
    fh = np.where(peak,
                  rng.normal(p["fraud_hour_mu"], p["fraud_hour_sigma"], nf),
                  rng.uniform(7, 21, nf))
    hour[is_fraud] = np.clip(np.round(fh), 0, 23)
    # Légitimes : activité diurne large (7h-22h) + un peu de nuit
    lh = np.where(rng.random(nl) < 0.93, rng.normal(14, 3.8, nl), rng.uniform(0, 24, nl))
    hour[~is_fraud] = np.clip(np.round(lh), 0, 23)
    df["call_hour"] = hour

    # --- call_duration_sec ----------------------------------------------
    dur = np.empty(n, dtype=int)
    short = rng.random(nf) < p["fraud_short_call_share"]
    fd = np.where(short, rng.uniform(8, 60, nf), rng.gamma(6.0, 27.0, nf) + 40)
    dur[is_fraud] = np.clip(fd, 5, 420)
    mu, sig = p["legit_duration_lognorm"]
    dur[~is_fraud] = np.clip(rng.lognormal(mu, sig, nl), 3, 1800)
    df["call_duration_sec"] = dur

    # --- n_reports -------------------------------------------------------
    rep = np.zeros(n, dtype=int)
    new = rng.random(nf) < p["fraud_new_number_share"]
    nb_n, nb_p = p["fraud_reports_nb"]
    rep[is_fraud] = np.where(new, 0, 1 + rng.negative_binomial(nb_n, nb_p, nf))
    false_rep = rng.random(nl) < p["legit_false_report_share"]
    rep[~is_fraud] = np.where(false_rep, rng.integers(1, 3, nl), 0)
    df["n_reports"] = rep

    # --- call_frequency_7d ----------------------------------------------
    freq = np.empty(n, dtype=int)
    burst = rng.random(nf) < p["fraud_burst_share"]
    freq[is_fraud] = np.where(burst, rng.integers(50, 201, nf), rng.integers(2, 50, nf))
    business = rng.random(nl) < p["legit_business_share"]
    freq[~is_fraud] = np.where(business, rng.integers(40, 160, nl), rng.negative_binomial(2, 0.2, nl) + 1)
    df["call_frequency_7d"] = freq

    # --- is_registered_operator -----------------------------------------
    official = np.zeros(n, dtype=bool)
    official[is_fraud] = rng.random(nf) < p["fraud_spoof_official_share"]
    official[~is_fraud] = rng.random(nl) < p["legit_official_share"]
    df["is_registered_operator"] = official
    # Un numéro officiel ne reçoit pas de signalement et appelle beaucoup
    df.loc[official & ~is_fraud, "n_reports"] = 0
    df.loc[official & ~is_fraud, "call_frequency_7d"] = rng.integers(
        150, 600, int((official & ~is_fraud).sum()))

    # --- region ------------------------------------------------------------
    region = np.empty(n, dtype=object)
    region[is_fraud] = rng.choice(REGIONS, nf, p=REGION_WEIGHTS_FRAUD / REGION_WEIGHTS_FRAUD.sum())
    region[~is_fraud] = rng.choice(REGIONS, nl, p=REGION_WEIGHTS_LEGIT / REGION_WEIGHTS_LEGIT.sum())
    df["region"] = region

    # --- ussd_after_call ----------------------------------------------------
    ussd = np.zeros(n, dtype=bool)
    ussd[is_fraud] = rng.random(nf) < p["fraud_ussd_share"]
    ussd[~is_fraud] = rng.random(nl) < p["legit_ussd_share"]
    df["ussd_after_call"] = ussd

    df["is_fraud"] = is_fraud
    df.insert(0, "caller_id", _caller_id(rng, n))
    return df


if __name__ == "__main__":
    import argparse
    from pathlib import Path

    ap = argparse.ArgumentParser(description="Génère le dataset synthétique BAMM")
    ap.add_argument("--n", type=int, default=PARAMS["n_records"])
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--out", default=str(Path(__file__).resolve().parents[1] / "data" / "bamm_calls.csv"))
    a = ap.parse_args()
    d = generate(a.n, a.seed)
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    d.to_csv(a.out, index=False)
    print(f"{len(d)} appels → {a.out} ({d.is_fraud.mean():.1%} fraude)")
