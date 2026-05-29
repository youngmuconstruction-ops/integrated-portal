# Business Balance Module Handoff

Last updated: 2026-05-28 (Asia/Seoul)
Target repository: `youngmuconstruction-ops/integrated-portal`

## Goal

Integrate the existing business balance dashboard into the company integrated portal so it can run on an internal server as a Java-based service.

## Current Local Outputs

Local source folder:

```text
C:\Users\user\Desktop\사업수지분석_v2\사업수지분석_java
```

Current verified local URL:

```text
http://localhost:8080/dashboard_b_workhub.html
```

## Decisions From Conversation

- Keep the existing prototype/dashboard versions intact.
- Create a separate improved Java operating version.
- Use Java 17 because it is installed locally.
- Maven is not currently available, so the first Java version uses only the JDK standard library.
- Keep the existing HTML dashboard and simulator UI.
- Replace the Node/Express API with a Java HTTP server that preserves the same `/api/*` contract.
- Store runtime data in `data/sites.json` for the no-dependency Java version.
- Do not upload actual business data, SQLite DB files, uploaded Excel/PDF files, or runtime logs to GitHub.
- Keep upload extraction as a Python subprocess for now.
- Use `PYTHON` environment variable on the internal server when a specific Python runtime is needed.

## Implemented Locally

- Java API server:
  - `src/BizAnalysisServer.java`
- Static dashboard:
  - `public/dashboard_b_workhub.html`
  - `public/dashboard_simulator.html`
  - `public/index.html`
- Runtime scripts:
  - `build.ps1`
  - `run.ps1`
- Data:
  - Local runtime: `data/sites.json` (not uploaded)
  - GitHub-safe sample: `data/sites.example.json`
- Existing extractor:
  - `file_to_json.py`

## Verified Locally

- `javac` build passed on Java 17.
- `/api/health` returned success.
- `/api/sites` returned 7 local copied sites.
- Dashboard loaded in browser with 7 site cards.
- Detail simulator loaded for `익산 송학동3차`.

## Intended GitHub Layout

```text
integrated-portal/
  modules/
    business-balance/
      src/
        BizAnalysisServer.java
      public/
        dashboard_b_workhub.html
        dashboard_simulator.html
        index.html
      data/
        sites.example.json
      uploads/
        .gitkeep
      build.ps1
      run.ps1
      file_to_json.py
      README.md
  docs/
    BUSINESS_BALANCE_HANDOFF.md
```

## Internal Server Deployment Shape

Recommended first deployment:

```powershell
cd modules\business-balance
.\build.ps1
$env:PORT=8080
.\run.ps1
```

Portal iframe target after deployment:

```text
http://<internal-server-ip>:8080/dashboard_b_workhub.html
```

The existing `prototype.html` currently points to the prior Node dashboard host. Once the Java server host is fixed, update `menus.businessUrl` to the Java service URL.

## Known Limitations

- This is not yet a Spring Boot service.
- There is no integrated authentication/RBAC yet.
- `data/sites.json` is file-based storage and should later be migrated to an internal DB.
- PDF detail extraction is less reliable than Excel extraction.
- `.xls` extraction requires `xlrd` in the configured Python runtime, or users should save files as `.xlsx`.

## Recommended Next Steps

1. Decide the internal server URL and port for the Java service.
2. Update `prototype.html` `businessUrl` to that URL.
3. Add login/RBAC integration between portal and business balance module.
4. Replace JSON file storage with internal DB tables.
5. Move parsing from Python subprocess to Java libraries when Maven/Gradle is available:
   - Excel: Apache POI
   - PDF: Apache PDFBox
6. Add GitHub Actions Java compile check.
7. Add sanitized test fixtures, never real 사업수지 source files.
