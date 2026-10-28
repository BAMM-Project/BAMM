"""Pré-remplit la base communautaire pour la vidéo de démonstration.

Le numéro 01 66 77 88 99 devient « arnaqueur connu » : signalé par 7 appareils et
vérifié par 120 appareils sur la semaine (il appelle en rafale).

    python seed_demo.py [--url http://localhost:8000]
"""
import argparse

import httpx

DEMO = "0166778899"

ap = argparse.ArgumentParser()
ap.add_argument("--url", default="http://localhost:8000")
url = ap.parse_args().url.rstrip("/")

with httpx.Client(base_url=url, timeout=10) as c:
    for i in range(7):
        c.post("/reports", json={"number": DEMO, "reporter_id": f"demo-reporter-{i:03d}", "region": "Littoral"})
    for i in range(120):
        c.get(f"/numbers/{DEMO}", params={"checker_id": f"demo-device-{i:03d}"})
    print(c.get(f"/numbers/{DEMO}").json())
