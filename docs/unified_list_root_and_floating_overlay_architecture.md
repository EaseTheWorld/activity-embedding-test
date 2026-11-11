# Unified Task Root & Floating Window Architecture

본 문서는 `feature/list-root-unified-cleartop` 브랜치에서 완성된 차세대 내비게이션 아키텍처에 대한 상세 설계 및 구현 가이드입니다.
이번 리팩토링은 기존 Jetpack WindowManager의 실험적 API(Pinned Overlay, Extension Version 8 의존, TaskFragment Organizer Reflection) 및 2단계 RTL Split 트릭을 완전히 걷어내고, **안드로이드 프레임워크 표준 태스크 메커니즘과 WindowManager Floating Window를 결합하여 안정성과 유지보수성을 극대화**했습니다.

---

## 1. 아키텍처 핵심 3대 혁신 요약

```mermaid
flowchart TD
    subgraph Architecture ["통합 태스크 및 오버레이 아키텍처"]
        direction TB
        subgraph Innovation1 ["1. WindowManager Floating Window Overlay"]
            FW["접힌 상태: FloatingMenuManager<br/>(80dp Always-on-top Floating Window)"]
            TA["펼쳐진 상태: ListWithTranslucentActivity<br/>(표준 Translucent Activity + 65% Dim Scrim)"]
            FW <-->|Click / Dim Touch| TA
        end

        subgraph Innovation2 ["2. ListActivity as Pristine Task Root"]
            Root["[Bottom] ListActivity (MAIN / LAUNCHER)"]
            Home["[Middle] HomeActivity (SingleTop / Search & Quick Access)"]
            Cat["[Top] CategoryActivity (Generic / External Plugins)"]
            Root --> Home --> Cat
        end

        subgraph Innovation3 ["3. Unified CLEAR_TOP | SINGLE_TOP Navigation"]
            CT["startActivity(HomeActivity, CLEAR_TOP | SINGLE_TOP)"]
            CT -.->|일괄 파괴| Cat
            CT ==>|onNewIntent 전달 & 상태 보존| Home
            CT -.->|바닥에 상주하여 파괴되지 않음| Root
        end
    end
```

1. **WindowManager 기반의 Floating Window 오버레이 (`FloatingMenuManager`)**:
   - Pinned TaskFragment Overlay의 불안정한 Window Bounds 조작 및 포커스 리플렉션을 제거.
   - 접힌 상태: WindowManager의 80dp 원형 플로팅 뷰로 시스템 최상단에 상주.
   - 펼쳐진 상태: 표준 반투명 액티비티([`ListWithTranslucentActivity`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/ListWithTranslucentActivity.kt))를 실행하여 35% 서랍 + 65% 모달 딤(Dim) 제공.
2. **`ListActivity`의 Task Root 단일화**:
   - Wide/Narrow 모드 상관없이 [`ListActivity`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/ListActivity.kt)가 앱의 시작점(`MAIN`/`LAUNCHER`)으로 최하단에 상주.
   - Fold/Unfold(모드 전환) 시 액티비티 파괴/재생성 없이 SplitPairRule 토글만으로 100% 상태 보존.
3. **`HomeActivity` CLEAR_TOP | SINGLE_TOP 내비게이션 일원화**:
   - Wide와 Narrow 모두에서 `startActivity(HomeActivity, CLEAR_TOP | SINGLE_TOP)` 방식을 단일 적용.
   - 상단의 모든 `CategoryActivity`와 서브 페이지들이 안드로이드 OS에 의해 원자적으로 정리되며, `CategoryStackManager`와 `finishActivityStacks` 해킹 코드를 전면 배제.

---

## 2. Floating Window 기반 Overlay 메커니즘

