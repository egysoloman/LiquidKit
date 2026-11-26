import SwiftUI

#if SKIP
// SKIP INSERT: import androidx.compose.ui.graphics.Color as ComposeColor
// SKIP INSERT: import io.liquidkit.skip.*
#endif

/// Item descriptor for hero shared-element transitions across SwiftUI and Compose.
public struct LiquidSharedHeroItem: Identifiable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let badge: String
    public let systemImage: String
    public let tint: LiquidRGBA
    public let summary: String

    public init(
        id: String,
        title: String,
        subtitle: String,
        badge: String,
        systemImage: String,
        tint: LiquidRGBA,
        summary: String
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.badge = badge
        self.systemImage = systemImage
        self.tint = tint
        self.summary = summary
    }
}

/// Shared transition presentation style for hero cards.
public enum LiquidHeroTransitionMode: String, CaseIterable, Sendable {
    case matchedGeometry
    case zoomNavigation
}

/// Track state for the docked mini-player <-> expanded player deck morph.
public struct LiquidSharedPlayerTrack: Identifiable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let artist: String
    public let bpm: Int
    public let tint: LiquidRGBA
    public let isPlaying: Bool
    public let progress: Double

    public init(
        id: String,
        title: String,
        artist: String,
        bpm: Int,
        tint: LiquidRGBA,
        isPlaying: Bool = true,
        progress: Double = 0.42
    ) {
        self.id = id
        self.title = title
        self.artist = artist
        self.bpm = bpm
        self.tint = tint
        self.isPlaying = isPlaying
        self.progress = min(1.0, max(0.0, progress))
    }
}

/// Glass morphing mode comparing coordinate bounds interpolation against optical SDF fusion.
public enum LiquidGlassMorphMode: String, CaseIterable, Sendable {
    case bounds
    case liquidSDF
}

#if !SKIP
private struct HeroSubstrateBackground: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color.blue.opacity(0.14),
                    Color.purple.opacity(0.12),
                    Color.teal.opacity(0.12)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Circle()
                .fill(Color.blue.opacity(0.24))
                .frame(width: 180, height: 180)
                .blur(radius: 32)
                .offset(x: -90, y: -60)
            Circle()
                .fill(Color.mint.opacity(0.22))
                .frame(width: 170, height: 170)
                .blur(radius: 30)
                .offset(x: 95, y: 70)
        }
    }
}

private struct HeroAdaptiveSurfaceModifier: ViewModifier {
    let cornerRadius: CGFloat
    let tint: Color
    let interactive: Bool

    func body(content: Content) -> some View {
        if #available(iOS 26.0, macOS 26.0, *) {
            if interactive {
                content
                    .glassEffect(
                        .regular.tint(tint.opacity(0.14)).interactive(),
                        in: .rect(cornerRadius: cornerRadius)
                    )
            } else {
                content
                    .glassEffect(
                        .regular.tint(tint.opacity(0.16)),
                        in: .rect(cornerRadius: cornerRadius)
                    )
            }
        } else {
            content
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .stroke(Color.white.opacity(0.24), lineWidth: 0.75)
                )
        }
    }
}

private struct ZoomHeroDetailScreen: View {
    let item: LiquidSharedHeroItem

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 14) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(item.tint.swiftUIColor.opacity(0.22))
                        Image(systemName: item.systemImage)
                            .font(.system(size: 28, weight: .semibold))
                            .foregroundStyle(item.tint.swiftUIColor)
                    }
                    .frame(width: 60, height: 60)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.title)
                            .font(.title2.bold())
                        Text(item.subtitle)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                HStack(spacing: 8) {
                    ForEach([item.badge, "matchedTransitionSource", "navigationTransition(.zoom)"], id: \.self) { tag in
                        Text(tag)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(item.tint.swiftUIColor)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(item.tint.swiftUIColor.opacity(0.14), in: Capsule())
                    }
                }

                Text(item.summary)
                    .font(.body)
                    .lineSpacing(5)
                    .foregroundStyle(.primary)

                VStack(alignment: .leading, spacing: 8) {
                    Text("iOS 原生 Zoom 共享转场链路")
                        .font(.headline)
                    Text("通过 NavigationStack 的 .matchedTransitionSource(id:in:) 与目标页的 .navigationTransition(.zoom(sourceID:in:)) 建立系统级连续拖拽手势与容器缩放映射。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 20, tint: item.tint.swiftUIColor, interactive: false))
            }
            .padding(20)
        }
        .background(HeroSubstrateBackground().ignoresSafeArea())
        .navigationTitle(item.title)
    }
}
#endif

