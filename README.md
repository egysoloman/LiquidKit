# LiquidKit

**LiquidKit** 是一套受 iOS 26 / 27 **Liquid Glass** 设计语言启发的跨平台组件库，同时提供：

1. **普通 Kotlin / Jetpack Compose / KMP 写法**（`io.liquidkit` 与底层光学引擎 `:backdrop`）—— 基于 Android 实时 AGSL / RenderEffect 背景采样折射、弹簧形变物理与边缘高光渲染。
2. **Swift / SwiftUI 双端写法**（`Sources/LiquidKit`，支持 **原生 iOS 26+ `.glassEffect`** 与 **Skip / SkipUI 自动转译至 Android**）—— 一份 SwiftUI 代码同时运行在 iOS/macOS 与 Android。

> **Pre-release (`v1.0.0-alpha.1`)**：当前处于首个公开预发布版本，核心交互、视觉光学材质与测试已就绪，公开 API 仍在持续完善中。

---

## 仓库结构（纯组件库源码）

```text
LiquidKit/
├── backdrop/                 # 核心 Compose Multiplatform 背景采样与光学渲染引擎 (com.kyant.backdrop)
├── liquidkit/                # 高层组件库门面与 Skip 无感 Backdrop 桥接层 (io.liquidkit)
├── Sources/LiquidKit/        # Swift / SwiftUI 组件封装及 Skip 转译适配 (Skip/skip.yml)
├── Tests/LiquidKitTests/     # Swift Package 单元测试
├── docs/
│   ├── kotlin-compose.md     # 普通 Kotlin / Compose 完整接入与组件指南
│   └── swift-skip.md         # Swift / SwiftUI & Skip 跨平台指南
├── Package.swift             # SwiftPM / Skip 清单
├── settings.gradle.kts       # Gradle 多平台工程（仅含 :backdrop 与 :liquidkit）
└── THIRD_PARTY_NOTICES.md    # 开源许可与致谢说明
```

## 平台支持与依赖版本

| 平台 / 工具链 | 支持情况 | 说明 |
| --- | --- | --- |
| Android | minSdk **21**（实时 AGSL 折射需 API 33+） | 低版本自动降级为柔和半透明/模糊材质 |
| Compose Multiplatform | `1.12.0` / Kotlin `2.4.10` / AGP `9.3.2` | 支持 Android、Desktop (JVM)、iOS、macOS、JS、WasmJs |
| Swift / Apple | Swift **6.0+**，iOS **18+** / macOS **15+** | iOS 26+ 自动启用原生 `.glassEffect` 与 `GlassEffectContainer`，iOS 18 回退 `.regularMaterial` |
| Skip / SkipUI | `skip >= 1.6.0`，`skip-ui >= 1.30.0` | 设置 `SKIP_BRIDGE=1` 时自动桥接 `io.liquidkit:liquidkit-android:1.0.0-alpha.1` |

---

## 快速开始

### 方式一：普通 Kotlin / Compose 写法（`io.liquidkit`）

#### 1. 引入依赖

