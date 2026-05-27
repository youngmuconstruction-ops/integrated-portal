# Integrated Portal CLI Handoff

Last updated: 2026-05-27 (Asia/Seoul)
Repository: youngmuconstruction-ops/integrated-portal

## 1) Project Goal
Build a company integrated portal with 4 entry roles:
- 경영진
- 임원
- 본사
- 현장

Core intent:
- Role-based access and menu routing
- Reuse existing business balance dashboard first
- Expand to integrated DB joins later

## 2) Confirmed Naming/Business Rules
### Business balance stage names
Use these names (not 1차/2차/3차):
1. 예상
2. 실행
3. 정산

### Data term correction
- RAW -> ROW (final direction)
- ROW parsing/storage design will be finalized later using real sample files

## 3) Implemented Prototype Scope
File: prototype.html

Implemented:
- 4-tile main entry UI (경영진/임원/본사/현장)
- Separate login modal flow for 경영진/임원
- Role-based left menu + main content area
- Wider content layout for readability
- Existing business dashboard embedded in main content (iframe)
  - URL: http://192.168.0.114:3001/dashboard_b_workhub.html
  - Includes "새 창 열기" fallback button
  - Includes "전체화면" button
- 본사 > 토건관리부 실행계획 upload demo UI
  - Supports .xlsx/.xls/.pdf/.csv input
  - Local demo storage via browser localStorage
  - Note: currently labels/messages still say RAW in places; business direction is ROW

## 4) Existing System Integration Decision
Decision: show existing business system inside portal content area first (no page transition).
Fallback: open new tab when iframe policy blocks embedding.

Technical checks required on existing server:
- X-Frame-Options
- CSP frame-ancestors
- Auth cookie settings (SameSite/Domain)

## 5) Headquarters/Department Direction
Confirmed initial HQ automation focus:
- 기획관리부: 입퇴거, 입주민 입주 설문
- 토건관리부: 실행계획 파일 업로드 -> ROW-level data handling later

## 6) Database/Architecture Direction (Planned)
Target stack: Java (Spring Boot) on deployment server.

Recommended common keys:
- project_id
- site_id
- department_id
- user_id

Planned outcome:
- Join business balance + move-in/out + survey for integrated KPIs

## 7) GitHub State
Repo: https://github.com/youngmuconstruction-ops/integrated-portal

Current files in repo:
- prototype.html (synced)
- overview.html (synced)
- README.md

Known gap:
- overview.pdf not uploaded yet due current API path limitations for binary upload in this session.

## 8) Immediate Next Steps (CLI-Actionable)
1. Replace remaining RAW labels/messages in prototype with ROW.
2. Add backend API skeleton (Spring Boot):
   - POST /api/execution-plans/upload
   - GET /api/execution-plans
3. Define ROW mapping spec from real sample files (Excel/PDF).
4. Implement parser:
   - Excel: Apache POI
   - PDF: Apache PDFBox
5. Persist structured rows:
   - execution_plan_file
   - execution_plan_row
6. Add role-based auth (RBAC) and executive MFA policy.
7. Validate iframe policies for dashboard host and decide embed vs API rendering by module.

## 9) Suggested Commit/Task Convention
Use task prefix examples:
- feat(portal): ...
- feat(auth): ...
- feat(execution-plan): ...
- chore(docs): ...

## 10) Quick Context for New CLI Session
If a new CLI agent starts, provide this instruction:
"Read docs/CLI_HANDOFF.md first, then continue implementation from section 8 in order."