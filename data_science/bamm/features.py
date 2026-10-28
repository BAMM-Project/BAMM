"""Encodage des features — contrat partagé entre le notebook, le TFLite et l'app Android.

Le vecteur d'entrée du modèle embarqué est construit dans cet ordre exact :
    [numériques bruts…, booléens (0/1)…, one-hot région…]
La normalisation (StandardScaler) est intégrée au modèle Keras exporté, donc
l'app Android n'envoie que des valeurs brutes. L'ordre est écrit dans
`model_meta.json` et relu par `FeatureEncoder.kt`.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from .dataset import REGIONS

TARGET = "is_fraud"

# Modèle complet : les 8 features du document projet (caller_id exclu, c'est un identifiant)
FULL = {
    "numeric": ["call_hour", "call_duration_sec", "n_reports", "call_frequency_7d"],
    "boolean": ["is_registered_operator", "ussd_after_call"],
}
# Modèle « avant décroché » : uniquement ce qui est connu quand le téléphone sonne.
# La durée et l'USSD post-appel n'existent pas encore à ce moment-là.
PRECALL = {
    "numeric": ["call_hour", "n_reports", "call_frequency_7d"],
    "boolean": ["is_registered_operator"],
}


def feature_names(spec: dict) -> list[str]:
    return spec["numeric"] + spec["boolean"] + [f"region_{r}" for r in REGIONS]


def encode(df: pd.DataFrame, spec: dict) -> np.ndarray:
    """DataFrame → matrice float32 (valeurs brutes, région en one-hot à catégories fixes)."""
    num = df[spec["numeric"]].to_numpy(dtype=np.float32)
    boo = df[spec["boolean"]].astype(np.float32).to_numpy()
    reg = np.stack([(df["region"] == r).to_numpy(dtype=np.float32) for r in REGIONS], axis=1)
    return np.hstack([num, boo, reg]).astype(np.float32)


def scaler_stats(X: np.ndarray, spec: dict) -> tuple[np.ndarray, np.ndarray]:
    """Moyenne/variance pour la couche Normalization : seules les colonnes
    numériques sont standardisées, booléens et one-hot passent tels quels."""
    k = len(spec["numeric"])
    mean = np.zeros(X.shape[1], dtype=np.float32)
    var = np.ones(X.shape[1], dtype=np.float32)
    mean[:k] = X[:, :k].mean(axis=0)
    var[:k] = X[:, :k].var(axis=0)
    return mean, var