- **方式 A：直接下载 GitHub Pre-release 中的 AAR 文件**

  从 [Releases](https://github.com/egysoloman/LiquidKit/releases) 下载 `liquidkit-1.0.0-alpha.1.aar` 和 `backdrop-2.0.1.aar` 放入 `app/libs/`：

  ```kotlin
  dependencies {
      implementation(files("libs/liquidkit-1.0.0-alpha.1.aar"))
      implementation(files("libs/backdrop-2.0.1.aar"))
      implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
      implementation("org.jetbrains.compose.animation:animation:1.12.0")
      implementation("io.github.kyant0:shapes:1.2.1")
  }
  ```

- **方式 B：发布到本地 Maven（推荐联调 / KMP 工程）**

  ```bash
  ./gradlew :backdrop:publishToMavenLocal :liquidkit:publishToMavenLocal
  ```

  在 `settings.gradle.kts` 中开启 `mavenLocal()` 后添加依赖：

  ```kotlin
  dependencies {
      implementation("io.liquidkit:liquidkit-android:1.0.0-alpha.1")
      // 底层光学引擎 :backdrop 会作为 api 依赖自动传递，或显式引入：
      implementation("io.github.kyant0:backdrop-android:2.0.1")
  }
  ```

#### 2. 编写 Compose 页面

用 `LiquidKitRoot` 包裹根视图，它会自动注入 `LocalLiquidKitBackdrop`、`LocalLiquidKitModalBackdrop`、主题 Tokens 与弹窗宿主：

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.liquidkit.*

@Composable
fun SampleLiquidScreen() {
    var dark by remember { mutableStateOf(false) }
    var glassOpacity by remember { mutableStateOf(0.5f) }
    var notifications by remember { mutableStateOf(true) }
    var volume by remember { mutableStateOf(0.6f) }
    var mode by remember { mutableStateOf("列表") }
    var selectedTab by remember { mutableStateOf("library") }
    var showSheet by remember { mutableStateOf(false) }

    val menuState = rememberLiquidKitMenuState { item -> /* 处理选中 item.key */ }

    LiquidKitRoot(opacity = glassOpacity, dark = dark) {
        LiquidKitNavigationScaffold(
            title = "LiquidKit",
            trailing = {
                LiquidKitAnchoredMenuHost(
                    state = menuState,
                    items = listOf(
                        LiquidKitMenuItem("light", "浅色", LiquidKitSymbol.Sun, checked = !dark),
                        LiquidKitMenuItem("dark", "深色", LiquidKitSymbol.Moon, checked = dark),
                    ),
                ) {
                    LiquidKitToolbarGroup {
                        LiquidKitToolbarButton("外观", menuState::show, Modifier.liquidKitMenuTrigger(menuState)) {
                            LiquidKitIcon(LiquidKitSymbol.Sun)
                        }
                    }
                }
            },
            bottomBar = {
                LiquidKitTabBar(
                    tabs = listOf(
                        LiquidKitTab("library", "组件", LiquidKitSymbol.Grid),
                        LiquidKitTab("favorites", "收藏", LiquidKitSymbol.Star),
                    ),
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    searchAction = { showSheet = true },
                )
            },
        ) { safePadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(safePadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LiquidKitGlassEffectContainer(spacing = 10.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidKitActionButton(
                            label = "打开面板",
                            leadingSymbol = LiquidKitSymbol.Sparkles,
                            style = LiquidKitActionStyle.Glass,
                            onClick = { showSheet = true },
                        )
                        LiquidKitActionButton(
                            label = "主要操作",
                            style = LiquidKitActionStyle.GlassProminent,
                            tint = Color(0xff007aff),
                            onClick = {},
                        )
                    }
                }

                LiquidKitLabeledContent("通知开关") {
                    LiquidKitToggle(selected = { notifications }, onSelect = { notifications = it })
                }

                LiquidKitSlider(
                    value = { volume },
                    onValueChange = { volume = it },
                    ticks = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                    neutralValue = 0.5f,
                )

                LiquidKitSegmentedPicker(
                    options = listOf("列表", "网格", "分栏"),
                    selected = mode,
                    select = { mode = it },
                )
            }
        }

        if (showSheet) {
            LiquidKitSheet(
                title = "外观设置",
                onDismissRequest = { showSheet = false },
            ) {
                LiquidKitOpacitySlider(
                    value = glassOpacity,
                    onValueChange = { glassOpacity = it },
                )
            }
        }
    }
}
```

> 若需要自定义底层实时采样图层（如为滚动背景卡片做直接折射），可直接使用 `:backdrop` 提供的 `rememberLiquidKitBackdrop()` + `Modifier.liquidKitBackdrop(backdrop)`，详见 [docs/kotlin-compose.md](docs/kotlin-compose.md)。

---

### 方式二：Swift / SwiftUI 写法（原生 iOS 26/27 & Skip 跨平台）

#### 1. 引入 Swift Package

在 Xcode 或 `Package.swift` 中添加依赖：

```swift
dependencies: [
    .package(url: "https://github.com/egysoloman/LiquidKit.git", from: "1.0.0-alpha.1")
]
```

如需通过 **Skip** 转译到 Android：

1. 本地先发布 Android 构件：`./gradlew :backdrop:publishToMavenLocal :liquidkit:publishToMavenLocal`
2. 以 `SKIP_BRIDGE=1` 构建 Skip 工程（`Sources/LiquidKit/Skip/skip.yml` 会自动向生成的 Android Gradle 注入 `mavenLocal()` 与 `io.liquidkit:liquidkit-android:1.0.0-alpha.1`）。

#### 2. 编写 SwiftUI 页面（零 `#if SKIP` 样板代码）