/// Cross-platform Hero Card <-> Detail shared-element transition.
///
/// - On iOS/macOS (`#if !SKIP`): supports both in-place `@Namespace` `.matchedGeometryEffect` and
///   iOS 18+/27 `.navigationTransition(.zoom(sourceID:in:))` inside a parent `NavigationStack`.
/// - On Android (`#if SKIP`): routes to `SkipLiquidKitSharedHeroTransition` (`SharedTransitionLayout`
///   + `sharedBounds` / `sharedElement` + `OverlayClip` with a sibling `LayerBackdrop`).
public struct LiquidSharedHeroView: View {
    public let items: [LiquidSharedHeroItem]
    @Binding private var selectedId: String?
    public let mode: LiquidHeroTransitionMode
    public let columns: Int

    #if !SKIP
    @Namespace private var heroNamespace
    @State private var zoomTargetItem: LiquidSharedHeroItem?
    #endif

    public init(
        items: [LiquidSharedHeroItem],
        selectedId: Binding<String?>,
        mode: LiquidHeroTransitionMode = .matchedGeometry,
        columns: Int = 2
    ) {
        self.items = items
        _selectedId = selectedId
        self.mode = mode
        self.columns = max(1, min(3, columns))
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitSharedHeroTransition(
                ids: items.map { $0.id }.toList(),
                titles: items.map { $0.title }.toList(),
                subtitles: items.map { $0.subtitle }.toList(),
                badges: items.map { $0.badge }.toList(),
                systemImages: items.map { $0.systemImage }.toList(),
                tints: items.map { $0.tint.composeColor }.toList(),
                summaries: items.map { $0.summary }.toList(),
                selectedId: selectedId,
                columns: columns,
                modifier: context.modifier,
                onSelectId: { selectedId = $0 }
            )
        }
        #else
        nativeBody
        #endif
    }

    #if !SKIP
    @ViewBuilder
    private var nativeBody: some View {
        let selectedItem = items.first(where: { $0.id == selectedId })
        ZStack {
            HeroSubstrateBackground()

            if mode == .zoomNavigation {
                gridContent(isZoomNavigation: true)
                    .padding(12)
                    .navigationDestination(item: $zoomTargetItem) { item in
                        #if os(iOS)
                        ZoomHeroDetailScreen(item: item)
                            .navigationTransition(.zoom(sourceID: item.id, in: heroNamespace))
                        #else
                        ZoomHeroDetailScreen(item: item)
                        #endif
                    }
            } else if let detail = selectedItem {
                detailCard(detail)
                    .padding(12)
                    .transition(.asymmetric(insertion: .opacity, removal: .opacity))
            } else {
                gridContent(isZoomNavigation: false)
                    .padding(12)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .animation(LiquidSpringToken(response: 0.36, dampingFraction: 0.84).animation, value: selectedId)
    }

    private func gridContent(isZoomNavigation: Bool) -> some View {
        let gridColumns = Array(repeating: GridItem(.flexible(), spacing: 12), count: columns)
        return LazyVGrid(columns: gridColumns, spacing: 12) {
            ForEach(items) { item in
                Button {
                    if isZoomNavigation {
                        zoomTargetItem = item
                    } else {
                        withAnimation(.spring(response: 0.36, dampingFraction: 0.84)) {
                            selectedId = item.id
                        }
                    }
                } label: {
                    cardLabel(item: item, isZoomNavigation: isZoomNavigation)
                }
                .buttonStyle(.plain)
                .modifier(ZoomSourceModifier(id: item.id, namespace: heroNamespace, enabled: isZoomNavigation))
            }
        }
    }

    @ViewBuilder
    private func cardLabel(item: LiquidSharedHeroItem, isZoomNavigation: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(item.tint.swiftUIColor.opacity(0.22))
                    Image(systemName: item.systemImage)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(item.tint.swiftUIColor)
                }
                .frame(width: 40, height: 40)
                .matchedGeometryEffect(id: "hero-icon-\(item.id)", in: heroNamespace, isSource: !isZoomNavigation)

                Spacer(minLength: 4)

                Text(item.badge)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(item.tint.swiftUIColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(item.tint.swiftUIColor.opacity(0.16), in: Capsule())
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .matchedGeometryEffect(id: "hero-title-\(item.id)", in: heroNamespace, isSource: !isZoomNavigation)

                Text(item.subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 22, tint: item.tint.swiftUIColor, interactive: true))
        .matchedGeometryEffect(id: "hero-card-\(item.id)", in: heroNamespace, isSource: !isZoomNavigation)
    }

    private func detailCard(_ detail: LiquidSharedHeroItem) -> some View {
        let currentIndex = items.firstIndex(where: { $0.id == detail.id }) ?? 0
        let prevItem = items.isEmpty ? nil : items[(currentIndex - 1 + items.count) % items.count]
        let nextItem = items.isEmpty ? nil : items[(currentIndex + 1) % items.count]

        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(detail.tint.swiftUIColor.opacity(0.24))
                    Image(systemName: detail.systemImage)
                        .font(.system(size: 26, weight: .semibold))
                        .foregroundStyle(detail.tint.swiftUIColor)
                }
                .frame(width: 56, height: 56)
                .matchedGeometryEffect(id: "hero-icon-\(detail.id)", in: heroNamespace)

                VStack(alignment: .leading, spacing: 3) {
                    Text(detail.title)
                        .font(.title3.bold())
                        .matchedGeometryEffect(id: "hero-title-\(detail.id)", in: heroNamespace)
                    Text(detail.subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Button {
                    withAnimation(.spring(response: 0.34, dampingFraction: 0.85)) {
                        selectedId = nil
                    }
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                        .frame(width: 36, height: 36)
                        .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 18, tint: detail.tint.swiftUIColor, interactive: true))
                }
                .buttonStyle(.plain)
            }

            HStack(spacing: 6) {
                ForEach([detail.badge, "matchedGeometryEffect", "GlassEffect"], id: \.self) { tag in
                    Text(tag)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(detail.tint.swiftUIColor)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(detail.tint.swiftUIColor.opacity(0.15), in: Capsule())
                }
            }

            Text(detail.summary)
                .font(.subheadline)
                .lineSpacing(4)
                .foregroundStyle(.primary)

            HStack {
                if let prevItem, items.count > 1 {
                    Button {
                        withAnimation(.spring(response: 0.34, dampingFraction: 0.84)) {
                            selectedId = prevItem.id
                        }
                    } label: {
                        Label("上一张", systemImage: "chevron.backward")
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 16, tint: detail.tint.swiftUIColor, interactive: true))
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                if let nextItem, items.count > 1 {
                    Button {
                        withAnimation(.spring(response: 0.34, dampingFraction: 0.84)) {
                            selectedId = nextItem.id
                        }
                    } label: {
                        Label("下一张", systemImage: "chevron.forward")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(detail.tint.swiftUIColor)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 16, tint: detail.tint.swiftUIColor, interactive: true))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, minHeight: 280, alignment: .topLeading)
        .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 28, tint: detail.tint.swiftUIColor, interactive: false))
        .matchedGeometryEffect(id: "hero-card-\(detail.id)", in: heroNamespace)
    }
    #endif
}

