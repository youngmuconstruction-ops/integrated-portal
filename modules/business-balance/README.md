# 사업수지 분석 Java 운영판

Node/Express 없이 Java 17 표준 라이브러리만으로 실행되는 사내 서버용 버전입니다.

## 실행

```powershell
cd C:\Users\user\Desktop\사업수지분석_v2\사업수지분석_java
.\build.ps1
.\run.ps1
```

기본 접속 주소:

```text
http://localhost:8080
```

포트를 바꾸려면:

```powershell
$env:PORT=18080
.\run.ps1
```

## 구성

- `src/BizAnalysisServer.java`: Java HTTP 서버 및 `/api/*` 구현
- `public/`: 기존 대시보드/시뮬레이터 화면
- `data/sites.json`: 현장 데이터 저장소
- `uploads/`: 업로드 파일 저장 위치
- `file_to_json.py`: 기존 추출기. 업로드 분석 시 Java 서버가 호출합니다.

## 운영 참고

- 대시보드 조회/저장/삭제/순서 변경은 Java만으로 동작합니다.
- `.xlsx`/PDF 업로드 분석은 Python 추출기를 호출합니다. 사내 서버에서는 `PYTHON` 환경변수에 사용할 Python 실행 파일을 지정하는 것을 권장합니다.
- `.xls` 분석까지 필요하면 해당 Python 환경에 `xlrd`가 필요합니다. 가능하면 원본을 `.xlsx`로 저장해 업로드하는 방식이 안정적입니다.
- 외부 라이브러리 없이 구성했기 때문에 Maven 설치가 없어도 배포할 수 있습니다.
