# 카테고리 내비게이션 및 백스택/오버레이 요구사항 명세서 (SRS)

## 1. 배경 및 핵심 문제 정의

본 프로젝트는 차량용 와이드 디스플레이(Wide Split Mode)와 태블릿/스마트폰(Narrow Overlay Mode)을 모두 지원하는 통합 내비게이션 아키텍처를 목표로 합니다.
특히 Narrow Mode에서는 **전체 화면 콘텐츠 레이어** 위에 **항상 최상단(Always-On-Top)으로 상주하는 플로팅 메뉴 오버레이(`PrimaryOverlayActivity`)**가 공존합니다.

이 환경에서 다음과 같은 안드로이드 기본 태스크 메커니즘의 충돌이 발생했습니다.

---

## 2. 5대 핵심 요구사항 (Core Requirements)

### 요구사항 1. [기동 화면] 앱 시작 시 항상 Home 화면이 루트로 보여야 한다.
- 앱 콜드 스타트 및 런처 진입 시 가장 바닥(Task Root)에는 항상 `HomeActivity`가 렌더링되어야 합니다.
- `HomeActivity`는 검색창 및 기본 위젯 상태를 유지하는 베이스라인입니다.

### 요구사항 2. [카테고리 진입] 카테고리 선택 시 Home 위에 스택으로 쌓여야 한다.
- 사용자가 메뉴(Primary 또는 Overlay)에서 카테고리(Seat, Door, Light 등)를 선택하면 `HomeActivity`가 종료되는 것이 아니라, **`HomeActivity` 위에 해당 카테고리 화면이 스택 형태로 중첩(Stacked)**되어야 합니다.

### 요구사항 3. [카테고리 전환 & 백키 복귀] 카테고리 변경 시 Home은 유지되고 상위 카테고리 스택만 교체되어야 한다.
- **카테고리 A → 카테고리 B 전환 시**:
  - `HomeActivity`는 절대 파괴(`onDestroy`)되거나 재시작되지 않고 기존 검색어/스크롤 상태를 유지해야 합니다.
  - 카테고리 A 및 그 하위 상세 화면(Depth >= 2)들은 스택에서 일괄 정리(Dismiss)되고, 카테고리 B 스택으로 교체되어야 합니다.
- **요구사항 3.1. [내부/외부 앱 카테고리 간 상호 전환 보장]**:
  - 내부 앱 카테고리(Seat, Door)뿐 아니라 **외부 프로세스 앱 카테고리(Vehicle Lights, `LightSettingsActivity`)**가 활성화된 상태에서도, 메뉴나 오버레이에서 다른 카테고리를 선택하면 해당 외부앱 TaskFragment의 토큰을 식별하여 안전하게 일괄 정리(`finishActivityStacks`)하고 새 카테고리로 원자적 교체가 이루어져야 합니다.
- **요구사항 3.2. [외부앱 Back 키 복귀 시 Home 노출 및 Overlay 선택 동기화]**:
  - 외부 프로세스 앱 카테고리 화면에서 Back 키를 눌러 해당 카테고리 스택이 모두 소진되면, 중간 징검다리 지연 없이 **바닥의 `HomeActivity`가 즉시 노출**되어야 합니다.
  - 이 복귀 신호는 즉시 `HomeNavigationBridge`를 거쳐 **`PrimaryActivity`(Wide Mode) 및 `PrimaryOverlayActivity`(Narrow Mode)의 리스트 선택 상태를 `Home`(0번 인덱스)으로 실시간 자동 동기화**해야 합니다.
- **Home에서의 Back 키 동작**:
  - `HomeActivity`에서 다시 Back을 누르면 앱이 완전히 종료(`finishAffinity`)되어야 합니다.

### 요구사항 4. [CLEAR_TOP 실패 분석] Home의 `CLEAR_TOP / onNewIntent` 방식의 한계
- **시도했던 방식**:
  - 카테고리를 변경할 때마다 `HomeActivity`로 `Intent.FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`을 날려 Home 위의 이전 카테고리 액티비티들을 한 번에 청소하고자 했습니다.
