# StockOS browser smoke testing (simulated Auth and API)

Run locally with Python 3.12 and Chromium:

```bash
python -m pip install playwright==1.56.0
python -m playwright install chromium
python -m unittest discover -s tests/browser -p 'test_*.py' -v
```

Four browser tests exercise the **real static HTML/CSS/JS**:
- Login and a tab through item delivery, Pix payment and close.
- One goods-receipt submission containing multiple products.
- Attendant sees no manager or owner operations.
- Suspended license hides checkout/cash operations.

Only in this test harness, the Supabase JS CDN module is intercepted and
replaced with a fake session; API calls are responded to by a stateful
in-process fake. No real credentials, Supabase project, payment provider or
customer data are accessed. These tests **do not establish authentication,
database isolation, business transaction correctness or full E2E coverage**.
Those require separate server-side integration tests and later staging
with a dedicated Auth project, JWT signatures and PostgreSQL.

CI runs these tests in addition to PostgreSQL and unit tests, not instead of
them. Do not interpret a browser smoke PASS as commercial readiness.
