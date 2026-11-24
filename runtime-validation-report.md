# Runtime Validation Report

**Generated**: 2026-11-23
**Target**: INGEARQ Spring Boot application
**Scope**: Counter sales; delivery was excluded per user confirmation.

## Summary

| Step | Status | Exit Code | Details |
|------|--------|-----------|---------|
| Startup | PASS | n/a | Spring Boot 4.1.1 / Java 25 became ready on port 3002 and connected to isolated MySQL; process was stopped intentionally after E2E. |
| Java unit tests | PASS | 0 | Maven Surefire: 19 tests, 0 failures, 0 errors, 0 skipped. |
| Browser E2E | PASS | 0 | Four Playwright journeys passed, including cash collection, receipt, PDF output, and sale history. |

**Overall**: PASS for the confirmed counter-sales scope.

## Requirement Verification

| Requirement | Result | Evidence |
|-------------|--------|----------|
| Register a sale and record its payment | PASS | Cash received is required to cover the computed total; the backend calculates and persists the change. Non-cash methods are recorded for the total. |
| Generate and print a receipt | PASS | Boleta/factura data is rendered, print controls remain available, and Playwright Chromium generated a PDF with a `%PDF` header. |
| Continue or navigate after a sale | PASS | The receipt view offers “Nueva venta”; the application also retains its page navigation. |
| Delivery order status | Not applicable | The user confirmed that this application should remain for counter sales only. |

## Environment and Test Evidence

- Java: JDK 25. `JAVA_HOME` in the shell initially pointed to JDK 24; validation used the installed JDK 25 explicitly.
- Node.js: v24.19.0.
- Playwright: v1.63.0 with Chromium installed.
- Docker: unavailable; MySQL was available locally and used as the real database.
- E2E database: isolated `ingearq_e2e`; no tests were run against the daily `ingearq2026` database.
- Startup command: `.\mvnw.cmd spring-boot:run` with `PORT=3002` and `DB_NAME=ingearq_e2e`.
- Java test command: `.\mvnw.cmd test` with JDK 25; 19 passed.
- Browser test command: `npm run test:e2e`; 4 passed, 0 failed.
- Sales E2E checks underpayment validation, cash change, stored sale, receipt details, print invocation, generated PDF output, returning to a new sale, editing/cancelling, inventory restoration, and cash reconciliation.
- Product E2E expectation was corrected to assert fields displayed by the product list; the full suite then passed.

## Known Constraints

- Card, Yape, and transfer methods are recorded as paid for the sale total; no external payment gateway is integrated.
- Delivery tracking and an `ENTREGADO` status are not included, per the confirmed scope.
- Mockito emits a non-failing dynamic-agent warning under JDK 25.