```swift
import SwiftUI
import LiquidKit

struct SampleSwiftScreen: View {
    @State private var glassOpacity = 0.5
    @State private var notifications = true
    @State private var volume = 0.6
    @State private var mode = "列表"
    @State private var selectedTab = "library"
    @State private var showSheet = false

    var body: some View {
        LiquidKitRoot(opacity: glassOpacity) {
            VStack(spacing: 16) {
                LiquidGlassContainer(spacing: 10) {
                    HStack(spacing: 10) {
                        LiquidButton("打开面板", systemImage: "sparkles", style: .glass) {
                            showSheet = true
                        }
                        LiquidButton("主要操作", style: .glassProminent, tint: .accentBlue) {}
                    }
                }

                LiquidSwitch("通知开关", isOn: $notifications)

                LiquidSliderControl(
                    value: $volume,
                    in: 0...1,
                    ticks: [0, 0.25, 0.5, 0.75, 1.0],
                    neutralValue: 0.5
                )

                LiquidSegmentedControl(options: ["列表", "网格", "分栏"], selection: $mode)

                LiquidMenuControl(
                    "排序方式",
                    items: [
                        LiquidMenuEntry(id: "name", title: "名称", systemImage: "textformat", checked: true),
                        LiquidMenuEntry(id: "date", title: "日期", systemImage: "calendar"),
                        LiquidMenuEntry(id: "delete", title: "删除", systemImage: "trash", destructive: true),
                    ]
                ) { selected in
                    print("Selected menu item:", selected)
                }

                Spacer()

                LiquidFloatingTabBar(
                    tabs: [
                        LiquidTabItem(id: "library", title: "组件", systemImage: "square.grid.2x2"),
                        LiquidTabItem(id: "favorites", title: "收藏", systemImage: "star.fill"),
                    ],
                    selection: $selectedTab,
                    searchAction: { showSheet = true }
                )
            }
            .padding()
            .overlay {
                LiquidModalSheet(isPresented: $showSheet, title: "外观设置") {
                    LiquidOpacityControl(opacity: $glassOpacity)
                }
            }
        }
    }
}
```

更多 Swift / Skip 组件示例与类型对照见 [docs/swift-skip.md](docs/swift-skip.md)。

---

## 组件总览与双端 API 对照

