# Swift / SwiftUI & Skip 跨平台使用指南

`Sources/LiquidKit` 为 Swift 开发者提供一套免 `#if SKIP` 的 SwiftUI API：

- 在 **iOS 26+ / macOS 26+** 上直接调用系统原生 `.glassEffect`、`GlassEffectContainer`、`.buttonStyle(.glass / .glassProminent)` 与原生控件。
- 在 **iOS 18–25 / macOS 15** 上自动平滑回退到 `.regularMaterial` / `.ultraThinMaterial`。
- 在 **Android (Skip / SkipUI)** 上自动桥接 `io.liquidkit.skip.SkipLiquidKit*`，渲染 `:liquidkit` + `:backdrop` 的实时 AGSL 液态玻璃组件，并自动消费 `LocalLiquidKitBackdrop` 与 `LocalLiquidKitModalBackdrop`。

---

## 1. 接入方式

### 1.1 Swift Package Manager (SwiftPM)

在 Xcode **Package Dependencies** 或 `Package.swift` 中加入：

```swift
dependencies: [
    .package(url: "https://github.com/egysoloman/LiquidKit.git", from: "1.0.0-alpha.3")
]
```

然后在目标 target 的 `dependencies` 中引入 `"LiquidKit"`。

### 1.2 Skip 双端转译到 Android

1. 先在本仓库根目录发布 Android 组件到本地 Maven（只需执行一次或更新版本时重跑）：

   ```bash
   ./gradlew :backdrop:publishToMavenLocal :liquidkit:publishToMavenLocal
   ```

2. 通过 Skip Xcode 插件或 SwiftPM 构建（默认启用 `skipstone` 插件；若需纯 Apple 环境构建可设 `LIQUIDKIT_NO_SKIP=1`）。`Sources/LiquidKit/Skip/skip.yml` 已配置：
   - `mode: 'transpiled'`
   - `mavenLocal()` 仓库
   - `implementation("io.liquidkit:liquidkit-android:1.0.0-alpha.3")` 与 `io.github.kyant0:backdrop-android:2.0.1-liquidkit.1`

---

## 2. 主题、材质与颜色 (`LiquidKitTheme.swift`)

```swift
import SwiftUI
import LiquidKit

struct ContentView: View {
    @State private var opacity = 0.5

    var body: some View {
        // dark: nil 表示跟随系统深浅色；传入 true / false 可显式锁定外观
        LiquidKitRoot(opacity: opacity, dark: nil) {
            VStack(spacing: 14) {
                LiquidGlassSurfaceView(
                    variant: .regular,         // .regular | .clear | .identity
                    cornerRadius: 20,          // nil 表示胶囊形状 Capsule
                    tint: .foldTeal,
                    interactive: true
                ) {
                    Text("Standalone Glass Card")
                        .padding(.horizontal, 18)
                        .padding(.vertical, 12)
                }

                // 或使用链式 View 扩展修饰符
                Text("Applied via modifier")
                    .padding()
                    .liquidGlass(variant: .clear, cornerRadius: 16, interactive: true)

                // 近距离自动融合容器（对应 iOS 26+ GlassEffectContainer）
                LiquidGlassContainer(spacing: 8.0) {
                    HStack(spacing: 8) {
                        LiquidButton("编辑", systemImage: "pencil", style: .glass) {}
                        LiquidButton("完成", style: .glassProminent, tint: .accentBlue) {}
                    }
                }
            }
            .padding()
        }
    }
}
```

可用预设常量与动画令牌：
- 颜色：`LiquidRGBA.accentBlue`、`LiquidRGBA.foldTeal`、`LiquidRGBA.destructiveRed` 或 `LiquidRGBA(red:green:blue:alpha:)`。
- 弹簧：`LiquidSpringToken.snappy`、`LiquidSpringToken.smooth`、`LiquidSpringToken.bouncy`（或自定义 `LiquidSpringToken(response:dampingFraction:)`）。

---

## 3. 按钮与菜单 (`LiquidKitButtons.swift`)

```swift
// 按钮样式：.plain | .borderless | .bordered | .borderedProminent | .glass | .glassProminent
// 尺寸：.mini | .small | .regular | .large | .extraLarge
// 形状：.capsule | .circle | .roundedRectangle
LiquidButton(
    "新建文件夹",
    systemImage: "folder.badge.plus",
    style: .glassProminent,
    tint: .accentBlue,
    controlSize: .regular,
    shape: .capsule,
    destructive: false
) {
    createFolder()
}

// 锚定变体菜单（borderless: true 使用浮层弹窗样式，false 使用按钮变形样式）
LiquidMenuControl(
    "操作",
    items: [
        LiquidMenuEntry(id: "rename", title: "重命名", systemImage: "pencil"),
        LiquidMenuEntry(id: "star", title: "标星", systemImage: "star.fill", checked: true),
        LiquidMenuEntry(id: "delete", title: "移除", systemImage: "trash", destructive: true),
    ],
    borderless: false,
    tint: .accentBlue
) { selectedId in
    handleMenuAction(selectedId)
}
```

---

## 4. 表单与选择器控件 (`LiquidKitControls.swift`)

