# YouTube 영상 분류기 (로컬 개인용)

키워드로 YouTube `search.list` API를 호출해 영상을 수집하고, 각 영상을 **미분류 / 보관 / 보류 / 제외**
상태로 분류·관리하는 로컬 전용 웹앱.

- Spring Boot 3.5.14 / Java 21 / Spring Web · Data JPA / Thymeleaf
- DB: H2 인메모리 (추후 파일 H2 또는 MySQL/PostgreSQL로 전환 용이)

## 실행

JDK 21(`C:\wise\java\jdk-21.0.2`)은 `gradle.properties`에 고정되어 있어 별도 설정 없이 실행된다.
(전역 Gradle 설치 불필요 — wrapper가 자동 처리)

```powershell
.\gradlew.bat bootRun
```

기동 후 브라우저에서 http://localhost:8080 접속.

> **포트 안내:** 현재 PC에서 8080을 다른 프로세스가 점유 중이면 기동에 실패한다.
> 그럴 때는 8080을 비우거나 다른 포트로 실행한다:
> ```powershell
> .\gradlew.bat bootRun --args='--server.port=8081'
> ```

## API Key

검색 화면의 **API Key** 입력란에 YouTube Data API v3 키를 넣는다.
매번 입력하기 번거로우면 `src/main/resources/application.properties`의 아래 줄을 해제해 기본값을 프리필한다
(키는 절대 공개 저장소에 커밋하지 말 것):

```properties
youtube.api.key=발급받은_키
```

> `search.list`는 호출당 쿼터 100유닛(기본 일일 10,000 ≈ 100회)을 소모한다. 페이지 수를 늘리면 그만큼 호출이 늘어난다.

## 화면

- `/videos?status=UNCLASSIFIED|KEEP|HOLD|EXCLUDE` — 상태별 탭 + 카드 목록
- 카드: 썸네일 / 제목(클릭 시 새 탭에서 YouTube) / 설명(150자 초과 시 …) / 채널명 / 게시일
- 상태 버튼(보관·보류·제외)을 누르면 즉시 해당 탭으로 이동(AJAX)
- `/history` — 검색 실행 이력(검색어·수집/신규/중복 건수·시각·오류)

## DB

- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:ytdb`, user `sa`, 비밀번호 없음)
- **인메모리이므로 앱을 재시작하면 데이터가 초기화된다.**
  - 디스크 영속화: `application.properties`의 datasource URL을 `jdbc:h2:file:./data/ytdb`로 변경(코드 동일).
  - 실DB 전환: URL/드라이버 교체 + `spring.jpa.hibernate.ddl-auto=validate` + Flyway 마이그레이션 권장.

## 테스트

```powershell
.\gradlew.bat test
```

- `SearchServiceTest` — 미분류 저장, 실행 내/DB 중복 처리, 기존 상태 보존, API 오류 기록
- `YoutubeSearchClientTest` — `q` 파이프(`|`)의 단일 인코딩(`%7C`) 검증
- `VideoWebTest` — 카드 렌더링(워치 URL·상태 버튼·설명 절단), 상태 변경 API

## 데이터 모델 (정규화)

- `youtube_video` (video_id PK) — 영상 메타
- `video_classification` (video_id PK, @MapsId 1:1) — 분류 상태/메모
- `search_run` (id PK) — 검색 실행 이력
- `search_run_item` (id PK, FK: search_run_id, video_id; UNIQUE(search_run_id, video_id)) — 실행↔영상 연결
