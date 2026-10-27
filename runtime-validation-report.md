# Runtime Validation Report

**Generated**: 2026-09-28
**Target**: INGEARQ Spring Boot application

## Summary

| Step | Status | Exit Code | Details |
|------|--------|-----------|---------|
| Startup | PASS | 0 | Spring Boot 4.1.1 / Java 25 started on 3001 and 3002; MySQL connection pool initialized |
| Java unit tests | PASS | 0 | Maven Surefire: 17 tests, 0 failures, 0 errors, 0 skipped |
| Browser E2E | PASS | 0 | Four Playwright journeys passed against isolated MySQL schema, including dashboard/API synchronization |

**Overall**: PASS

## Environment and Test Evidence

- Docker: unavailable (not installed).
- Node.js: available, v24.19.0.
- Playwright: available, v1.63.0; Chromium installed.
- Database: local MySQL on `127.0.0.1:3306`; E2E data is isolated in `ingearq_e2e_20260928`, not `ingearq2026`.
- Browser tier: Playwright Chromium, primary.
- Infra tier: real local MySQL, primary; no Docker fallback was needed.
- E2E command: `npm run test:e2e` with `E2E_BASE_URL=http://127.0.0.1:3002`.
- E2E journeys: invalid then valid admin login; dashboard KPI comparison to `/api/productos`, `/api/ventas`, and `/api/caja` plus inventory refresh; product create/edit/deactivate/reactivate; sale create/edit/cancel, stock restoration, cancellation ledger, and cash expense registration.
- Unit command: `./mvnw.cmd test`; 17 tests passed.

## Issues Found and Fixed

| # | Severity | Description | Resolution |
|---|----------|-------------|------------|
| 1 | High | Spring Boot 4.1.1 failed to bind `spring.jackson.serialization.write-dates-as-timestamps` to Jackson 3. | Removed the obsolete property; the API already emits date fields as strings. Application startup now succeeds. |
| 2 | Medium | The Bootstrap bundle SRI digest was stale and the browser blocked the script on six pages. | Updated the digest on all six HTML pages; subsequent browser loads and CRUD flows passed. |

## Known Constraints

- Port 3000 was already occupied by a Node process, so Java was tested on 3001 and 3002 without stopping that process.
- Playwright tests require a separate MySQL schema; `npm run test:e2e:prepare` creates the schema named by `E2E_DB_NAME` (default `ingearq_e2e`).
- Mockito emits a non-failing dynamic-agent warning during Maven tests on Java 25.