"""Stockage SQLite de la base communautaire (remplaçable par Firebase/Postgres en production)."""
from __future__ import annotations

import json
import sqlite3
import threading
from pathlib import Path

from .numbers import normalize

OFFICIAL_FILE = Path(__file__).with_name("official_numbers.json")

SCHEMA = """
CREATE TABLE IF NOT EXISTS reports (
    number TEXT NOT NULL, reporter_id TEXT NOT NULL, region TEXT, ts REAL NOT NULL,
    PRIMARY KEY (number, reporter_id));
CREATE TABLE IF NOT EXISTS checks (
    number TEXT NOT NULL, checker_id TEXT NOT NULL, ts REAL NOT NULL);
CREATE INDEX IF NOT EXISTS idx_checks ON checks(number, ts);
"""


class Store:
    def __init__(self, path: str):
        self.path = path
        self.db: sqlite3.Connection | None = None
        self.lock = threading.Lock()
        self.official: dict[str, str] = {}

    def init(self):
        self.db = sqlite3.connect(self.path, check_same_thread=False)
        self.db.executescript(SCHEMA)
        entries = json.loads(OFFICIAL_FILE.read_text(encoding="utf-8"))
        self.official = {normalize(e["number"]): e["label"] for e in entries}

    def close(self):
        if self.db:
            self.db.close()

    def _q(self, sql: str, args=()):
        with self.lock:
            cur = self.db.execute(sql, args)
            rows = cur.fetchall()
            self.db.commit()
            return rows

    def official_label(self, number: str) -> str | None:
        return self.official.get(number)

    def official_numbers(self) -> list[dict]:
        return [{"number": n, "label": l} for n, l in self.official.items()]

    def add_report(self, number: str, reporter: str, region: str | None, ts: float):
        # INSERT OR IGNORE : un appareil ne peut pas gonfler le compteur d'un numéro
        self._q("INSERT OR IGNORE INTO reports VALUES (?,?,?,?)", (number, reporter, region, ts))

    def add_check(self, number: str, checker: str, ts: float):
        self._q("INSERT INTO checks VALUES (?,?,?)", (number, checker, ts))

    def report_counts(self, number: str) -> tuple[int, int]:
        (n,), = self._q("SELECT COUNT(*) FROM reports WHERE number=?", (number,))
        return n, n  # une ligne par (numéro, appareil) : signalements = signaleurs distincts

    def distinct_checkers_since(self, number: str, since: float) -> int:
        (n,), = self._q("SELECT COUNT(DISTINCT checker_id) FROM checks WHERE number=? AND ts>=?", (number, since))
        return n

    def red_numbers(self, threshold: int, since: float) -> list[dict]:
        rows = self._q(
            "SELECT number, COUNT(*) c, MAX(ts) last FROM reports GROUP BY number "
            "HAVING c>=? AND last>=? ORDER BY c DESC", (threshold, since))
        return [{"number": n, "reports": c, "last_report": t} for n, c, t in rows]

    def stats(self, threshold: int) -> dict:
        (reports,), = self._q("SELECT COUNT(*) FROM reports")
        (numbers,), = self._q("SELECT COUNT(DISTINCT number) FROM reports")
        red = len(self.red_numbers(threshold, 0))
        regions = dict(self._q("SELECT COALESCE(region,'?'), COUNT(*) FROM reports GROUP BY 1 ORDER BY 2 DESC"))
        return {"reports": reports, "reported_numbers": numbers, "red_numbers": red, "by_region": regions}
