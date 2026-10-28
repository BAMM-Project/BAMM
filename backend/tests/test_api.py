import importlib

import pytest
from fastapi.testclient import TestClient


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("BAMM_DB", str(tmp_path / "test.db"))
    import app.main as main
    importlib.reload(main)
    with TestClient(main.app) as c:
        yield c


def test_normalize():
    from app.numbers import normalize
    assert normalize("+229 01 97 12 34 56") == "0197123456"
    assert normalize("00229 0197123456") == "0197123456"
    assert normalize("97 97 001") == "9797001"
    assert normalize("abc") == ""


def test_three_distinct_reporters_turn_red(client):
    n = "+229 01 66 77 88 99"
    for i, expected in enumerate(["orange", "orange", "red"]):
        r = client.post("/reports", json={"number": n, "reporter_id": f"device-{i:04d}", "region": "Littoral"})
        assert r.status_code == 201
        assert r.json()["level"] == expected
    assert client.get("/blocklist").json()["red"][0]["number"] == "0166778899"


def test_same_device_counts_once(client):
    for _ in range(5):
        client.post("/reports", json={"number": "0166778899", "reporter_id": "device-0001"})
    s = client.get("/numbers/0166778899").json()
    assert s["distinct_reporters"] == 1 and s["level"] == "orange"


def test_official_number_is_green_and_cannot_be_reported(client):
    assert client.get("/numbers/97 97 001").json()["level"] == "green"
    r = client.post("/reports", json={"number": "97 97 001", "reporter_id": "device-0001"})
    assert r.status_code == 409


def test_checks_estimate_call_frequency(client):
    for i in range(4):
        client.get("/numbers/0155555555", params={"checker_id": f"device-{i:04d}"})
    client.get("/numbers/0155555555", params={"checker_id": "device-0000"})
    assert client.get("/numbers/0155555555").json()["call_frequency_7d"] == 4


def test_invalid_number(client):
    assert client.get("/numbers/xx").status_code == 422