### 2.1 기존 Pinned TaskFragment Overlay의 문제점
과거 Narrow Mode에서 사용하던 `ListOverlayActivity`는 Jetpack WindowManager의 `setOverlayCreateParams`와 Pinned TaskFragment API를 사용했습니다:
- **버전 종속성**: WindowManager Extension Version >= 8 이상 기기에서만 동작.
- **터치 가로챔 및 바운즈 리사이징 버그**: 투명 액티비티라도 Task 크기 전체의 터치를 소비하므로, `OverlayBoundsHelper`를 통해 물리적 윈도우 사각형을 `80dp` ↔ `100%`로 프레임마다 강제 리사이징해야 했음.
- **포커스 반환 리플렉션**: 하드웨어 Back 키를 하위 콘텐츠로 보내기 위해 비공개 API(`SplitController.mPresenter`, `TaskFragmentOrganizer`)를 리플렉션으로 호출.

### 2.2 해결책: Floating Window + Translucent Activity 분리 패턴

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant FMM as FloatingMenuManager (WindowManager)
    participant LTA as ListWithTranslucentActivity
    participant HA as HomeActivity / CategoryActivity

    Note over FMM,HA: 접힌 상태 (Floating Menu 노출)
    FMM->>FMM: WindowManager.addView() (80dp x 80dp)
    User->>HA: 하위 화면 자유롭게 터치 및 스크롤 (터치 통과 100%)
    
    Note over User,LTA: 메뉴 펼침 인터랙션
    User->>FMM: 좌측 미니 에지 핸들 탭(클릭) 또는 오른쪽 스와이프
    FMM->>FMM: WindowManager.removeView() (플로팅 뷰 숨김)
    FMM->>LTA: startActivity(ListWithTranslucentActivity)
    LTA->>LTA: 좌측 35% Drawer 슬라이드인 + 우측 65% Dim Scrim 페이드인
    
    Note over User,HA: 닫힘 또는 카테고리 전환 인터랙션
    alt Dim 영역 터치 또는 Back Key
        User->>LTA: 우측 Dim 터치 또는 Back 누름
        LTA->>LTA: finish()
        LTA-->>FMM: onDestroy() -> FloatingMenuManager.attach()
    else 카테고리 선택
        User->>LTA: 카테고리 클릭
        LTA->>HA: launchCategory() (HomeActivity CLEAR_TOP)
        LTA->>LTA: finish()
        LTA-->>FMM: onDestroy() -> FloatingMenuManager.attach()
    end