- **발생한 치명적 결함**:
  - 안드로이드 OS(`system_server`의 `Task.java`)는 `CLEAR_TOP` 플래그를 처리할 때, 해당 Task 내에서 `HomeActivity`보다 위에 존재하는 **모든 액티비티를 강제 종료(`finish`)**시킵니다.
  - Narrow Mode의 플로팅 메뉴인 `PrimaryOverlayActivity` 역시 동일한 Task 내 최상단 TaskFragment에 위치하므로, **`CLEAR_TOP`이 실행되는 순간 오버레이 메뉴까지 함께 소멸(Destroy)**되어 버렸습니다.

### 요구사항 5. [중간 ActivityStack 격리] Home과 Overlay 사이에 전용 TaskFragment 배치
- **채택된 솔루션 (Option A)**:
  - `CLEAR_TOP`을 쓸 수 없으므로, **`HomeActivity`(바닥)**와 **`PrimaryOverlayActivity`(최상단)** 사이에 **`CategoryHostActivity` 전용 독립 TaskFragment(`ActivityStack`)**를 샌드위치처럼 배치했습니다.
  - 카테고리 전환 시 `HomeActivity`와 `Overlay`는 건드리지 않고, **가운데 끼어있는 카테고리 전용 TaskFragment만 `finishActivityStacks()`로 원자적으로 제거**하고 새 카테고리 TaskFragment로 바꿔치기합니다.

---

## 3. 단위/통합 테스트(UT) 반영 매핑 현황

현재 본 5대 요구사항은 [`InternalBackStackUiAutomatorTest.kt`](../../app/src/androidTest/java/com/example/lifecycleapp/InternalBackStackUiAutomatorTest.kt)에 100% 반영되어 정밀 검증되고 있습니다.

| 요구사항 번호 | 검증 시나리오 (UI Automator Test) | 검증 내용 및 Assertion |
| :--- | :--- | :--- |
| **요구사항 1, 2** | `scenario10_WideMode_InternalCategory_ExhaustBackStack...`<br/>`scenario12_NarrowOverlayMode_InternalAndExternal...` | 콜드 스타트 시 Home의 검색창 노출 확인. 카테고리 선택 시 Home 위에 Seat 카테고리 뷰가 렌더링되는지 확인. |
| **요구사항 3** | `scenario10`, `scenario11`, `scenario12` | 1. Home에 검색어 `"Lumbar"` 입력 후 카테고리 진입.<br/>2. 카테고리 서브 화면 진입 후 Back 연타 시 이전 카테고리가 아닌 **Home이 이전 검색어("Lumbar")를 보존한 채 즉시 복원**되는지 확인.<br/>3. Home에서 Back 1회 더 누르면 `finishAffinity()`로 앱 종료 확인. |
| **요구사항 4, 5** | `scenario8_NarrowOverlayMode_PrimaryOverlayAndHomeNeverDestroyed...` | Narrow Mode에서 카테고리를 `Home -> Seat -> Door -> Light -> Home`으로 연속 전환하는 동안, **`PrimaryOverlayActivity`와 `HomeActivity`가 단 한 번도 파괴(`ON_DESTROY`)되거나 재생성(`ON_CREATE`)되지 않고 완전히 보존**되는지 라이프사이클 콜백으로 검증 (`overlayLifecycleEvents.isEmpty()`, `homeLifecycleEvents.isEmpty()`). |
| **무감쇠 스택 교체** | `scenario9_WideAndNarrowModes_NoActivityRecreatedByConfigChange` | 카테고리 교체 과정에서 Configuration Change로 인한 불필요한 액티비티 파괴/재생성이 전혀 없는지 전수 검증. |

---

## 4. 아키텍처 다이어그램 (레이어 격리 구조)

```
[ Narrow Mode Task 계층 구조 ]

┌────────────────────────────────────────────────────────┐
│  TaskFragment #2 : PrimaryOverlayActivity (Always-On)  │ ◀── Top (80dp 플로팅 메뉴)
├────────────────────────────────────────────────────────┤
│  TaskFragment #1 : Category Stack (동적 교체 대상)     │ ◀── [Seat / Door / Light]
│    - CategoryHostActivity (Root)                       │     카테고리 전환 시 이 영역만
│    - GenericSettingsActivity (Depth 2)                 │     finishActivityStacks()로 삭제
│    - External Plugin Activity (Depth 3)                │
├────────────────────────────────────────────────────────┤
│  TaskFragment #0 : HomeActivity (Persistent Base)      │ ◀── Bottom (검색창/상태 보존)
└────────────────────────────────────────────────────────┘
```
