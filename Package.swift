// swift-tools-version: 6.0
import Foundation
import PackageDescription

let skipEnabled = ProcessInfo.processInfo.environment["LIQUIDKIT_NO_SKIP"] != "1"

var packageDependencies: [Package.Dependency] = []
var targetDependencies: [Target.Dependency] = []
var targetPlugins: [Target.PluginUsage] = []

if skipEnabled {
    packageDependencies += [
        .package(url: "https://github.com/skiptools/skip.git", from: "1.9.23"),
        .package(url: "https://github.com/skiptools/skip-ui.git", from: "1.0.0"),
    ]
    targetDependencies += [
        .product(name: "SkipUI", package: "skip-ui"),
    ]
    targetPlugins += [
        .plugin(name: "skipstone", package: "skip"),
    ]
}

let package = Package(
    name: "LiquidKit",
    defaultLocalization: "zh-Hans",
    platforms: [
        .iOS(.v18),
        .macOS(.v15),
    ],
    products: [
        .library(name: "LiquidKit", targets: ["LiquidKit"]),
    ],
    dependencies: packageDependencies,
    targets: [
        .target(
            name: "LiquidKit",
            dependencies: targetDependencies,
            exclude: ["Skip"],
            plugins: targetPlugins
        ),
        .testTarget(
            name: "LiquidKitTests",
            dependencies: ["LiquidKit"]
        ),
    ]
)