```swift
@State private var isEnabled = true
@State private var pinned = false
@State private var sliderVal = 0.25
@State private var glassOpacity = 0.5
@State private var count = 4
@State private var segment = "网格"
@State private var menuChoice = "自动"
@State private var dueDate = LiquidDateValue(year: 2026, month: 11, day: 25)
@State private var accent = LiquidRGBA.accentBlue
@State private var tabOption = "overview"

VStack(spacing: 12) {
    LiquidSwitch("显示隐藏文件", isOn: $isEnabled)
    LiquidTogglePill("固定顶栏", systemImage: "pin.fill", isOn: $pinned)

    LiquidSliderControl(
        value: $sliderVal,
        in: -1...1,
        ticks: [-1, -0.5, 0, 0.5, 1],
        neutralValue: 0
    )

    LiquidOpacityControl(opacity: $glassOpacity, label: "玻璃不透明度")
    LiquidStepperControl("数量", value: $count, in: 1...20)
    LiquidSegmentedControl(options: ["列表", "网格", "分栏"], selection: $segment)
    LiquidMenuPickerControl("风速", options: ["自动", "低", "高"], selection: $menuChoice)

    LiquidTabsPickerControl(
        options: [
            LiquidTabOptionItem(id: "overview", label: "概览", systemImage: "sparkles"),
            LiquidTabOptionItem(id: "code", label: "源码", systemImage: "chevron.left.forwardslash.chevron.right"),
        ],
        selection: $tabOption
    )

    LiquidDatePickerControl("计划日期", date: $dueDate, style: "compact")
    LiquidColorPickerControl("强调色", color: $accent, supportsOpacity: true)
}
```

其他选择器：`LiquidInlinePickerControl`、`LiquidPalettePickerControl(options:selection:)`、`LiquidMultiDatePickerControl(selectedIsoDates:)`（基于 `YYYY-MM-DD` 字符串数组实现零成本跨端同步）。

---

## 5. 输入、容器、边缘栏与视图转场 (`LiquidKitContainers.swift`, `LiquidKitNavigation.swift`)

```swift
@State private var text = ""
@State private var query = ""
@State private var expanded = false

LiquidTextInput("请输入名称", text: $text, roundedBorder: true)
LiquidSearchBar(query: $query, placeholder: "搜索文件", cancelLabel: "取消")

LiquidKeyValueRow("版本", value: "1.0.0-alpha.3")
LiquidProgress(value: 0.72) // value 为 nil 时展示 Apple 风格加载指示器
LiquidGaugeControl(value: 0.68, label: "电量", style: "circular") // circular | linear | circularOpen

LiquidDisclosure("高级选项", isExpanded: $expanded) {
    Text("展开内容")
}

LiquidGroupCard("概览") {
    LiquidBadgeView("NEW", tint: .accentBlue)
    Text("内容区域").liquidRedacted(active: false)
}

LiquidEmptyView("暂无文件", systemImage: "folder", description: "点击右上角新建")

// iOS 27 风格边缘渐进模糊栏
LiquidSafeAreaBarView(edge: .bottom, blurRadius: 16) {
    Text("已选择 2 项")
    Spacer()
    LiquidButton("分享", style: .glassProminent) {}
}

// 轻量视图切换动画（.crossFade | .zoom | .slide）
LiquidViewSwitcher(key: segment, style: .crossFade) { current in
    Text("当前视图: \(current)")
}
```

---

## 6. 底栏与模态弹窗 (`LiquidKitNavigation.swift`)

```swift
@State private var tab = "recent"
@State private var minimized = false
@State private var showSheet = false
@State private var showAlert = false
@State private var showActionSheet = false
@State private var showPopover = false

LiquidFloatingTabBar(
    tabs: [
        LiquidTabItem(id: "recent", title: "最近", systemImage: "clock.fill"),
        LiquidTabItem(id: "shared", title: "已共享", systemImage: "person.2.fill"),
        LiquidTabItem(id: "browse", title: "浏览", systemImage: "folder.fill"),
    ],
    selection: $tab,
    minimized: minimized,
    onExpand: { minimized = false },
    searchAction: { showSheet = true }
)

LiquidModalSheet(isPresented: $showSheet, title: "详细信息", mediumDetent: true, largeDetent: true) {
    Text("支持多档位高度切换与预测式返回手势")
}

LiquidAlertModal(
    isPresented: $showAlert,
    title: "确认操作？",
    message: "操作完成后可随时重新编辑。",
    actions: [
        LiquidDialogAction(label: "取消", role: .cancel) {},
        LiquidDialogAction(label: "继续", role: .primary) {},
    ]
)

LiquidActionSheetModal(
    isPresented: $showActionSheet,
    title: "选择操作",
    actions: [
        LiquidDialogAction(label: "重命名") {},
        LiquidDialogAction(label: "永久删除", role: .destructive) {},
    ]
)

LiquidPopoverView(isPresented: $showPopover) {
    Text("锚定气泡提示")
}
```

---

## 7. 高级集合、图表与网格渐变 (`LiquidKitGallery.swift`)

- `LiquidMeshGradientView(width:height:colors:)`
- `LiquidReorderableGridView(ids:columns:tileHeight:spacing:tileContent:)`（长按跨格拖拽重排）
- `LiquidInspectorContainer(isPresented:title:inspectorContent:content:)`
- `LiquidTabViewBottomAccessoryView(onTap:content:)`
- `LiquidFlowLayoutView`、`LiquidOutlineGroupView`、`LiquidTableView`
- `LiquidBarChartView`、`LiquidLineAreaChartView`、`LiquidPieDonutChartView`、`LiquidMathPlotChartView`
- `LiquidTipBannerView`
