"""
StockOS browser smoke tests with fake browser Auth and fake HTTP API.

These tests check front-end navigation, forms, basic flows and role/plan
visibility. They DO NOT validate a real Supabase account, a signed JWT or
PostgreSQL persistence. Backend checks are covered by separate CI jobs.
"""
import functools
import json
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
import unittest
from urllib.parse import urlsplit

from playwright.sync_api import sync_playwright


STATIC_ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/static"
MOCK_AUTH_MODULE = """
const listeners=[];
let loggedIn=false;
const token={access_token:'STOCKOS_BROWSER_TEST_NOT_A_REAL_JWT'};
export function createClient(){
  return {auth:{
    getSession:async()=>({data:{session:loggedIn?token:null}}),
    getUser:async()=>({data:{user:loggedIn?{id:'owner-fixture'}:null}}),
    signInWithPassword:async()=>{loggedIn=true;return {data:{user:{id:'owner-fixture'}},error:null};},
    signOut:async()=>{loggedIn=false;for(const cb of listeners)cb('SIGNED_OUT',null);return {error:null};},
    onAuthStateChange:(cb)=>{listeners.push(cb);return {data:{subscription:{unsubscribe(){}}}};}
  }};
}
"""


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, _format, *_args):
        pass


class FakeStoreApi:
    def __init__(self, role="PROPRIETARIO", licensed=True):
        self.role = role
        self.licensed = licensed
        self.requests = []
        self.tabs = []
        self.payments = {}
        self.items = {}
        self.shift_open = False
        self.products = [
            {"id": "produto-agua", "codigo": "A001", "nome": "Água",
             "preco_unitario": "10.00", "estoque_atual": 10, "ativo": True,
             "versao_cadastro": 0},
            {"id": "produto-refri", "codigo": "R002", "nome": "Refrigerante",
             "preco_unitario": "8.00", "estoque_atual": 5, "ativo": True,
             "versao_cadastro": 0},
        ]
        self.receipts = []
        self.invites = []

    def respond(self, route):
        request = route.request
        path = urlsplit(request.url).path
        method = request.method
        self.requests.append((method, path, dict(request.headers)))
        if path == "/api/saas/config":
            return self.json(route, {"supabaseUrl": "https://fixture.supabase.co",
                                     "publishableKey": "sb_publishable_browser_test_not_real"})
        if request.headers.get("authorization") != "Bearer STOCKOS_BROWSER_TEST_NOT_A_REAL_JWT":
            return self.json(route, {"detail": "Token obrigatório"}, 401)
        if path == "/api/saas/empresas" and method == "GET":
            return self.json(route, [{"id": "empresa-fixture", "nome": "Loja Piloto",
                                      "papel": self.role}])
        if path == "/api/saas/empresas/empresa-fixture/licenca":
            return self.json(route, {"status": "ATIVA" if self.licensed else "SUSPENSA",
                                     "acesso_liberado": self.licensed,
                                     "plano": "TESTE",
                                     "vigencia_fim": "2027-01-01T00:00:00Z"})
        if path == "/api/saas/empresas/empresa-fixture/filiais":
            return self.json(route, [{"id": "filial-fixture", "nome": "Principal"}])
        if not self.licensed:
            return self.json(route, {"detail": "Licença suspensa"}, 403)

        if path == "/api/saas/empresas/empresa-fixture/convites":
            if self.role != "PROPRIETARIO":
                return self.json(route, {"detail": "Permission denied"}, 403)
            if method == "GET":
                return self.json(route, self.invites)
            if method == "POST":
                data = request.post_data_json
                item = {
                    "id": "invitation-" + str(len(self.invites) + 1),
                    "email_normalizado": data["email"].lower(),
                    "papel": data["papel"],
                    "status": "PENDENTE",
                    "expira_em": "2027-12-01T00:00:00Z"
                }
                self.invites.append(item)
                return self.json(route, {**item, "codigoConvite": "A" * 43}, 201)
        if "/convites/" in path and method == "DELETE":
            ident = path.rsplit("/", 1)[-1]
            target = next(inv for inv in self.invites if inv["id"] == ident)
            target["status"] = "REVOGADO"
            return route.fulfill(status=204, body="")
        if path == "/api/saas/empresas/empresa-fixture/fornecedores":
            if method == "GET":
                return self.json(route, [{"id": "fornecedor-a", "nome": "Distribuidora",
                                          "ativo": True}])
        if path.endswith("/funcionarios") and method == "GET":
            return self.json(route, [{"auth_user_id": "owner-fixture",
                                      "papel": self.role, "ativo": True}])
        if path.endswith("/funcionarios/auditoria"):
            return self.json(route, [])
        if path.endswith("/produtos/ajustes"):
            return self.json(route, [])
        if path.endswith("/relatorios/financeiro"):
            return self.json(route, {"total_recebido": "0.00",
                                     "total_estornado": "0.00",
                                     "total_devolvido": "0.00",
                                     "fluxo_liquido_registrado": "0.00"})
        if path.endswith("/relatorios/estoque-baixo"):
            return self.json(route, [])
        if path.endswith("/relatorios/produtos-mais-entregues"):
            return self.json(route, [])
        if path.endswith("/entradas-mercadoria"):
            if method == "GET":
                return self.json(route, self.receipts)
            if method == "POST":
                payload = request.post_data_json
                for row in payload["itens"]:
                    next(p for p in self.products if p["id"] == row["produtoId"])["estoque_atual"] += row["quantidade"]
                total = sum(i["quantidade"] * float(i["custoUnitario"]) for i in payload["itens"])
                result = {"id": f"entrada-{len(self.receipts)+1}",
                          "fornecedor_nome": "Distribuidora",
                          "documento_referencia": payload["documentoReferencia"],
                          "custo_total": total, "linhas": len(payload["itens"])}
                self.receipts.append(result)
                return self.json(route, result, 201)

        if path.endswith("/produtos") and method == "GET":
            return self.json(route, self.products)
        if path.endswith("/caixa/atual"):
            if not self.shift_open:
                return self.json(route, {"status": "SEM_CAIXA_ABERTO"})
            return self.json(route, {"status": "ABERTO", "id": "turno-fixture",
                                     "valor_abertura": "50.00", "dinheiro_esperado": "50.00",
                                     "dinheiro": "0.00", "pix": "0.00",
                                     "debito": "0.00", "credito": "0.00",
                                     "sangrias": "0.00", "suprimentos": "0.00"})
        if path.endswith("/caixa/historico"):
            return self.json(route, [])
        if path.endswith("/caixa/abrir") and method == "POST":
            self.shift_open = True
            return self.json(route, {"id": "turno-fixture", "status": "ABERTO"}, 201)
        if path.endswith("/comandas/fechadas"):
            return self.json(route, [])
        if path.endswith("/comandas"):
            if method == "GET":
                return self.json(route, [
                    {**tab, "total": sum(i["quantidade"]*float(i["preco_unitario_snapshot"])
                                         for i in self.items.get(tab["id"], [])),
                     "pago": sum(float(p["valor"])
                                 for p in self.payments.get(tab["id"], []))}
                    for tab in self.tabs if tab["status"] == "ABERTA"
                ])
            if method == "POST":
                payload = request.post_data_json
                tab = {"id": f"tab-{len(self.tabs)+1}", "numero": payload["numero"],
                       "status": "ABERTA", "total": 0, "pago": 0}
                self.tabs.append(tab)
                return self.json(route, tab, 201)
        if "/comandas/" in path:
            segments = path.split("/comandas/", 1)[1].split("/")
            tab_id = segments[0]
            if len(segments) > 1 and segments[1] == "itens":
                if method == "GET":
                    return self.json(route, self.items.get(tab_id, []))
                if method == "POST":
                    payload = request.post_data_json
                    product = next(p for p in self.products if p["id"] == payload["produtoId"])
                    product["estoque_atual"] -= payload["quantidade"]
                    item = {"id": f"item-{len(self.items.get(tab_id, []))+1}",
                            "quantidade": payload["quantidade"],
                            "nome_snapshot": product["nome"],
                            "preco_unitario_snapshot": product["preco_unitario"],
                            "cancelado_em": None}
                    self.items.setdefault(tab_id, []).append(item)
                    return self.json(route, item, 201)
            if len(segments) > 1 and segments[1] == "pagamentos":
                if method == "GET":
                    return self.json(route, self.payments.get(tab_id, []))
                if method == "POST":
                    payload = request.post_data_json
                    payment = {"id": f"pay-{len(self.payments.get(tab_id, []))+1}",
                               "modalidade": payload["modalidade"],
                               "valor": payload["valor"], "estorno_id": None}
                    self.payments.setdefault(tab_id, []).append(payment)
                    return self.json(route, payment, 201)
            if len(segments) > 1 and segments[1] == "fechar" and method == "POST":
                tab = next(t for t in self.tabs if t["id"] == tab_id)
                tab["status"] = "FECHADA"
                return self.json(route, tab)
        raise AssertionError(f"Unexpected mocked API request: {method} {path}")

    @staticmethod
    def json(route, body, status=200):
        route.fulfill(status=status, content_type="application/json",
                      body=json.dumps(body, ensure_ascii=False))