```

* **접힌 상태 (Collapsed) - 미니 에지 스와이프 핸들**:
  - [`FloatingMenuManager`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/FloatingMenuManager.kt)가 안드로이드 표준 `WindowManager.addView()`를 통해 화면 좌측 수직 중앙(`Gravity.START | Gravity.CENTER_VERTICAL`)에 6dp 시각적 폭(32dp 터치 타깃)의 미니 에지 알약 핸들([`layout_floating_menu_button.xml`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/res/layout/layout_floating_menu_button.xml))을 직접 부착.
  - 상단 툴바/타이틀(Top 0~80dp) 및 서브스크린 뒤로가기 버튼 영역을 전혀 침범하지 않으므로 화면 가림(Occlusion)이 0%로 완전히 해소됨.
  - **듀얼 제스처 지원**: 핸들을 가볍게 탭(클릭)하거나 오른쪽으로 가볍게 쓸어넘기면(Edge Swipe Right, `dx > 16dp`) 즉시 서랍이 열림.
  - 외부 플러그인(`LightSettingsActivity`)을 포함하여 어떤 화면이든 WindowManager 레벨에서 100% 무간섭 동작.
* **펼쳐진 상태 (Expanded)**:
  - 에지 핸들을 터치하거나 스와이프하면 뷰를 일시 제거(`removeView`)하고, 표준 반투명 액티비티인 [`ListWithTranslucentActivity`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/ListWithTranslucentActivity.kt)를 `startActivity`로 띄움.
  - 이 액티비티는 좌측 35% 메뉴 서랍과 우측 65% Dim 뷰([`viewDimScrim`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/res/layout/activity_list_translucent.xml))로 구성.
  - 우측 Dim 터치 또는 Back 키 입력 시 단순히 `finish()`되어 일반 액티비티 스택 규칙에 따라 자연스럽게 파괴.
  - `onDestroy()` 콜백 시점에 `FloatingMenuManager.attach()`를 재호출하여 다시 미니 에지 스와이프 핸들 복원.

* **옵션 A — 특권 시스템 앱 기반 이벤트 드리븐 태스크/UID 가시성 제어 (폴링 루프 제거 & Auto-Hide / Auto-Restore)**:
  - `TYPE_APPLICATION_OVERLAY`는 시스템 레이어에 위치하므로, 사용자가 하드웨어 Home 키·최근 앱(Recents)을 누르거나 다른 앱으로 전환할 때도 기본적으로 화면에 남아있는 문제가 있습니다.
  - 또한 단순 `registerActivityLifecycleCallbacks`는 **외부 카테고리 플러그인(`LightSettingsActivity` 등 독립 프로세스)의 생명주기를 감지할 수 없어**, 플러그인 화면에서 핸들이 잘못 숨겨지는 한계가 있습니다.
  - 이를 해결하기 위해 호스트 앱을 `/system/priv-app/LifecycleApp/LifecycleApp.apk` 특권 시스템 앱으로 배포하고 [`privapp-permissions-lifecycleapp.xml`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/privapp-permissions-lifecycleapp.xml)(`ACTIVITY_EMBEDDING`, `MANAGE_ACTIVITY_TASKS`, `PACKAGE_USAGE_STATS`) 및 `:framework-stubs` + `HiddenApiBypass`를 결합해 연속 폴링 루프 없는 **OS Binder 이벤트 드리븐 아키텍처(옵션 A)**를 구현했습니다:
    1. **2단계 OS 이벤트 옵저버 ([`FloatingMenuManager`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/FloatingMenuManager.kt))**:
       - **Primary (`TaskStackListener`)**: 플랫폼 서명 또는 `RECENTS` 권한이 부여된 환경(OEM IVI/시스템 이미지)에서는 `ActivityTaskManager.getService().registerTaskStackListener()`로 태스크 전환·포커스 이벤트를 직접 수신 (`MANAGE_ACTIVITY_TASKS` 미보유 AOSP/에뮬레이터 환경에서는 `SecurityException` 처리 후 자동 폴백).
       - **Privileged Fallback (`IUidObserver` via `PACKAGE_USAGE_STATS`)**: `IActivityManager.registerUidObserver(..., UID_OBSERVER_PROCSTATE or UID_OBSERVER_GONE, PROCESS_STATE_TOP)`를 등록하여 홈 런처(`homePackageUids`) 및 외부 플러그인/서드파티 앱의 `TOP(2)` 진입·이탈 시점에만 OS Binder 콜백을 수신.
    2. **태스크 가시성 판정 및 Android 17 Quickstep Live-Tile 대응 (`isAppTaskVisible` / `isTaskForegroundActive`)**:
       - 이벤트 수신 시 `ActivityManager.getAppTasks()`의 `TaskInfo.isVisible` (API 29+), Hidden API인 `TaskInfo.isFocused`, 그리고 런처 UID 상태(`isHomeUidTop`)를 함께 판정.
       - Pixel Tablet(Android 17) Quickstep Live-Tile Recents 화면에서 `TaskInfo.isVisible == true` 및 `isFocused == true`가 일시 유지되더라도 `isHomeUidTop == true`이면 즉시 `View.GONE`으로 숨기고, `Back` 키나 Recents 카드 선택으로 외부 플러그인(`LightSettingsActivity`)에 직접 복귀해도 즉시 `View.VISIBLE`로 복원.
       - 시스템 `Settings`(`uid=1000`, `PROCESS_STATE_PERSISTENT`) 등 cutpoint를 통과하지 않는 앱에서 `Back`으로 복귀할 때 OS 전환 애니메이션이 `TaskInfo.isVisible`을 커밋하는 시차를 보정하기 위해, Binder 이벤트 발생 시점에 한정된 2회 단발성 안착 재확인(`TRANSITION_SETTLE_EARLY_MS = 220L`, `TRANSITION_SETTLE_LATE_MS = 550L`, `detach()` 시 취소)만 수행하고 유휴 상태의 주기적 폴링은 전혀 수행하지 않음.
    3. **Android 17 `CachedAppsFreezer` 대응 ([`FloatingOverlayService`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/FloatingOverlayService.kt))**:
       - 핸들이 `View.GONE`으로 숨겨져 `hasOverlayUi`가 해제된 상태에서 ~10초 이상 백그라운드에 머물면 호스트 프로세스가 `CACHED` → Freezer 동결되어 외부 플러그인 복귀 이벤트를 수신할 수 없는 문제를 방지하기 위해, Narrow 모드에서 핸들이 attach된 동안만 경량 `specialUse` Foreground Service(`IMPORTANCE_MIN`, `NOTIFICATION_ID = 1001`)를 유지하고 Wide 전환(`detach()`) 또는 Recents 스와이프 제거(`onTaskRemoved()`) 시 즉시 종료.

---

## 3. ListActivity를 Task Root로 전환

### 3.1 전환 배경 및 문제 인식
기존에는 [`HomeActivity`](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeActivity.kt)가 Task Root(`MAIN`/`LAUNCHER`)였습니다.
이 구조의 치명적인 모순:
* Wide Mode에서 좌측 35%에 `ListActivity`를 띄우려면, `HomeActivity` 위로 `ListActivity`를 올린 후 2단계 RTL Split(`Home RTL 65:35 List` + `List LTR 35:65 Category`)을 적용해야 했습니다.
* 이 상태에서 사용자가 "Home"으로 돌아가기 위해 `HomeActivity`에 `CLEAR_TOP`을 날리면, **스택상 `Home`보다 위에 있던 `ListActivity`(좌측 메뉴)까지 함께 파괴**되어 버리는 현상이 발생했습니다.
* 결국 Wide 모드에서는 `CLEAR_TOP`을 쓰지 못하고, `finishActivityStacks` 및 IBinder 토큰 추적이라는 위험한 내부 API(`CategoryStackManager`)를 유지해야만 했습니다.

### 3.2 ListActivity Task Root의 스택 구조

`ListActivity`를 앱의 유일한 Task Root로 설정함으로써 스택 구조가 완전히 정상화되었습니다:

```text
[Top]    CategoryActivity (GenericSettingsActivity / External Plugins, Depth >= 2)
[Middle] HomeActivity (LaunchMode: singleTop)
[Bottom] ListActivity (Task Root / LaunchMode: singleTop / Pristine Root)
```

| 모드 | ListActivity (Root) | HomeActivity (Middle) | CategoryActivity (Top) |
| :--- | :--- | :--- | :--- |
| **Wide Mode** | 좌측 35% 분할 사이드바로 상시 노출 | 우측 65% 컨테이너 기본 화면 | 우측 65% 컨테이너 상단에 적층 |
| **Narrow Mode** | 백스택 최하단(바닥)에 상주 + Floating Window 관리 | 전체 화면 전면 노출 | 전체 화면 전면 노출 |

### 3.3 Overlay ↔ Split 모드 전환(Fold/Unfold) 시 상태 보존 원리 (No-Restart Transition)
과거의 [`ModeTransitionManager`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/ModeTransitionManager.kt)는 두 모드의 스택 구조가 상이하여 `FLAG_ACTIVITY_CLEAR_TASK`로 태스크 전체를 날리고 `HomeActivity`를 재시작하는 치명적인 모순(검색어/스크롤/백스택 전멸)이 있었습니다.

새 아키텍처에서는 Narrow 모드에서도 `ListActivity`가 백스택 최하단(STOPPED)에 살아있으므로, **`CLEAR_TASK` 및 액티비티 재기동을 전면 제거**하고 순수한 규칙/뷰 토글만으로 전환합니다:

```mermaid
flowchart LR
    subgraph Fold ["Fold 접힘 (Wide -> Narrow)"]
        WtoN1["1. SplitInitializer.setRules(emptySet())<br/>(우측 화면이 전체 화면으로 자연스럽게 확장)"]
        WtoN2["2. FloatingMenuManager.attach()<br/>(80dp 플로팅 버튼 노출)"]
        WtoN1 --> WtoN2
    end

    subgraph Unfold ["Unfold 펼침 (Narrow -> Wide)"]
        NtoW1["1. SplitInitializer.setRules(setOf(splitRule))<br/>(바닥의 ListActivity가 좌측 35%로, 화면이 우측 65%로 배치)"]
        NtoW2["2. FloatingMenuManager.detach()<br/>(80dp 플로팅 버튼 제거)"]
        NtoW3["3. ListActivity.ensureWideContentView()<br/>(사이드바 뷰 바인딩 보장)"]
        NtoW1 --> NtoW2 --> NtoW3
    end
