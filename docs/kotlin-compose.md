# Kotlin / Compose 使用指南（`io.liquidkit` & `:backdrop`）

本文介绍如何在普通 **Android Jetpack Compose** 或 **Compose Multiplatform (KMP)** 工程中直接以 Kotlin 方式接入 `LiquidKit` 与底层 `:backdrop` 渲染引擎。

---

## 1. 接入方式

### 方式 A：使用预编译 AAR（适合标准 Android 应用）

从 [GitHub Releases](https://github.com/egysoloman/LiquidKit/releases) 下载 `liquidkit-1.0.0-alpha.2.aar` 和 `backdrop-2.0.1.aar` 放入模块 `libs/` 目录：

```kotlin
dependencies {
    implementation(files("libs/liquidkit-1.0.0-alpha.2.aar"))
    implementation(files("libs/backdrop-2.0.1.aar"))
    implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
    implementation("org.jetbrains.compose.animation:animation:1.12.0")
    implementation("io.github.kyant0:shapes:1.2.1")
}
```

### 方式 B：本地 Maven 发布（推荐 Kotlin Multiplatform / 多模块工程）

在本仓库根目录执行：

```bash
./gradlew :backdrop:publishToMavenLocal :liquidkit:publishToMavenLocal
```

在消费方 `settings.gradle.kts` 添加 `mavenLocal()`，并在对应 `build.gradle.kts` 中引入：

```kotlin
// Android-only 模块
implementation("io.liquidkit:liquidkit-android:1.0.0-alpha.2")

// 或 KMP commonMain（若发布完整 KMP 目标）
implementation("io.liquidkit:liquidkit:1.0.0-alpha.2")
```

### 方式 C：Gradle Composite Build 源码依赖

在应用工程的 `settings.gradle.kts` 中引入：

```kotlin
includeBuild("../LiquidKit")
```

---

## 2. 根宿主与 Backdrop 机制

`LiquidKit` 的实时折射依赖背景采集对象（`Backdrop`）。你有两种组织方式：

### 2.1 `LiquidKitRoot` + 隐式 Ambient Backdrop（推荐）

`LiquidKitRoot` 会自动提供：
- `LiquidKitTheme(opacity, dark)` 材质浓度与深浅色主题
- `LiquidKitSystemBarAppearance` 状态栏/导航栏边到边外观控制
- `LocalLiquidKitBackdrop`（场景默认背景）与 `LocalLiquidKitModalBackdrop`（仅在模态弹出激活时录制）
- `LiquidModalHost`（用于 `LiquidKitAlert`、`LiquidKitActionSheet`、`LiquidKitSheet`、`LiquidKitPopover`、`LiquidKitAnchoredMenuHost`、`LiquidKitContextMenuHost`）

```kotlin
LiquidKitRoot(
    opacity = 0.5f,
    dark = isSystemInDarkTheme(),
    edgeToEdgeSystemBars = true,
) {
    // 内部所有 LiquidKit* 组件默认自动读取 LocalLiquidKitBackdrop / LocalLiquidKitModalBackdrop
}
```

### 2.2 `LiquidKitAppScaffold` / 自定义 `rememberLiquidKitBackdrop()`

当需要悬浮底栏或顶部导航栏对**可滚动页面内容本身**进行实时背景折射（而非纯色画布）时，显式将 `pageBackdrop` 传给内容层：

```kotlin
val customBackdrop = rememberLiquidKitBackdrop()

Box(Modifier.fillMaxSize()) {
    // 1. 被采样背景内容
    Box(
        Modifier
            .fillMaxSize()
            .liquidKitBackdrop(customBackdrop)
    ) {
        // 列表 / 图片 / 渐变背景
    }

    // 2. 前景液态玻璃控件采样 customBackdrop
    LiquidKitGlassSurface(
        backdrop = customBackdrop,
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        variant = LiquidKitVariant.Regular,
        interactive = true,
        onClick = {},
    ) {
        BasicText("实时折射玻璃卡片", Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    }
}
```

若使用 `LiquidKitAppScaffold`：

```kotlin
LiquidKitAppScaffold(
    bottomBar = { sceneBackdrop ->
        LiquidKitTabBar(
            tabs = tabs,
            selected = selectedTab,
            onSelect = { selectedTab = it },
            backdrop = sceneBackdrop,
        )
    },
) { pageBackdrop, safePadding ->
    Box(Modifier.fillMaxSize().liquidKitBackdrop(pageBackdrop)) {
        // 页面内容
    }
}
```

可通过 `LocalLiquidKitTokens`（类型别名 `LiquidKitTokens`）覆写全局半径、阻尼、高光与 Tab 透镜参数。

---

## 3. 分类组件写法示例

### 3.1 按钮、融合容器与工具栏

```kotlin
LiquidKitGlassEffectContainer(spacing = 10.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LiquidKitActionButton(
            label = "常规",
            style = LiquidKitActionStyle.Glass,
            leadingSymbol = LiquidKitSymbol.Sparkles,
            controlSize = LiquidKitControlSize.Regular,
            onClick = {},
        )
        LiquidKitActionButton(
            label = "确认",
            style = LiquidKitActionStyle.GlassProminent,
            tint = Color(0xff007aff),
            shape = LiquidKitButtonShape.Capsule,
            onClick = {},
        )
        LiquidKitToggleButton(
            label = "收藏",
            checked = pinned,
            onCheckedChange = { pinned = it },
            leadingSymbol = LiquidKitSymbol.Star,
        )
    }
}

LiquidKitToolbarPair(
    spacing = 8.dp,
    firstVisible = canGoBack,
    first = {
        LiquidKitToolbarGroup {
            LiquidKitToolbarButton("返回", onClick = onBack) {
                LiquidKitIcon(LiquidKitSymbol.ChevronLeft)
            }
        }
    },
    second = {
        LiquidKitToolbarGroup {
            LiquidKitToolbarButton("分享", onClick = onShare) {
                LiquidKitIcon(LiquidKitSymbol.Share)
            }
            LiquidKitToolbarButton("更多", onClick = onMore) {
                LiquidKitIcon(LiquidKitSymbol.Ellipsis)
            }
        }
    },
)
```

### 3.2 开关、滑块、不透明度滑块与步进器

```kotlin
// 液态玻璃拨片开关
LiquidKitToggle(
    selected = { isOn },
    onSelect = { isOn = it },
    tint = Color(0xff34c759),
)

// 连续 / 刻度磁吸 / 中性点滑块
LiquidKitSlider(
    value = { balance },
    onValueChange = { balance = it },
    valueRange = -50f..50f,
    ticks = listOf(-50f, -25f, 0f, 25f, 50f),
    snapToTicks = true,
    neutralValue = 0f,
)

// 材质浓度滑块（配合 LiquidKitRoot(opacity = density)）
LiquidKitOpacitySlider(
    value = density,
    onValueChange = { density = it },
    label = "玻璃不透明度",
)

// 支持点击和长按连续增减的步进器
LiquidKitStepper(
    label = "数量 $count",
    value = count,
    onChange = { count = it },
    range = 1..20,
)
```

### 3.3 选择器（分段、Tabs、滚轮、菜单、日期、颜色）

```kotlin
// Segmented 分段选择器（拖动手势不会误触 SwipeBack）
LiquidKitSegmentedPicker(
    options = listOf("概览", "细节", "源码"),
    selected = selectedSegment,
    select = { selectedSegment = it },
)

// iOS 27 Tabs 选择器
LiquidKitTabsPicker(
    options = listOf(
        LiquidKitTabOption("overview", "概览"),
        LiquidKitTabOption("design", "设计"),
        LiquidKitTabOption("test", "测试"),
    ),
    selectedId = tabId,
    onSelect = { tabId = it },
)

// 3D 弹性滚轮选择器
LiquidKitWheelPicker(
    options = listOf("10 分钟", "30 分钟", "1 小时", "2 小时"),
    selected = duration,
    onSelect = { duration = it },
)

// 菜单式选择器 / 日期选择器 / 颜色选择器
LiquidKitMenuPicker("分辨率", listOf("720p", "1080p", "4K"), resolution, { resolution = it })
LiquidKitDatePicker("截止日期", date, { date = it }, style = LiquidKitDatePickerStyle.Compact)
LiquidKitColorPicker("主题色", accentColor, { accentColor = it }, supportsOpacity = true)
```

### 3.4 锚定菜单、上下文菜单与多级子菜单

```kotlin
val menuState = rememberLiquidKitMenuState { item ->
    when (item.key) {
        "copy" -> copyLink()
        "delete" -> deleteItem()
    }
}

LiquidKitAnchoredMenuHost(
    state = menuState,
    presentation = LiquidKitMenuPresentation.ButtonTransform, // 无边框触发器可用 Popup
    items = listOf(
        LiquidKitMenuItem("copy", "复制链接", LiquidKitSymbol.DocOnDoc),
        LiquidKitMenuItem(
            key = "more",
            title = "更多操作",
            symbol = LiquidKitSymbol.EllipsisCircle,
            children = listOf(
                LiquidKitMenuItem("pin", "置顶", LiquidKitSymbol.Pin),
                LiquidKitMenuItem("delete", "删除", LiquidKitSymbol.Trash, destructive = true),
            ),
        ),
    ),
) {
    LiquidKitToolbarGroup {
        LiquidKitToolbarButton("操作", menuState::show, Modifier.liquidKitMenuTrigger(menuState)) {
            LiquidKitIcon(LiquidKitSymbol.Ellipsis)
        }
    }
}
```

对于长按行弹出的上下文预览菜单，使用 `LiquidKitContextMenuHost(state, items, preview = { ... }) { Box(Modifier.liquidKitContextMenu(state)) { ... } }`。

### 3.5 导航栈、侧滑返回与 SplitView

```kotlin
LiquidKitSwipeBackNavigation(
    page = currentPage,
    previousPage = stack.dropLast(1).lastOrNull(),
    depth = { it.depth },
    contentKey = { it.id },
    onNavigateBack = { popPage() },
) { page ->
    LiquidKitNavigationScaffold(
        title = page.title,
        titleMode = LiquidKitNavigationTitleMode.Automatic,
        leading = {
            if (page.depth > 0) {
                LiquidKitActionButton("返回", style = LiquidKitActionStyle.Glass, onClick = ::popPage)
            }
        },
    ) { padding ->
        // 页面内容
    }
}
```

### 3.6 Alert、ActionSheet、Sheet 与锚定 Popover

```kotlin
// 按钮间支持滑动切换高亮的 LiquidKitAlert
if (showAlert) {
    LiquidKitAlert(
        title = "移除项目？",
        message = "此操作不可撤销。",
        actions = listOf(
            LiquidKitAlertAction("取消", LiquidKitAlertRole.Cancel) { showAlert = false },
            LiquidKitAlertAction("移除", LiquidKitAlertRole.Destructive) {
                removeItem()
                showAlert = false
            },
        ),
        onDismissRequest = { showAlert = false },
    )
}

// 多档位半屏抽屉（支持 Medium / Large 拖拽吸附与背景卡片缩放景深）
if (showSheet) {
    LiquidKitSheet(
        title = "详情设置",
        detents = listOf(LiquidKitSheetDetent.Medium, LiquidKitSheetDetent.Large),
        onDismissRequest = { showSheet = false },
    ) {
        BasicText("Sheet 内容")
    }
}

// 锚定浮层 Popover
val anchor = remember { mutableStateOf<Rect?>(null) }
Box(Modifier.liquidPopoverAnchor(anchor)) { /* 触发按钮 */ }
if (showPopover) {
    LiquidKitPopover(
        anchorBoundsInRoot = anchor.value,
        onDismissRequest = { showPopover = false },
    ) {
        BasicText("Popover 内容")
    }
}
```

### 3.7 列表、拖拽重排、图表与 MeshGradient

- `LiquidKitList` / `LiquidKitListCell`（搭配 `:backdrop` 中的 `liquidListSection` 与 `LiquidSwipeActions`）
- `LiquidKitReorderableGrid(items, key, onReorder)`：长按跨格拖拽重排（对标 iOS 27 `.reorderContainer`）
- `LiquidKitSafeAreaBar(edge = LiquidKitSafeAreaEdge.Bottom)`：边缘渐进模糊护栏
- `LiquidKitGauge`、`LiquidKitLinearGauge`、`LiquidKitBarChart`、`LiquidKitLineAreaChart`、`LiquidKitPieDonutChart`、`LiquidKitMathPlotChart`
- `LiquidKitMeshGradient(width, height, colors)`：GPU 网格渐变背景

---

## 4. 直接使用底层 `:backdrop`

如果需要直接操控物理弹簧、手势阈值或自定义绘制修饰符（如 `Modifier.drawBackdrop`、`LiquidMorphContainer`、`LiquidListSection`、`LiquidSwipeActions`、`GlassTabTokens`），可直接导入 `com.kyant.backdrop.*`（`LiquidKit` 会通过 Gradle `api(project(":backdrop"))` 自动导出完整 `:backdrop` 符号）。
