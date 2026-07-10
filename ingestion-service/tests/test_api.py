from fastapi.testclient import TestClient
from app.main import app, get_repo

class FakeRepo:
    def __init__(self): self._s=[]
    def list_sources(self, enabled_only=False): return self._s
    def add_source(self, name, url):
        from app.models import Source
        s=Source(len(self._s)+1,name,url,True,None); self._s.append(s); return s
    def set_source_enabled(self, sid, enabled): pass
    def latest_run(self): return None

def test_add_and_list_source():
    app.dependency_overrides[get_repo] = lambda: FakeRepo()
    c = TestClient(app)
    r = c.post("/api/ingestion/sources", json={"name":"KZ","tmeUrl":"https://t.me/s/kz"},
               headers={"X-User-Role":"ADMIN"})
    assert r.status_code == 201 and r.json()["name"] == "KZ"
    app.dependency_overrides.clear()

def test_non_admin_forbidden():
    app.dependency_overrides[get_repo] = lambda: FakeRepo()
    c = TestClient(app)
    r = c.get("/api/ingestion/sources")  # no X-User-Role header
    assert r.status_code == 403
    app.dependency_overrides.clear()
