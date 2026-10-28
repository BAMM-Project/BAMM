"""BAMM — API de la base communautaire.

Un numéro signalé par `RED_THRESHOLD` utilisateurs *distincts* passe en alerte rouge pour tous.
Chaque vérification d'un numéro par l'app est journalisée : le nombre d'appareils distincts
qui ont reçu un appel de ce numéro sur 7 jours sert d'estimation de `call_frequency_7d`,
feature du modèle que le téléphone ne peut pas mesurer seul.

Lancer :  uvicorn app.main:app --reload   (documentation Swagger sur /docs)
"""
from __future__ import annotations

import os
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

from .numbers import normalize
from .store import Store

RED_THRESHOLD = 3
WEEK = 7 * 24 * 3600

store = Store(os.environ.get("BAMM_DB", "bamm.db"))


@asynccontextmanager
async def lifespan(_: FastAPI):
    store.init()
    yield
    store.close()


app = FastAPI(
    title="BAMM — Base communautaire",
    description="Signalements de numéros frauduleux mobile money (Bénin).",
    version="1.0.0",
    lifespan=lifespan,
)


class ReportIn(BaseModel):
    number: str = Field(..., examples=["+229 01 97 12 34 56"])
    reporter_id: str = Field(..., min_length=8, max_length=64, description="Identifiant anonyme de l'appareil")
    region: str | None = Field(None, examples=["Littoral"])


class NumberStatus(BaseModel):
    number: str
    official: bool
    official_label: str | None = None
    reports: int
    distinct_reporters: int
    call_frequency_7d: int
    level: str = Field(..., description="green (officiel) · red (≥3 signalements) · orange (1-2) · unknown")


def _status(number: str) -> NumberStatus:
    official = store.official_label(number)
    reports, reporters = store.report_counts(number)
    freq = store.distinct_checkers_since(number, time.time() - WEEK)
    if official:
        level = "green"
    elif reporters >= RED_THRESHOLD:
        level = "red"
    elif reporters > 0:
        level = "orange"
    else:
        level = "unknown"
    return NumberStatus(number=number, official=official is not None, official_label=official,
                        reports=reports, distinct_reporters=reporters, call_frequency_7d=freq, level=level)


def _norm_or_422(raw: str) -> str:
    n = normalize(raw)
    if not n:
        raise HTTPException(422, "numéro invalide")
    return n


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/reports", response_model=NumberStatus, status_code=201)
def report(body: ReportIn):
    """Signalement en un geste depuis l'app. Un même appareil ne compte qu'une fois par numéro."""
    n = _norm_or_422(body.number)
    if store.official_label(n):
        raise HTTPException(409, "numéro officiel opérateur : signalement refusé")
    store.add_report(n, body.reporter_id, body.region, time.time())
    return _status(n)


@app.get("/numbers/{number}", response_model=NumberStatus)
def check(number: str, checker_id: str | None = Query(None, min_length=8, max_length=64)):
    """Vérifie un numéro entrant. `checker_id` alimente l'estimation de fréquence d'appels."""
    n = _norm_or_422(number)
    if checker_id:
        store.add_check(n, checker_id, time.time())
    return _status(n)


@app.get("/blocklist")
def blocklist(since: float = 0):
    """Numéros en alerte rouge (pour le cache hors ligne de l'app) + whitelist officielle."""
    return {
        "generated_at": time.time(),
        "red_threshold": RED_THRESHOLD,
        "red": store.red_numbers(RED_THRESHOLD, since),
        "official": store.official_numbers(),
    }


@app.get("/stats")
def stats():
    return store.stats(RED_THRESHOLD)