#if !SKIP
private struct ZoomSourceModifier: ViewModifier {
    let id: String
    let namespace: Namespace.ID
    let enabled: Bool

    func body(content: Content) -> some View {
        #if os(iOS)
        if enabled {
            content.matchedTransitionSource(id: id, in: namespace)
        } else {
            content
        }
        #else
        content
        #endif
    }
}
#endif

/// Cross-platform floating mini-player capsule <-> expanded glass player deck.
public struct LiquidSharedPlayerMorphView: View {
    public let track: LiquidSharedPlayerTrack
    @Binding private var expanded: Bool
    private let onTogglePlay: () -> Void
    private let onNextTrack: (() -> Void)?

    #if !SKIP
    @Namespace private var playerNamespace
    #endif

    public init(
        track: LiquidSharedPlayerTrack,
        expanded: Binding<Bool>,
        onTogglePlay: @escaping () -> Void,
        onNextTrack: (() -> Void)? = nil
    ) {
        self.track = track
        _expanded = expanded
        self.onTogglePlay = onTogglePlay
        self.onNextTrack = onNextTrack
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitSharedPlayerMorph(
                id: track.id,
                title: track.title,
                artist: track.artist,
                bpm: track.bpm,
                tint: track.tint.composeColor,
                isPlaying: track.isPlaying,
                progress: track.progress,
                expanded: expanded,
                modifier: context.modifier,
                onExpandedChange: { expanded = $0 },
                onTogglePlay: onTogglePlay,
                onNextTrack: onNextTrack
            )
        }
        #else
        nativeBody
        #endif
    }

    #if !SKIP
    @ViewBuilder
    private var nativeBody: some View {
        Group {
            if !expanded {
                HStack(spacing: 10) {
                    artworkTile(size: 38)
                        .matchedGeometryEffect(id: "player-art", in: playerNamespace)

                    VStack(alignment: .leading, spacing: 1) {
                        Text(track.title)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                            .matchedGeometryEffect(id: "player-title", in: playerNamespace)
                        Text("\(track.artist) · \(track.bpm) FPS Target")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    Spacer(minLength: 4)

                    Button(action: onTogglePlay) {
                        Image(systemName: track.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(track.tint.swiftUIColor)
                            .frame(width: 34, height: 34)
                            .background(track.tint.swiftUIColor.opacity(0.20), in: Circle())
                    }
                    .buttonStyle(.plain)
                    .matchedGeometryEffect(id: "player-play", in: playerNamespace)
                }
                .padding(.horizontal, 12)
                .frame(height: 58)
                .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 28, tint: track.tint.swiftUIColor, interactive: true))
                .matchedGeometryEffect(id: "player-surface", in: playerNamespace)
                .contentShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                .onTapGesture {
                    withAnimation(.spring(response: 0.34, dampingFraction: 0.84)) {
                        expanded = true
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 12) {
                        artworkTile(size: 62)
                            .matchedGeometryEffect(id: "player-art", in: playerNamespace)

                        VStack(alignment: .leading, spacing: 3) {
                            Text(track.title)
                                .font(.headline.bold())
                                .lineLimit(1)
                                .matchedGeometryEffect(id: "player-title", in: playerNamespace)
                            Text("\(track.artist) · \(track.bpm) Hz Engine")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        Button {
                            withAnimation(.spring(response: 0.30, dampingFraction: 0.86)) {
                                expanded = false
                            }
                        } label: {
                            Image(systemName: "chevron.down")
                                .font(.system(size: 13, weight: .semibold))
                                .frame(width: 34, height: 34)
                                .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 17, tint: track.tint.swiftUIColor, interactive: true))
                        }
                        .buttonStyle(.plain)
                    }

                    HStack(spacing: 4) {
                        ForEach(0..<22, id: \.self) { idx in
                            let active = Double(idx) / 22.0 <= track.progress
                            let height = 8.0 + 18.0 * abs(sin(Double(idx + 1) * 0.72))
                            RoundedRectangle(cornerRadius: 2)
                                .fill(active ? track.tint.swiftUIColor : Color.secondary.opacity(0.28))
                                .frame(maxWidth: .infinity)
                                .frame(height: height)
                        }
                    }
                    .frame(height: 26)

                    HStack {
                        Text("matchedGeometry + Glass Capsule")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button(action: onTogglePlay) {
                            Image(systemName: track.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(track.tint.swiftUIColor)
                                .frame(width: 40, height: 40)
                                .background(track.tint.swiftUIColor.opacity(0.22), in: Circle())
                        }
                        .buttonStyle(.plain)
                        .matchedGeometryEffect(id: "player-play", in: playerNamespace)

                        if let onNextTrack {
                            Button(action: onNextTrack) {
                                Label("切歌", systemImage: "forward.fill")
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 18, tint: track.tint.swiftUIColor, interactive: true))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(16)
                .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 30, tint: track.tint.swiftUIColor, interactive: false))
                .matchedGeometryEffect(id: "player-surface", in: playerNamespace)
            }
        }
        .animation(.spring(response: 0.34, dampingFraction: 0.84), value: expanded)
    }

    private func artworkTile(size: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [track.tint.swiftUIColor, track.tint.swiftUIColor.opacity(0.55), Color.indigo],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Image(systemName: track.isPlaying ? "sparkles" : "drop.halffull")
                .font(.system(size: size * 0.42, weight: .semibold))
                .foregroundStyle(.white)
        }
        .frame(width: size, height: size)
    }
    #endif
}

