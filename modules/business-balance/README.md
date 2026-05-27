# Business Balance Java Module

This module contains the Java operating version of the business balance dashboard prepared from the local project at `C:\Users\user\Desktop\사업수지분석_v2\사업수지분석_java`.

## Purpose

- Run the existing business analysis dashboard on an internal company server without Node/Express.
- Keep the existing dashboard API shape under `/api/*` so the current HTML screens can continue to work.
- Store runtime data in `data/sites.json` on the server.
- Keep real operating data, uploaded documents, generated build output, and SQLite files out of GitHub.

## Local Run Shape

```powershell
cd modules\business-balance
.\build.ps1
.\run.ps1
```

Default URL:

```text
http://localhost:8080/dashboard_b_workhub.html
```

To change the port:

```powershell
$env:PORT = "18080"
.\run.ps1
```

## Current Upload Note

The full working source was prepared locally in the workspace and packaged without real data. This branch includes the operating handoff and safe scaffold first; actual `data/sites.json`, PDFs, Excels, build classes, and uploads must stay off GitHub.

See `docs/BUSINESS_BALANCE_HANDOFF.md` for the continuation notes from the Codex CLI/browser session.