class StorefrontBrowserSmoke(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        handler = functools.partial(QuietHandler, directory=str(STATIC_ROOT))
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = f"http://127.0.0.1:{cls.server.server_port}/saas-comandas.html"
        cls.playwright = sync_playwright().start()
        cls.browser = cls.playwright.chromium.launch(headless=True)

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self):
        self.page = self.browser.new_page()
        self.page_errors = []
        self.page.on("pageerror", lambda error: self.page_errors.append(str(error)))

    def tearDown(self):
        self.page.close()

    def visit(self, store):
        self.page.route("https://esm.sh/**", lambda route: route.fulfill(
            status=200, content_type="text/javascript",
            headers={"access-control-allow-origin": "*"}, body=MOCK_AUTH_MODULE))
        self.page.route("**/api/**", store.respond)
        self.page.goto(self.url)
        self.page.locator("#email").fill("pilot@example.com")
        self.page.locator("#password").fill("not-a-real-password")
        self.page.locator("#login-form button").click()
        self.page.locator("#workspace").wait_for(state="visible")
        return store

    def test_full_tab_checkout_flow(self):
        store = self.visit(FakeStoreApi())
        self.page.locator("#cash-open-form").wait_for(state="visible")
        self.page.locator("#cash-open-form button").click()
        self.page.locator("#cash-active").wait_for(state="visible")
        self.page.locator("#numero").fill("1")
        self.page.locator("#open-form button").click()
        self.page.locator("#comandas .tab").first.wait_for()
        self.page.locator("#comandas .tab").first.click()
        self.page.locator("#detail").wait_for(state="visible")
        self.page.locator("#quantidade").fill("2")
        self.page.locator("#item-form button").click()
        self.page.get_by_text("2 × Água", exact=False).wait_for()
        self.page.locator("#modalidade").select_option("PIX")
        self.page.locator("#valor").fill("20")
        self.page.locator("#pay-form button").click()
        self.page.get_by_text("PIX · R$ 20,00", exact=False).wait_for()
        self.page.locator("#close-tab").click()
        self.page.get_by_text("Nenhuma comanda aberta.").wait_for()
        self.assertEqual(store.products[0]["estoque_atual"], 8)
        self.assertEqual(store.tabs[0]["status"], "FECHADA")
        self.assertFalse(self.page_errors, self.page_errors)

    def test_multi_product_receipt_is_single_submission(self):
        store = self.visit(FakeStoreApi())
        self.page.locator("#goods-panel").wait_for(state="visible")
        self.page.locator("#receipt-document").fill("NOTA-123")
        self.page.locator("#receipt-quantity").fill("3")
        self.page.locator("#receipt-unit-cost").fill("2.50")
        self.page.locator("#receipt-add").click()
        self.page.locator("#receipt-product").select_option("produto-refri")
        self.page.locator("#receipt-quantity").fill("2")
        self.page.locator("#receipt-unit-cost").fill("4.00")
        self.page.locator("#receipt-add").click()
        self.page.get_by_text("Total da entrada: R$ 15,50").wait_for()
        self.page.once("dialog", lambda d: d.accept())
        self.page.locator("#receipt-confirm").click()
        self.page.wait_for_timeout(650)
        self.assertEqual(len(store.receipts), 1, "POST receipt missing. UI feedback: "
                         + str(self.page.locator("#feedback").text_content())
                         + " | API calls: " + str([(m, p) for m, p, _ in store.requests][-12:])
                         + " | JS errors: " + str(self.page_errors))
        self.page.get_by_text("NOTA-123", exact=False).wait_for()
        self.assertEqual(len(store.receipts), 1)
        self.assertEqual(store.receipts[0]["linhas"], 2)
        self.assertEqual(store.products[0]["estoque_atual"], 13)
        self.assertEqual(store.products[1]["estoque_atual"], 7)
        self.assertFalse(self.page_errors, self.page_errors)

    def test_invitation_create_and_revoke_without_reexposing_code(self):
        store = self.visit(FakeStoreApi())
        self.page.locator("#employees-panel").wait_for(state="visible")
        self.page.locator("#invite-email").fill("NEW.WORKER@example.com")
        self.page.locator("#invite-role").select_option("GERENTE")
        self.page.once("dialog", lambda dialog: dialog.accept())
        self.page.locator("#invite-create-form button").click()
        self.page.get_by_text("Código de convite (exibido uma vez):", exact=False).wait_for()
        self.assertEqual(len(store.invites), 1)
        self.assertEqual(store.invites[0]["papel"], "GERENTE")
        self.assertEqual(store.invites[0]["email_normalizado"], "new.worker@example.com")
        self.page.once("dialog", lambda dialog: dialog.accept())
        self.page.locator("#invite-list button").click()
        self.page.get_by_text("REVOGADO", exact=False).wait_for()
        self.assertEqual(store.invites[0]["status"], "REVOGADO")
        self.page.locator("#invite-list-refresh").click()
        self.assertIn("Código de convite", self.page.locator("#invite-token-output").text_content())
        # The read-only invitation listing never returns the secret token.
        self.assertFalse(any("codigoConvite" in row for row in store.invites))
        self.assertFalse(self.page_errors, self.page_errors)

    def test_attendant_cannot_see_manager_controls(self):
        self.visit(FakeStoreApi(role="ATENDENTE"))
        for element in ["#goods-panel", "#catalog-panel", "#inventory-panel",
                        "#employees-panel", "#refunds-panel"]:
            self.assertFalse(self.page.locator(element).is_visible(), element)
        self.page.locator("#cash-open").wait_for(state="visible")
        self.assertFalse(self.page_errors, self.page_errors)

    def test_suspended_license_hides_operations(self):
        store = self.visit(FakeStoreApi(licensed=False))
        self.page.get_by_text("Operações bloqueadas", exact=False).wait_for()
        self.assertFalse(self.page.locator(".bar").is_visible())
        self.assertFalse(self.page.locator("#cash-panel").is_visible())
        self.assertFalse(any("/comandas" in req[1] for req in store.requests))
        self.assertFalse(self.page_errors, self.page_errors)


if __name__ == "__main__":
    unittest.main()