/// Compares Coordinate Bounds Morph (`matchedGeometryEffect` / `sharedBounds`) against
/// Optical SDF Liquid Morph (`GlassEffectContainer` + `.glassEffectID` on iOS 26+, `LiquidMorphContainer` on Android).
public struct LiquidSharedGlassMorphView: View {
    @Binding private var expanded: Bool
    public let mode: LiquidGlassMorphMode

    #if !SKIP
    @Namespace private var morphNamespace
    #endif

    public init(expanded: Binding<Bool>, mode: LiquidGlassMorphMode = .liquidSDF) {
        _expanded = expanded
        self.mode = mode
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitSharedGlassMorph(
                expanded: expanded,
                useOpticalSdf: mode == .liquidSDF,
                modifier: context.modifier,
                onExpandedChange: { expanded = $0 }
            )
        }
        #else
        nativeBody
        #endif
    }

    #if !SKIP
    @ViewBuilder
    private var nativeBody: some View {
        ZStack {
            HeroSubstrateBackground()

            if mode == .liquidSDF {
                if #available(iOS 26.0, macOS 26.0, *) {
                    GlassEffectContainer(spacing: 28) {
                        morphControlSurface(useGlassEffectId: true)
                    }
                } else {
                    morphControlSurface(useGlassEffectId: false)
                }
            } else {
                morphControlSurface(useGlassEffectId: false)
            }
        }
        .frame(height: 216)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .animation(.spring(response: 0.34, dampingFraction: 0.80), value: expanded)
    }

    @ViewBuilder
    private func morphControlSurface(useGlassEffectId: Bool) -> some View {
        if !expanded {
            Button {
                withAnimation(.spring(response: 0.34, dampingFraction: 0.80)) {
                    expanded = true
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: mode == .liquidSDF ? "drop.halffull" : "square.on.square")
                        .foregroundStyle(mode == .liquidSDF ? Color.teal : Color.blue)
                    Text(mode == .liquidSDF ? "SDF 光学形变" : "Bounds 共享")
                        .font(.subheadline.weight(.semibold))
                }
                .frame(width: 156, height: 48)
            }
            .buttonStyle(.plain)
            .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 24, tint: mode == .liquidSDF ? .teal : .blue, interactive: true))
            .modifier(MorphIdentityModifier(id: "glass-morph-panel", namespace: morphNamespace, useGlassEffectId: useGlassEffectId))
        } else {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text(mode == .liquidSDF ? "LiquidKit · SDF Glass Morph" : "LiquidKit · Matched Bounds")
                        .font(.subheadline.bold())
                    Spacer()
                    Button {
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.85)) {
                            expanded = false
                        }
                    } label: {
                        Image(systemName: "xmark")
                            .font(.footnote.weight(.bold))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }

                HStack(spacing: 8) {
                    ForEach(Array(["折射棱镜", "色散边缘", "收起"].enumerated()), id: \.offset) { idx, title in
                        Button {
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.85)) {
                                expanded = (idx != 2)
                            }
                        } label: {
                            Text(title)
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(idx == 2 ? (mode == .liquidSDF ? Color.teal : Color.blue) : .primary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                                .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 16, tint: mode == .liquidSDF ? .teal : .blue, interactive: true))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
            .frame(width: 304, height: 128)
            .modifier(HeroAdaptiveSurfaceModifier(cornerRadius: 28, tint: mode == .liquidSDF ? .teal : .blue, interactive: false))
            .modifier(MorphIdentityModifier(id: "glass-morph-panel", namespace: morphNamespace, useGlassEffectId: useGlassEffectId))
        }
    }
    #endif
}

#if !SKIP
private struct MorphIdentityModifier: ViewModifier {
    let id: String
    let namespace: Namespace.ID
    let useGlassEffectId: Bool

    func body(content: Content) -> some View {
        if useGlassEffectId, #available(iOS 26.0, macOS 26.0, *) {
            content
                .glassEffectID(id, in: namespace)
                .glassEffectTransition(.matchedGeometry)
        } else {
            content.matchedGeometryEffect(id: id, in: namespace)
        }
    }
}
#endif