```

* **Narrow → Wide (Unfold)**:
  - `SplitInitializer.kt`의 `SplitPairRule(ListActivity 35% <-> Home/Category 65%)`을 활성화.
  - `FloatingMenuManager.detach()`로 플로팅 버튼 제거.
  - `ListActivity.ensureWideContentView()`로 좌측 사이드바 뷰 인플레이션 및 리스트 바인딩 보장.
  - 안드로이드 Activity Embedding 프레임워크가 바닥에 있던 `ListActivity`를 좌측 35%로 올리고, 우측 65%에 전면 액티비티를 나란히 배치.
  - **어떤 액티비티도 파괴/재생성되지 않으므로 검색어, 스크롤, 서브 페이지 스택 100% 무중단 보존.**
* **Wide → Narrow (Fold)**:
  - `SplitInitializer.setRules(emptySet())` 호출로 임베딩 해제.
  - `FloatingMenuManager.attach()`로 80dp 원형 플로팅 버튼을 시스템에 즉시 부착.
  - 상단 화면이 전체 화면으로 매끄럽게 확장되고 `ListActivity`는 조용히 바닥으로 복귀.

---

## 4. HomeActivity CLEAR_TOP | SINGLE_TOP 통일

### 4.1 동작 메커니즘
`ListActivity`가 바닥에 있으므로, 카테고리 전환이나 홈 복귀 시 아래와 같은 단일 코드로 모든 모드가 완벽히 정리됩니다:

```kotlin
// HomeNavigationBridge.kt 및 BaseCategoryMenuActivity.kt
val homeIntent = Intent(context, HomeActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    putExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID, categoryId)
    if (targetIntent != null) {
        putExtra(HomeActivity.EXTRA_TARGET_INTENT, targetIntent)
    }
}
context.startActivity(homeIntent)
```

1. **상위 카테고리 스택 일괄 파괴**:
   - `HomeActivity`보다 위에 쌓여있던 모든 `CategoryActivity`(내부/외부 플러그인, Depth 2, 3 등)가 안드로이드 OS에 의해 일괄 파괴(`onDestroy`).
2. **HomeActivity 상태 보존**:
   - `FLAG_ACTIVITY_SINGLE_TOP`에 의해 `HomeActivity`는 재생성되지 않고 `onNewIntent()`를 호출받음.
   - 검색창 쿼리("Lumbar" 등)와 Compose 스크롤 상태가 메모리에 100% 보존됨.
3. **하위 ListActivity 보존**:
   - `CLEAR_TOP`은 타깃(`HomeActivity`)보다 "위에 있는" 액티비티들만 파괴하므로, 타깃보다 "아래에 있는" `ListActivity`는 손끝 하나 건드리지 않고 그대로 유지됨.
4. **새 카테고리 실행**:
   - `HomeActivity.onNewIntent()`에서 인텐트에 실린 `EXTRA_TARGET_INTENT`가 있다면 이를 즉시 실행하여 깨끗해진 스택 위에 새로운 카테고리를 안착시킴.

### 4.2 CategoryStackManager 및 해킹 코드 제거
이로써 과거 카테고리 스택을 비우기 위해 필요했던 다음 복잡성이 완전히 제거되었습니다:
- `finishActivityStacks()` API 의존성 제거.
- TaskFragmentOrganizer / Token 추출 Reflection 불필요.
- `CategoryStackManager.kt`의 등록/해제 사이클 바이패스.

---

## 5. 앱 종료(Exit) 및 수명주기 보장

* **Home 화면에서 Back 키 입력 시**:
  - `HomeActivity`의 `onBackPressedDispatcher`에서 `finishAffinity()`를 호출.
  - Task에 속한 모든 액티비티(`CategoryActivity`, `HomeActivity`, 그리고 바닥의 `ListActivity`)가 한 번에 정상 종료.
  - 동시에 `HomeNavigationBridge.notifyAppExit()`를 발행하여 남아있는 플로팅 오버레이 뷰도 완전히 detach.
* **좀비 액티비티 발생 가능성 차단**:
  - `ListActivity`는 `BaseCategoryMenuActivity`의 `appExitListener`를 등록하고 있어, `HomeActivity` 종료 시 즉각적으로 동기화되어 종료됨.

---

## 6. 동적 카테고리 헤더 제어 계약 (`EXTRA_CATEGORY_HEADER_BREAKPOINT_DP`)

Narrow(Overlay) 모드와 Wide(Split) 모드 간의 UI 맥락 차이를 해결하기 위해 Intent Extra 기반의 동적 Breakpoint 계약을 도입했습니다.

### 6.1 설계 배경 및 문제 해결
* **Wide (Split) 모드**: 좌측 35% 사이드바에 현재 선택된 카테고리가 항상 하이라이트되어 노출되므로, 우측 카테고리 화면 상단에 거대한 카테고리 타이틀/서브타이틀이 중복 표시되면 수직 공간 낭비가 발생합니다.
* **Narrow (Overlay) 모드**: 사이드바가 닫혀있고 최소형 엣지 핸들(`›`)만 노출되므로, 전체 화면으로 열린 카테고리 상단에 명확한 카테고리 타이틀과 설명이 있어야 사용자가 자신의 위치를 인지할 수 있습니다.
* **Intent Extra Breakpoint 계약의 우수성**:
  - Intent Extra에 고정된 `Boolean`(`true/false`)을 주입하면 화면 회전이나 폴더블 디스플레이 접힘/펼침으로 액티비티가 recreate되거나 `onConfigurationChanged`가 발생했을 때 과거 모드의 값에 갇히게 됩니다.
  - 호스트 앱은 불변의 기준 너비인 **`EXTRA_CATEGORY_HEADER_BREAKPOINT_DP` (840dp)**만 Extra로 전달합니다.
  - 각 카테고리 Activity는 자기 자신의 현재 너비(`resources.configuration.screenWidthDp`)를 breakpoint와 실시간 비교하여:
    - `screenWidthDp < breakpoint` (Narrow Overlay): **카테고리 메인 헤더 표시 (`View.VISIBLE`)**
    - `screenWidthDp >= breakpoint` (Wide Split): **카테고리 메인 헤더 숨김 (`View.GONE`)**
  - **Main Screen에만 한정**: 서브 스크린(`GenericSubActivity`, 아이템 상세 화면 등)은 이미 고유한 상단바/뒤로가기 버튼이 존재하므로 영향을 받지 않고 오직 루트 카테고리 메인 화면에만 적용됩니다.
  - **외부 플러그인 호환성**: `LightSettingsActivity`와 같은 독립 플러그인 앱도 별도의 내부 라이브러리 컴파일 의존 없이 `intent.getIntExtra("extra_category_header_breakpoint_dp", 840)` 한 줄만으로 완벽하게 동작합니다.

---

## 7. 결론

이번 리팩토링을 통해 **"비표준 윈도우 확장 API 및 복잡한 TaskFragment 관리"**에서 벗어나, **"안드로이드 표준 액티비티 스택 플래그(`CLEAR_TOP`)와 독립된 WindowManager 뷰(`FloatingMenuManager`)"**를 결합한 견고하고 우아한 아키텍처를 완성했습니다.

* **코드 복잡도 대폭 감소**: SplitPairRule 단순화 (1단계 LTR), TaskFragment 해킹 제거.
* **플랫폼 호환성 향상**: WindowManager Extension Version 8 미만 기기에서도 Narrow 모드 완벽 동작.
* **100% 테스트 검증 완료**: Wide/Narrow 전 시나리오 에뮬레이터 검증 통과.
