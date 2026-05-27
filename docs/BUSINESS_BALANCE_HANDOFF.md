# Business Balance Handoff (Codex Session)

Last updated: 2026-05-28 (Asia/Seoul)

## Goal

Integrate the existing business balance dashboard into `youngmuconstruction-ops/integrated-portal` so it can run on an internal Java server environment.

## Workspace Source

Local working copy used in this session:

`C:\Users\user\Desktop\사업수지분석_v2\사업수지분석_java`

Verified local URL during development:

`http://localhost:8080/dashboard_b_workhub.html`

## What Was Built Locally

- Java 17 standard-library HTTP server (no Maven/Spring required)
- Existing dashboard UI kept (`dashboard_b_workhub.html`, `dashboard_simulator.html`)
- API compatibility maintained under `/api/*`
- JSON file storage model using `data/sites.json`
- Upload/parse flow using existing `file_to_json.py`
- PowerShell scripts for build/run

## GitHub Branch for This Work

Branch:

`codex/business-balance-java-module`

Files currently added on this branch:

- `.gitignore`
- `modules/business-balance/README.md`
- `modules/business-balance/build.ps1`
- `modules/business-balance/run.ps1`
- `modules/business-balance/data/sites.example.json`
- `modules/business-balance/uploads/.gitkeep`
- `docs/BUSINESS_BALANCE_HANDOFF.md`

## Data Safety Rules Applied

Not uploaded to GitHub:

- real `data/sites.json`
- sqlite/db files
- uploads
- raw internal PDF/Excel source files
- build output classes/logs

## Deploy Shape (Internal Server)

Expected module run shape after full source sync:

```powershell
cd modules\business-balance
.\build.ps1
$env:PORT=8080
.\run.ps1
```

## Next Continuation Tasks

1. Add full Java source and static HTML assets into `modules/business-balance`.
2. Connect portal page(s) to `http://<internal-host>:8080/dashboard_b_workhub.html`.
3. Add auth/access control in front of this module if required by internal policy.
4. Optionally migrate from JSON file storage to internal DB if concurrency grows.

## Notes For Future Codex CLI Sessions

- Continue from this branch and this handoff document.
- Keep business data excluded from repository.
- Preserve current dashboard API contract while refactoring.