| 分类 | Kotlin / Compose (`io.liquidkit`) | Swift / Skip (`LiquidKit`) |
| --- | --- | --- |
| 根宿主与材质 | `LiquidKitRoot`、`LiquidKitTheme`、`LiquidKitGlassSurface`、`LiquidKitGlassEffectContainer` | `LiquidKitRoot`、`LiquidGlassSurfaceView` / `.liquidGlass(...)`、`LiquidGlassContainer` |
| 按钮与工具栏 | `LiquidKitActionButton`、`LiquidKitButton`、`LiquidKitToggleButton`、`LiquidKitBorderlessButton`、`LiquidKitToolbarGroup`、`LiquidKitToolbarPair` | `LiquidButton`（6 种 `LiquidButtonStyleKind`）、`LiquidTogglePill` |
| 菜单与上下文 | `LiquidKitAnchoredMenuHost`、`LiquidKitContextMenuHost`、`rememberLiquidKitMenuState` | `LiquidMenuControl`、`LiquidMenuEntry` |
| 基础交互控件 | `LiquidKitToggle`、`LiquidKitSlider`、`LiquidKitOpacitySlider`、`LiquidKitStepper`、`LiquidKitTextField`、`LiquidKitSearchField` | `LiquidSwitch`、`LiquidSliderControl`、`LiquidOpacityControl`、`LiquidStepperControl`、`LiquidTextInput`、`LiquidSearchBar` |
| 选择器系列 | `LiquidKitSegmentedPicker`、`LiquidKitTabsPicker`、`LiquidKitWheelPicker`、`LiquidKitMenuPicker`、`LiquidKitInlinePicker`、`LiquidKitPalettePicker`、`LiquidKitDatePicker`、`LiquidKitMultiDatePicker`、`LiquidKitColorPicker` | `LiquidSegmentedControl`、`LiquidTabsPickerControl`、`LiquidMenuPickerControl`、`LiquidInlinePickerControl`、`LiquidPalettePickerControl`、`LiquidDatePickerControl`、`LiquidMultiDatePickerControl`、`LiquidColorPickerControl` |
| 导航与底栏 | `LiquidKitNavigationScaffold`、`LiquidKitSwipeBackNavigation`、`LiquidKitNavigationSplitView`、`LiquidKitTabBar`、`LiquidKitSafeAreaBar`、`LiquidKitViewTransition` | `LiquidFloatingTabBar`、`LiquidSafeAreaBarView`、`LiquidViewSwitcher`、`LiquidTabViewBottomAccessoryView` |
| 弹窗与侧边栏 | `LiquidKitAlert`、`LiquidKitActionSheet`、`LiquidKitSheet`、`LiquidKitPopover`、`LiquidKitInspector` | `LiquidAlertModal`、`LiquidActionSheetModal`、`LiquidModalSheet`、`LiquidPopoverView`、`LiquidInspectorContainer` |
| 列表、状态与图表 | `LiquidKitList`、`LiquidKitListCell`、`LiquidKitReorderableGrid`、`LiquidKitOutlineGroup`、`LiquidKitTable`、`LiquidKitGauge`、`LiquidKitBarChart`、`LiquidKitLineAreaChart`、`LiquidKitPieDonutChart`、`LiquidKitMeshGradient`、`LiquidKitTipCard` | `LiquidKeyValueRow`、`LiquidDisclosure`、`LiquidGroupCard`、`LiquidEmptyView`、`LiquidReorderableGridView`、`LiquidOutlineGroupView`、`LiquidTableView`、`LiquidProgress`、`LiquidGaugeControl`、`LiquidBarChartView`、`LiquidLineAreaChartView`、`LiquidPieDonutChartView`、`LiquidMathPlotChartView`、`LiquidMeshGradientView`、`LiquidBadgeView`、`LiquidTipBannerView`、`liquidRedacted(...)` |

---

## 构建与测试验证

```bash
# 运行 Kotlin Multiplatform / Desktop 单元测试并构建 Android AAR
./gradlew :backdrop:desktopTest :liquidkit:desktopTest :backdrop:bundleAndroidMainAar :liquidkit:bundleAndroidMainAar

# 运行 Swift Package 单元测试
swift test
```

- Android AAR 输出目录：
  - `backdrop/build/outputs/aar/`（`backdrop-release.aar` / `backdrop-debug.aar`）
  - `liquidkit/build/outputs/aar/`（`liquidkit-release.aar` / `liquidkit-debug.aar`）

## 开源许可与致谢

- 本仓库遵循 **Apache License, Version 2.0**（见 [LICENSE](LICENSE)）。
- 底层 `:backdrop` 渲染引擎衍生并基于 Kyant 的 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（Apache-2.0），详细声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
