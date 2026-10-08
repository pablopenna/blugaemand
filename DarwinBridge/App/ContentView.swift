import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @State private var importing = false
    @State private var importingApp = false
    @State private var guestApps: [DBGuestApp] = []
    @State private var guestError: String?
    @State private var guestRunReports: [String: String] = [:]
    @State private var fileName = "No Mach-O selected"
    @State private var image: MachOImageInfo?
    @State private var report: CompatibilityReport?
    @State private var errorText: String?
    @State private var mappedSummary: String?
    @State private var mappedImage: MappedMachOImage?
    @State private var importedData: Data?
    @State private var fixupPlan: FixupPlan?
    @State private var guestSpace: GuestAddressSpace?
    @State private var appliedFixups: AppliedFixups?
    @State private var jitResult: JITExecutionResult?
    @State private var runtimeReport: RuntimeCompatibilityReport?
    @State private var runtimeCallResult: RuntimeCallResult?
    @State private var runtimeChainResult: RuntimeCallResult?
    @State private var appKitBridgeResult: AppKitBridgeTestResult?
    @State private var graphicsResult: GraphicsCommandTestResult?
    @State private var inputSequence: UInt64 = 0
    @State private var inputSummary = "none"
    @State private var realMachOPreflight: RealMachOPreflight?
    @State private var stage9Readiness: Stage9ReadinessReport?
    @State private var lolReadiness: LoLReadinessReport?
    @State private var runtimeFoundation: RuntimeFoundationReport?
    @State private var stage11To14: Stage11To14Report?
    @State private var lolStage15: LoLStage15Report?
    @State private var classicSymbols: ClassicSymbolSurfaceReport?
    @State private var deepSymbols: DeepSymbolScanReport?
    @State private var lolCompatibilityPlan: LoLCompatibilityPlan?
    @State private var lolShimCoverage: LoLShimCoverageReport?
    @State private var lolRuntimeValidation: LoLRuntimeShimValidation?
    @State private var lolStage17Preflight: LoLStage17Preflight?
    @State private var lolLaunchDryRun: LoLLaunchDryRunReport?
    @State private var lolRuntimeDiagnostics: LoLRuntimeDiagnosticReport?
    @State private var liveContainerBackend: LiveContainerBackendReport?
    @State private var externalHandoff: LoLExternalHandoffReadiness?
    @State private var firstRunPackageURL: URL?
    @State private var firstRunPackageSummary: String?
    @StateObject private var runtimeEvents = LoLRuntimeEventLog.shared
    @State private var stikDebugStatus = "not requested"

    var body: some View {
        NavigationStack {
            List {
                Section("macOS Guest Library — DarwinBridge 2.0") {
                    Button("Install macOS .app bundle") { importingApp = true }
                    ForEach(guestApps) { guest in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(guest.name).font(.headline)
                            Text("macOS ARM64 • " + guest.executable).font(.caption)
                            Text("Imports: \(guest.importCount) • unresolved: \(guest.unresolvedCount)").font(.caption)
                            Text(guest.blockers.isEmpty ? "Dependency inspection pending" : guest.blockers.joined(separator: "; "))
                                .font(.caption2).foregroundStyle(.secondary)
                            Button("Check guest launch readiness") {
                                do {
                                    guestRunReports[guest.id] = try DBGuestExecution.preflight(guest).description
                                } catch {
                                    guestRunReports[guest.id] = "Preflight failed: " + error.localizedDescription
                                }
                            }
                            if let details = guestRunReports[guest.id] {
                                Text(details).font(.caption2).monospaced()
                                    .textSelection(.enabled)
                            }
                            Text("Guest execution is not yet implemented; installation is not an execution claim.")
                                .font(.caption2).foregroundStyle(.orange)
                        }
                    }
                    if let guestError { Text(guestError).foregroundStyle(.red) }
                }
                Section("Target") {
                    Text(fileName).font(.headline)
                    Button("Import macOS Mach-O") { importing = true }
                }

                if let image {
                    Section("Mach-O") {
                        row("CPU", image.isArm64 ? "ARM64" : String(format: "0x%08X", image.cpuType))
                        row("Segments", "\(image.segments.count)")
                        row("Dependencies", "\(image.dependencies.count)")
                        row("Entry offset", image.entryOffset.map { String(format: "0x%llX", $0) } ?? "none")
                        row("Minimum macOS", image.minimumOS ?? "unknown")
                        row("SDK", image.sdk ?? "unknown")
                        row("Encrypted", image.encrypted ? "yes" : "no")
                        row("Chained fixups", image.chainedFixups == nil ? "none" : "present")
                    }

                    Section("Stage-0 loader") {
                        Button("Map segments for inspection") { map(image) }
                            .disabled(!image.isArm64 || image.encrypted)

                        if let mappedSummary {
                            Text(mappedSummary)
                                .font(.caption)
                                .monospaced()
                        }
                    }

                    Section("Stage-1 dyld") {
                        Button("Analyze chained fixups") { analyzeFixups(image) }
                            .disabled(image.chainedFixups == nil)

                        if let fixupPlan {
                            row("Imports", "\(fixupPlan.info.imports.count)")
                            row("Fixup pointers", "\(fixupPlan.supportedFixupCount)")
                            row("Unresolved binds", "\(fixupPlan.unresolvedBindCount)")
                            row("Apply-ready", fixupPlan.canApplyStage1 ? "yes" : "no")

                            if !fixupPlan.info.unsupportedPointerFormats.isEmpty {
                                let values = fixupPlan.info.unsupportedPointerFormats
                                    .sorted()
                                    .map(String.init)
                                    .joined(separator: ", ")
                                Text("Unsupported pointer formats: \(values)")
                                    .font(.caption)
                                    .foregroundStyle(.orange)
                            }

                            ForEach(fixupPlan.notes, id: \.self) {
                                Text($0)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }

                    Section("Stage-2 runtime") {
                        Button("Load guest + apply fixups") {
                            loadGuest(image)
                        }
                        .disabled(fixupPlan?.canApplyStage1 != true)

                        if let guestSpace, let appliedFixups {
                            row("Guest base", String(format: "0x%llX", guestSpace.guestBase))
                            row("Guest end", String(format: "0x%llX", guestSpace.guestEnd))
                            row("Mapped bytes", "\(guestSpace.size)")
                            row("Host base", String(describing: guestSpace.base))
                            row("Rebases applied", "\(appliedFixups.rebases)")
                            row("Binds applied", "\(appliedFixups.binds)")

                            if let plan = fixupPlan {
                                let expected = plan.supportedFixupCount
                                let actual = appliedFixups.rebases + appliedFixups.binds
                                row("Fixup verification", actual == expected ? "passed" : "\(actual)/\(expected)")
                            }

                            Text("Guest memory is now allocated as one contiguous address space and supported chained rebases/binds have been written into it.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else {
                            Text("Analyze chained fixups first, then load the guest to apply them to real mapped memory.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Section("Stage-3 execution capability") {
                        Button("Run ARM64 return-42 test") {
                            jitResult = JITExecutionBackend.runReturn42SelfTest()
                        }

                        if let jitResult {
                            row("Debugger attached", jitResult.debuggerAttached ? "yes" : "no")
                            row("RW region", jitResult.regionAllocated ? "yes" : "no")
                            row("JIT26 prepared", jitResult.regionPrepared ? "yes" : "no")
                            row("Execution attempted", jitResult.executionAttempted ? "yes" : "no")
                            row("Guest execution", jitResult.executed ? "PASS" : "not executed")
                            row("Return value", jitResult.returnValue.map(String.init) ?? "—")
                            if jitResult.errnoValue != 0 {
                                row("errno", "\(jitResult.errnoValue)")
                            }
                            Text(jitResult.note)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Text("On M2/iOS 27, launch through LiveContainer with JIT and use darwinbridge-universal.js as the JIT Launch Script, then run this test.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-4 macOS runtime") {
                        Button("Probe libSystem compatibility") {
                            runtimeReport = RuntimeCompatibility.probeCoreRuntime()
                        }

                        if let runtimeReport {
                            row("Resolved symbols", "\(runtimeReport.resolvedCount)/\(runtimeReport.totalCount)")
                            row("Core libc ready", runtimeReport.coreReady ? "yes" : "no")

                            ForEach(runtimeReport.probes) { probe in
                                HStack {
                                    Text(probe.symbol).monospaced().font(.caption)
                                    Spacer()
                                    Text(probe.resolved ? "resolved" : "missing")
                                        .font(.caption)
                                        .foregroundStyle(probe.resolved ? Color.secondary : Color.orange)
                                }
                            }
                        }

                        Button("Run guest → strlen test") {
                            runtimeCallResult = JITExecutionBackend.runStrlenRuntimeTest()
                        }

                        if let runtimeCallResult {
                            row("Debugger", runtimeCallResult.debuggerAttached ? "yes" : "no")
                            row("JIT region", runtimeCallResult.regionPrepared ? "yes" : "no")
                            row("Guest call", runtimeCallResult.executed ? "executed" : "not executed")
                            row("strlen result", runtimeCallResult.returnValue.map(String.init) ?? "—")
                            row("Expected", "\(runtimeCallResult.expectedValue)")
                            row("Runtime ABI", runtimeCallResult.returnValue == runtimeCallResult.expectedValue ? "PASS" : "not passed")
                            Text(runtimeCallResult.note)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Button("Run malloc → memcpy → strlen → free") {
                            runtimeChainResult = JITExecutionBackend.runRuntimeChainTest()
                        }

                        if let runtimeChainResult {
                            row("Runtime chain", runtimeChainResult.executed ? "executed" : "not executed")
                            row("Chain result", runtimeChainResult.returnValue.map(String.init) ?? "—")
                            row("Expected", "\(runtimeChainResult.expectedValue)")
                            row("Chain ABI", runtimeChainResult.returnValue == runtimeChainResult.expectedValue ? "PASS" : "not passed")
                            Text(runtimeChainResult.note)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Text("This stage verifies that ARM64 guest code can call selected iOS libSystem/libc symbols using the native AArch64 ABI.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-5 AppKit → UIKit") {
                        Button("Guest create NSWindow / NSView") {
                            let result = JITExecutionBackend.runAppKitWindowBridgeTest()
                            appKitBridgeResult = result

                            if result.passed {
                                Task { @MainActor in
                                    _ = AppKitUIKitBridge.shared.createDemoWindowFacade()
                                }
                            }
                        }

                        if let appKitBridgeResult {
                            row("Debugger", appKitBridgeResult.debuggerAttached ? "yes" : "no")
                            row("JIT region", appKitBridgeResult.regionPrepared ? "yes" : "no")
                            row("Guest executed", appKitBridgeResult.guestExecuted ? "yes" : "no")
                            row("Bridge return", appKitBridgeResult.bridgeReturnValue.map(String.init) ?? "—")
                            row("AppKit bridge", appKitBridgeResult.passed ? "PASS" : "not passed")
                            Text(appKitBridgeResult.note)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Button("Dismiss guest windows") {
                            Task { @MainActor in
                                AppKitUIKitBridge.shared.dismissAllGuestWindows()
                            }
                        }

                        Text("Guest ARM64 now posts through a memory mailbox and returns without calling C, Swift or UIKit. After the guest returns, the host renders the NSWindow/NSView facade on the main actor.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-6 Graphics + Input") {
                        Button("Guest submit graphics commands") {
                            let result = JITExecutionBackend.runGraphicsCommandQueueTest()
                            graphicsResult = result

                            if result.passed {
                                Task { @MainActor in
                                    _ = GraphicsInputBridge.shared.execute(result.commands)
                                }
                            }
                        }

                        if let graphicsResult {
                            row("Debugger", graphicsResult.debuggerAttached ? "yes" : "no")
                            row("JIT region", graphicsResult.regionPrepared ? "yes" : "no")
                            row("Guest executed", graphicsResult.guestExecuted ? "yes" : "no")
                            row("Commands", "\(graphicsResult.commands.count)")
                            row("Graphics queue", graphicsResult.passed ? "PASS" : "not passed")
                            Text(graphicsResult.note)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Button("Read latest input event") {
                            Task { @MainActor in
                                let event = GraphicsInputBridge.shared.lastInput
                                inputSequence = event.sequence
                                inputSummary = event.kind == "none"
                                    ? "none"
                                    : "\(event.kind) @ \(Int(event.x)),\(Int(event.y))"
                            }
                        }

                        row("Input sequence", "\(inputSequence)")
                        row("Latest input", inputSummary)

                        Button("Dismiss graphics surface") {
                            Task { @MainActor in
                                GraphicsInputBridge.shared.dismiss()
                            }
                        }

                        Text("Guest commands cross the boundary through shared memory. UIKit rendering and input capture occur only after guest execution returns.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-7 Real Mach-O Bring-up") {
                        Button("Preflight real entry point") {
                            realMachOPreflight = RealMachOLaunchPipeline.preflight(
                                image: image,
                                guestSpace: guestSpace,
                                fixupPlan: fixupPlan,
                                appliedFixups: appliedFixups
                            )
                        }

                        if let preflight = realMachOPreflight {
                            row("Preflight", preflight.ready ? "READY" : "blocked")
                            row("Unresolved imports", "\(preflight.unresolvedImports)")
                            row("Executable segment", preflight.executableSegment ?? "—")
                            row("Guest entry", preflight.entryGuestAddress.map { String(format: "0x%llX", $0) } ?? "—")
                            row("Mapped entry", preflight.entryHostAddress.map { String(format: "0x%llX", $0) } ?? "—")
                            row("LC_MAIN file offset", preflight.entryFileOffset.map { String(format: "0x%llX", $0) } ?? "—")
                            row("Entry in __TEXT", preflight.entryOffsetInText.map { String(format: "0x%llX", $0) } ?? "—")
                            row("__TEXT file offset", preflight.textFileOffset.map { String(format: "0x%llX", $0) } ?? "—")
                            row("__TEXT file bytes", preflight.textFileSize.map(String.init) ?? "—")
                            row("Executable range", preflight.textRangeValid ? "PASS" : "blocked")
                            row("Stage-8A boundary", preflight.ready && preflight.textRangeValid ? "READY" : "blocked")

                            ForEach(preflight.notes, id: \.self) { note in
                                Text(note)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Text("Stage-7 validates a real imported Mach-O through parse → fixups → symbol resolution → LC_MAIN entry-point discovery. Direct control transfer remains disabled until __TEXT is remapped through JIT26 executable memory.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-9 Lightweight macOS App") {
                        Button("Analyze app bring-up readiness") {
                            stage9Readiness = Stage9ReadinessAnalyzer.analyze(
                                image: image,
                                fixupPlan: fixupPlan
                            )
                        }

                        if let stage9 = stage9Readiness {
                            row("CLI bring-up", stage9.readyForCLIBringUp ? "READY" : "blocked")
                            row("Dependencies", "\(stage9.dependencyCount)")
                            row("Native", "\(stage9.nativeCount)")
                            row("Shim required", "\(stage9.shimCount)")
                            row("Partial", "\(stage9.partialCount)")
                            row("Blocked", "\(stage9.blockedCount)")
                            row("Unknown", "\(stage9.unknownCount)")
                            row("Unresolved imports", "\(stage9.unresolvedImports)")

                            ForEach(stage9.notes, id: \.self) { note in
                                Text(note)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Text("Stage-9 profiles the dependency and symbol surface for the first lightweight real macOS program. It does not transfer control to imported executable code.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("LoL Target Readiness") {
                        Button("Analyze path to League of Legends") {
                            lolReadiness = LoLReadinessAnalyzer.analyze(
                                image: image,
                                fixupPlan: fixupPlan,
                                stage9: stage9Readiness
                            )
                        }

                        if let lol = lolReadiness {
                            row("Foundation tier", lol.tier)
                            row("Current blockers", "\(lol.blockers.count)")
                            row("Bridge work items", "\(lol.requiredBridges.count)")

                            if !lol.blockers.isEmpty {
                                Text("Blockers")
                                    .font(.caption)
                                    .bold()
                                ForEach(lol.blockers, id: \.self) {
                                    Text("• \($0)").font(.caption)
                                }
                            }

                            Text("Required compatibility layers")
                                .font(.caption)
                                .bold()
                            ForEach(lol.requiredBridges, id: \.self) {
                                Text("• \($0)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            ForEach(lol.notes, id: \.self) {
                                Text($0)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }

                    Section("Stage-10 Runtime Foundation Sweep") {
                        Button("Run full runtime foundation sweep") {
                            runtimeFoundation = RuntimeFoundationAnalyzer.analyze(
                                image: image,
                                fixupPlan: fixupPlan
                            )

                            stage9Readiness = runtimeFoundation?.stage9
                            lolReadiness = LoLReadinessAnalyzer.analyze(
                                image: image,
                                fixupPlan: fixupPlan,
                                stage9: runtimeFoundation?.stage9
                            )
                        }

                        if let runtime = runtimeFoundation {
                            row("Foundation sweep", runtime.overallReady ? "READY" : "partial")
                            row("Ready capabilities", "\(runtime.readyCount)/\(runtime.totalCount)")
                            row("Bridge probes", "\(runtime.bridgeReport.readyCount)/\(runtime.bridgeReport.totalCount)")
                            row("CLI foundation", runtime.stage9.readyForCLIBringUp ? "READY" : "blocked")

                            ForEach(runtime.items) { item in
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack {
                                        Text(item.name)
                                            .font(.caption)
                                        Spacer()
                                        Text(item.ready ? "PASS" : "MISSING")
                                            .font(.caption)
                                            .foregroundStyle(item.ready ? Color.secondary : Color.orange)
                                    }
                                    Text(item.detail)
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                .padding(.vertical, 2)
                            }
                        }

                        Text("This sweep batches Objective-C, CoreFoundation, pthread, filesystem, dispatch, bundle paths, Metal availability and process environment checks in one pass.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage 11 → 14 Integration") {
                        Button("Run Stage 11–14 integration sweep") {
                            stage11To14 = Stage11To14Analyzer.analyze(
                                image: image,
                                fixupPlan: fixupPlan
                            )
                        }

                        if let sweep = stage11To14 {
                            row("Stage 11 Runtime integration", sweep.stage11Ready ? "READY" : "partial")
                            row("Stage 12 Cocoa/AppKit host", sweep.stage12Ready ? "READY" : "partial")
                            row("Stage 13 Metal foundation", sweep.stage13Ready ? "READY" : "partial")
                            row("Stage 14 App bundle bring-up", sweep.stage14Ready ? "READY" : "partial")
                            row("Combined capabilities", "\(sweep.readyCount)/\(sweep.items.count)")

                            ForEach(11...14, id: \.self) { stage in
                                Text("Stage \(stage)")
                                    .font(.caption)
                                    .bold()
                                ForEach(sweep.items.filter { $0.stage == stage }) { item in
                                    VStack(alignment: .leading, spacing: 3) {
                                        HStack {
                                            Text(item.name).font(.caption)
                                            Spacer()
                                            Text(item.ready ? "PASS" : "MISSING")
                                                .font(.caption)
                                                .foregroundStyle(item.ready ? Color.secondary : Color.orange)
                                        }
                                        Text(item.detail)
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }

                        Text("Stages 11–14 are intentionally batched: runtime integration, Cocoa/AppKit host prerequisites, Metal foundation, and macOS app-bundle bring-up prerequisites are evaluated together.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-15 LoL Client Bring-up") {
                        Button("Scan imported client for LoL bring-up") {
                            if let importedData {
                                lolStage15 = LoLStage15Analyzer.analyze(
                                    data: importedData,
                                    image: image,
                                    fixupPlan: fixupPlan
                                )
                            }
                        }

                        if let scan = lolStage15 {
                            row("Client candidate", scan.candidate ? "YES" : "blocked")
                            row("Architecture", scan.architecture)
                            row("File bytes", "\(scan.fileSize)")
                            row("Segments", "\(scan.segmentCount)")
                            row("Dependencies", "\(scan.dependencyCount)")
                            row("RPATHs", "\(scan.rpathCount)")
                            row("Imported symbols", scan.importedSymbolCount.map(String.init) ?? "not analyzed")
                            row("Unresolved imports", scan.unresolvedImports.map(String.init) ?? "not analyzed")
                            row("Static blockers", "\(scan.blockers.count)")

                            ForEach(scan.buckets) { bucket in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("\(bucket.name): \(bucket.count)")
                                        .font(.caption)
                                        .bold()
                                    ForEach(bucket.paths.prefix(12), id: \.self) {
                                        Text($0)
                                            .font(.caption2)
                                            .monospaced()
                                            .foregroundStyle(.secondary)
                                    }
                                    if bucket.paths.count > 12 {
                                        Text("+ \(bucket.paths.count - 12) more")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }

                            if !scan.blockers.isEmpty {
                                Text("Blockers").font(.caption).bold()
                                ForEach(scan.blockers, id: \.self) {
                                    Text("• \($0)").font(.caption)
                                }
                            }

                            if !scan.warnings.isEmpty {
                                Text("Warnings").font(.caption).bold()
                                ForEach(scan.warnings, id: \.self) {
                                    Text("• \($0)")
                                        .font(.caption)
                                        .foregroundStyle(.orange)
                                }
                            }

                            Text("Next compatibility work").font(.caption).bold()
                            ForEach(scan.nextActions, id: \.self) {
                                Text("• \($0)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Text("Import the actual ARM64 League of Legends macOS executable here. Stage 15 performs one large dependency/RPATH/import compatibility scan so subsequent work is driven by the real client rather than synthetic probes.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-15B Real LoL Symbols") {
                        Button("Scan real LoL imported symbols") {
                            do {
                                if let importedData {
                                    classicSymbols = try ClassicSymbolScanner.scan(
                                        data: importedData,
                                        image: image
                                    )
                                    errorText = nil
                                }
                            } catch {
                                classicSymbols = nil
                                errorText = error.localizedDescription
                            }
                        }

                        if let symbols = classicSymbols {
                            row("Symbol source", symbols.source)
                            row("LC_SYMTAB", symbols.symtabPresent ? "present" : "missing")
                            row("LC_DYSYMTAB", symbols.dysymtabPresent ? "present" : "missing")
                            row("Imported symbols", "\(symbols.imported.count)")
                            row("Host resolved", "\(symbols.resolvedCount)")
                            row("Unresolved", "\(symbols.unresolvedCount)")

                            if symbols.unresolvedCount > 0 {
                                Text("Unresolved symbol surface")
                                    .font(.caption)
                                    .bold()
                                ForEach(Array(symbols.imported.filter { !$0.hostResolved }.prefix(80))) { symbol in
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(symbol.name)
                                            .font(.caption2)
                                            .monospaced()
                                        Text(symbol.dependencyPath ?? "ordinal \(symbol.libraryOrdinal)")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                if symbols.unresolvedCount > 80 {
                                    Text("+ \(symbols.unresolvedCount - 80) more unresolved symbols")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }

                        Text("Stage 15B reads the real client's classic Mach-O symbol tables and groups undefined imports against their linked libraries. It is analysis-only and does not execute imported client code.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-15C Deep LoL Metadata Scan") {
                        Button("Run maximum-depth LoL scan") {
                            do {
                                if let importedData {
                                    deepSymbols = try DeepSymbolScanner.scan(
                                        data: importedData,
                                        image: image
                                    )
                                    errorText = nil
                                }
                            } catch {
                                deepSymbols = nil
                                errorText = error.localizedDescription
                            }
                        }

                        if let deep = deepSymbols {
                            row("Metadata sources", "\(deep.sources.count)")
                            row("Classic imports", "\(deep.classicImportCount)")
                            row("Chained imports", "\(deep.chainedImportCount)")
                            row("Dyld bind imports", "\(deep.bindImportCount)")
                            row("Weak-bind imports", "\(deep.weakBindImportCount)")
                            row("Lazy-bind imports", "\(deep.lazyBindImportCount)")
                            row("Exports", "\(deep.exportCount)")
                            row("Merged imports", "\(deep.imports.count)")
                            row("Host resolved", "\(deep.resolvedCount)")
                            row("Unresolved", "\(deep.unresolvedCount)")

                            Text("Detected metadata sources")
                                .font(.caption)
                                .bold()
                            ForEach(deep.sources, id: \.self) {
                                Text("• \($0)")
                                    .font(.caption2)
                                    .monospaced()
                            }

                            if deep.unresolvedCount > 0 {
                                Text("Unresolved compatibility surface")
                                    .font(.caption)
                                    .bold()
                                ForEach(Array(deep.imports.filter { !$0.hostResolved }.prefix(120))) { symbol in
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(symbol.name)
                                            .font(.caption2)
                                            .monospaced()
                                        Text("\(symbol.dependencyPath ?? "ordinal \(symbol.libraryOrdinal)") • \(symbol.source)")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                if deep.unresolvedCount > 120 {
                                    Text("+ \(deep.unresolvedCount - 120) more unresolved symbols")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }

                            ForEach(deep.notes, id: \.self) {
                                Text($0)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Text("Maximum-depth analysis combines classic symbol tables, chained-fixup imports, LC_DYLD_INFO bind/weak/lazy streams and export metadata, then de-duplicates the complete import surface and checks it against the current iOS process.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-16 LoL Compatibility Plan") {
                        Button("Build compatibility plan from real imports") {
                            if let deepSymbols {
                                lolCompatibilityPlan = LoLCompatibilityPlanner.make(from: deepSymbols)
                            }
                        }
                        .disabled(deepSymbols == nil)

                        if let plan = lolCompatibilityPlan {
                            row("Import coverage", "\(plan.coveragePercent)%")
                            row("Resolved", "\(plan.resolvedImports)/\(plan.totalImports)")
                            row("Unresolved", "\(plan.unresolvedImports)")
                            row("Self/weak C++", "\(plan.selfUnresolved)")
                            row("AppKit", "\(plan.appKitUnresolved)")
                            row("ScriptingBridge", "\(plan.scriptingBridgeUnresolved)")
                            row("CoreServices", "\(plan.coreServicesUnresolved)")
                            row("Other", "\(plan.otherUnresolved)")

                            Text("Unresolved by dependency")
                                .font(.caption)
                                .bold()
                            ForEach(plan.buckets.filter { $0.unresolved > 0 }) { bucket in
                                VStack(alignment: .leading, spacing: 3) {
                                    Text("\(bucket.unresolved) — \(bucket.dependency)")
                                        .font(.caption)
                                        .bold()
                                    ForEach(bucket.unresolvedSymbols.prefix(16), id: \.self) {
                                        Text($0)
                                            .font(.caption2)
                                            .monospaced()
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }

                            Text("Compatibility priorities")
                                .font(.caption)
                                .bold()
                            ForEach(plan.priorities, id: \.self) {
                                Text("• \($0)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Text("Stage 16 converts the maximum-depth scan into a concrete compatibility backlog grouped by the real League client dependency surface.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-16B LoL Shim Coverage") {
                        Button("Map all unresolved LoL symbols") {
                            if let lolCompatibilityPlan, let deepSymbols {
                                lolShimCoverage = LoLShimCoverageAnalyzer.analyze(
                                    plan: lolCompatibilityPlan,
                                    scan: deepSymbols
                                )
                            }
                        }
                        .disabled(lolCompatibilityPlan == nil || deepSymbols == nil)

                        if let coverage = lolShimCoverage {
                            row("Mapped unresolved", "\(coverage.coveredCount)/\(coverage.totalCount)")
                            row("Projected import coverage", "\(coverage.projectedCoveragePercent)%")
                            row("Still unclassified", "\(coverage.remaining.count)")

                            ForEach(coverage.items) { item in
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack {
                                        Text(item.symbol)
                                            .font(.caption2)
                                            .monospaced()
                                        Spacer()
                                        Text(item.covered ? "MAPPED" : "TODO")
                                            .font(.caption2)
                                            .foregroundStyle(item.covered ? Color.secondary : Color.orange)
                                    }
                                    Text("\(item.category) → \(item.strategy)")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }

                        Text("This stage maps every currently unresolved League import to a concrete compatibility strategy before runtime shim implementation.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-16C LoL Runtime Shims") {
                        Button("Validate implemented LoL runtime shims") {
                            if let deepSymbols {
                                lolRuntimeValidation = LoLRuntimeShimValidator.validate(scan: deepSymbols)
                            }
                        }
                        .disabled(deepSymbols == nil)

                        if let validation = lolRuntimeValidation {
                            row("Runtime shim symbols", "\(validation.runtimeResolved)")
                            row("Intra-image/weak", "\(validation.weakIntraImage)")
                            row("Covered unresolved", "\(validation.totalCovered)/34")
                            row("Remaining", "\(validation.remaining.count)")
                            row("Runtime shim status", validation.remaining.isEmpty ? "READY" : "partial")

                            if !validation.remaining.isEmpty {
                                Text("Remaining symbols")
                                    .font(.caption)
                                    .bold()
                                ForEach(validation.remaining, id: \.self) {
                                    Text($0)
                                        .font(.caption2)
                                        .monospaced()
                                        .foregroundStyle(.orange)
                                }
                            }
                        }

                        Text("Stage 16C validates actual runtime symbol providers for the LoL-specific AppKit, ScriptingBridge, CoreServices and dyld compatibility surface. Self/weak C++ imports remain assigned to intra-image resolution.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-17 LoL Bring-up") {
                        Button("Run LoL bring-up preflight") {
                            if let importedData {
                                lolStage17Preflight = LoLStage17PreflightAnalyzer.analyze(
                                    data: importedData,
                                    image: image,
                                    deepScan: deepSymbols,
                                    runtimeValidation: lolRuntimeValidation
                                )
                            }
                        }

                        if let stage17 = lolStage17Preflight {
                            row("Bring-up preflight", stage17.ready ? "READY" : "blocked")
                            row("First blocker", stage17.firstBlocker ?? "none")
                            ForEach(stage17.checkpoints) { point in
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack {
                                        Text(point.name).font(.caption)
                                        Spacer()
                                        Text(point.passed ? "PASS" : "BLOCKED")
                                            .font(.caption)
                                            .foregroundStyle(point.passed ? Color.secondary : Color.orange)
                                    }
                                    Text(point.detail)
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }

                        Text("Stage 17 is the final preflight before real League client bring-up. It combines the actual ARM64 client, deep dyld metadata, resolved host imports and implemented LoL runtime shims into one launch-readiness checkpoint.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-17B LoL Launch Dry Run") {
                        Button("Prepare real LoL launch image") {
                            if let importedData {
                                lolLaunchDryRun = LoLLaunchDryRunAnalyzer.analyze(
                                    data: importedData,
                                    image: image,
                                    deepScan: deepSymbols,
                                    runtimeValidation: lolRuntimeValidation
                                )
                            }
                        }

                        if let launch = lolLaunchDryRun {
                            row("Launch image", launch.ready ? "READY" : "blocked")
                            row("Imports resolved", "\(launch.resolvedImports)/\(launch.totalImports)")
                            row("Unresolved", "\(launch.unresolvedImports)")
                            row("Executable segments", "\(launch.executableSegments)")
                            row("Writable segments", "\(launch.writableSegments)")
                            row("Readable segments", "\(launch.readableSegments)")
                            row("LC_MAIN", launch.entryOffset.map { String(format: "0x%llX", $0) } ?? "missing")

                            Text("Launch checkpoints").font(.caption).bold()
                            ForEach(launch.checkpoints, id: \.self) {
                                Text("• \($0)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            if !launch.blockers.isEmpty {
                                Text("Launch blockers").font(.caption).bold()
                                ForEach(launch.blockers, id: \.self) {
                                    Text("• \($0)")
                                        .font(.caption)
                                        .foregroundStyle(.orange)
                                }
                            }
                        }

                        Text("Stage 17B resolves the complete real-client import surface against the host runtime, DarwinBridge LoL shims and intra-image weak symbols, then validates the client segment/entry layout. It prepares the launch image without transferring control to imported executable code.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-17D Runtime Diagnostics") {
                        Button("Capture LoL handoff diagnostics") {
                            if let importedData {
                                lolRuntimeDiagnostics = LoLRuntimeDiagnostics.capture(
                                    data: importedData,
                                    image: image,
                                    deepScan: deepSymbols,
                                    runtimeValidation: lolRuntimeValidation,
                                    launchDryRun: lolLaunchDryRun
                                )
                            }
                        }

                        if let diagnostics = lolRuntimeDiagnostics {
                            row("Execution handoff", diagnostics.readyForHandoff ? "READY" : "blocked")
                            ForEach(diagnostics.checkpoints) { point in
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack {
                                        Text(point.name).font(.caption)
                                        Spacer()
                                        Text(point.passed ? "PASS" : "BLOCKED")
                                            .font(.caption)
                                            .foregroundStyle(point.passed ? Color.secondary : Color.orange)
                                    }
                                    Text(point.detail)
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Text(diagnostics.summary)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Text("Stage 17D records the complete measured state immediately before the external execution handoff: client layout, LC_MAIN, import resolution, LoL shims, Objective-C, Metal and resource availability.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-18 LiveContainer Backend") {
                        Button("Validate LiveContainer execution backend") {
                            liveContainerBackend = LiveContainerExecutionBackend.inspect(
                                image: image,
                                deepScan: deepSymbols,
                                runtimeValidation: lolRuntimeValidation,
                                launchDryRun: lolLaunchDryRun
                            )
                        }

                        if let backend = liveContainerBackend {
                            row("Backend integration", backend.compatible ? "READY" : "blocked")
                            row("dlopen host API", backend.hostDlopenAvailable ? "PASS" : "missing")
                            row("dyld image APIs", backend.dyldImageAPIAvailable ? "PASS" : "missing")
                            row("JIT/debug session", backend.debuggerAttached ? "PASS" : "missing")
                            row("LoL client", backend.clientReady ? "PASS" : "blocked")
                            row("Selected image import surface", backend.importsReady ? "PASS" : "blocked")
                            row("LoL shim surface", backend.shimReady ? "PASS" : "blocked")

                            if !backend.requirements.isEmpty {
                                Text("Backend requirements").font(.caption).bold()
                                ForEach(backend.requirements, id: \.self) {
                                    Text("• \($0)")
                                        .font(.caption)
                                        .foregroundStyle(.orange)
                                }
                            }

                            ForEach(backend.notes, id: \.self) {
                                Text($0)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Text("Stage 18 validates the adapter contract for using LiveContainer as DarwinBridge's native execution backend while retaining DarwinBridge's LoL-specific compatibility analysis and shim layer.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-19 Real Runtime Test") {
                        Button("Arm real LoL runtime observer") {
                            externalHandoff = LoLExternalHandoffObserver.prepare(
                                image: image,
                                deepScan: deepSymbols,
                                runtimeValidation: lolRuntimeValidation,
                                backend: liveContainerBackend
                            )
                        }

                        if let handoff = externalHandoff {
                            row("External handoff", handoff.ready ? "ARMED" : "blocked")
                            row("Expected LC_MAIN", handoff.expectedEntry.map { String(format: "0x%llX", $0) } ?? "missing")
                            row("Imports", "\(handoff.importCount)")
                            row("Runtime shims", "\(handoff.runtimeShimCount)")
                            Text(handoff.note)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        if !runtimeEvents.events.isEmpty {
                            Text("Runtime checkpoints").font(.caption).bold()
                            ForEach(runtimeEvents.events.suffix(40)) { event in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(event.phase)
                                        .font(.caption2)
                                        .bold()
                                        .monospaced()
                                    Text(event.detail)
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }

                        Button("Clear runtime checkpoints") {
                            runtimeEvents.clear()
                        }

                        Text("Stage 19 arms DarwinBridge to observe and record the first real runtime events from the external LiveContainer execution handoff. It does not itself transfer control to imported executable code.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Section("Stage-20B First Real Launch Package") {
                        Button("Build diagnostic IPA for LiveContainer") {
                            do {
                                guard let importedData else { return }
                                let package = try LoLLiveContainerPackager.buildMinimalIPA(
                                    executable: importedData,
                                    sourceFileName: fileName
                                )
                                firstRunPackageURL = package.ipaURL
                                firstRunPackageSummary = "Patched \(package.patchedDependencies.count) desktop dependency path(s); executable \(package.executableBytes) bytes; plugin \(package.pluginBytes) bytes."
                                errorText = nil
                            } catch {
                                firstRunPackageURL = nil
                                firstRunPackageSummary = nil
                                errorText = error.localizedDescription
                            }
                        }
                        .disabled(liveContainerBackend?.compatible != true ||
                                  lolLaunchDryRun?.ready != true)

                        if let firstRunPackageSummary {
                            Text(firstRunPackageSummary)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        if let url = firstRunPackageURL {
                            ShareLink(item: url) {
                                Label("Export DarwinBridge-LoL-first-run.ipa", systemImage: "square.and.arrow.up")
                            }
                            row("Package", "DIAGNOSTIC ONLY")
                        }

                        Text("This packages an imported executable for loader diagnostics and records its original identity. It does not include a complete Riot client bundle. Riot installers and missing payload dependencies block packaging.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    if let fixupPlan, !fixupPlan.resolutions.isEmpty {
                        Section("Symbol broker") {
                            ForEach(fixupPlan.resolutions) { symbol in
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(symbol.name)
                                        .font(.caption)
                                        .monospaced()
                                    Text(symbol.dependencyPath ?? "unknown library")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                    Text(symbol.address.map { String(format: "0x%llX", $0) } ?? "unresolved")
                                        .font(.caption2)
                                        .monospaced()
                                }
                            }
                        }
                    }
                }

                if let report {
                    Section("Compatibility") {
                        row("Score", "\(report.score)/100")
                        row("Execution candidate", report.executableCandidate ? "yes" : "no")
                    }

                    if !report.blockers.isEmpty {
                        Section("Blockers") {
                            ForEach(report.blockers, id: \.self) {
                                Text($0)
                            }
                        }
                    }

                    if !report.warnings.isEmpty {
                        Section("Warnings") {
                            ForEach(report.warnings, id: \.self) {
                                Text($0)
                            }
                        }
                    }

                    Section("Framework map") {
                        ForEach(report.assessments) { item in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.dependency.path)
                                    .font(.caption)
                                    .monospaced()
                                Text(item.disposition.rawValue)
                                    .font(.subheadline)
                                    .bold()
                                if let replacement = item.replacement {
                                    Text("→ \(replacement)")
                                        .font(.caption)
                                }
                                Text(item.note)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }

                if let errorText {
                    Section("Error") {
                        Text(errorText)
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("DarwinBridge")
            .onAppear { guestApps = DBGuestLibrary.installed() }
            .fileImporter(isPresented: $importingApp, allowedContentTypes: [.item], allowsMultipleSelection: false) { result in
                do {
                    guard let url = try result.get().first else { return }
                    _ = try DBGuestLibrary.install(bundle: url)
                    guestApps = DBGuestLibrary.installed()
                    guestError = nil
                } catch { guestError = error.localizedDescription }
            }
            .fileImporter(isPresented: $importing,
                          allowedContentTypes: [.item],
                          allowsMultipleSelection: false) { result in
                do {
                    guard let url = try result.get().first else { return }
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer {
                        if scoped { url.stopAccessingSecurityScopedResource() }
                    }

                    let sourceData = try Data(contentsOf: url, options: .mappedIfSafe)
                    let data = try MachOParser.preferredArm64Slice(sourceData)
                    let parsed = try MachOParser.parse(data)

                    fileName = url.lastPathComponent
                    image = parsed
                    report = CompatibilityAnalyzer.analyze(parsed)
                    importedData = data
                    errorText = nil
                    mappedSummary = nil
                    mappedImage = nil
                    fixupPlan = nil
                    guestSpace = nil
                    appliedFixups = nil

                    // Fast path for the real LoL client: all analysis stages
                    // required by the first-run packager are deterministic, so
                    // run them automatically after import and immediately emit
                    // a fresh LiveContainer IPA. This avoids repeating Stages
                    // 15C -> 20B for every compatibility iteration.
                    do {
                        let deep = try DeepSymbolScanner.scan(data: data, image: parsed)
                        deepSymbols = deep

                        let plan = LoLCompatibilityPlanner.make(from: deep)
                        lolCompatibilityPlan = plan
                        lolShimCoverage = LoLShimCoverageAnalyzer.analyze(plan: plan, scan: deep)

                        let runtime = LoLRuntimeShimValidator.validate(scan: deep)
                        lolRuntimeValidation = runtime

                        let dryRun = LoLLaunchDryRunAnalyzer.analyze(
                            data: data,
                            image: parsed,
                            deepScan: deep,
                            runtimeValidation: runtime
                        )
                        lolLaunchDryRun = dryRun

                        let backend = LiveContainerExecutionBackend.inspect(
                            image: parsed,
                            deepScan: deep,
                            runtimeValidation: runtime,
                            launchDryRun: dryRun
                        )
                        liveContainerBackend = backend

                        guard dryRun.ready else {
                            throw LoLLiveContainerPackageError.malformed("automatic launch dry-run is not READY")
                        }
                        guard backend.compatible else {
                            throw LoLLiveContainerPackageError.malformed(
                                "LiveContainer backend is not READY: " +
                                backend.requirements.joined(separator: ", ")
                            )
                        }

                        let package = try LoLLiveContainerPackager.buildMinimalIPA(
                            executable: data,
                            sourceFileName: url.lastPathComponent
                        )
                        firstRunPackageURL = package.ipaURL
                        firstRunPackageSummary =
                            "PACKAGE CREATED (execution unverified) — patched \(package.patchedDependencies.count) dependency path(s); " +
                            "imports \(deep.imports.count); unresolved launch imports \(dryRun.unresolvedImports)."
                    } catch {
                        firstRunPackageURL = nil
                        firstRunPackageSummary = nil
                        errorText = "Automatic LoL first-run build failed: " + error.localizedDescription
                    }
                } catch {
                    errorText = error.localizedDescription
                    image = nil
                    report = nil
                    importedData = nil
                    mappedImage = nil
                    fixupPlan = nil
                    guestSpace = nil
                    appliedFixups = nil
                }
            }
        }
    }

    @ViewBuilder
    private func row(_ name: String, _ value: String) -> some View {
        HStack {
            Text(name)
            Spacer()
            Text(value)
                .foregroundStyle(.secondary)
                .monospaced()
        }
    }

    private func map(_ image: MachOImageInfo) {
        do {
            guard let importedData else { return }
            let mapped = try MachOLoader.mapForInspection(data: importedData, image: image)
            mappedImage = mapped
            mappedSummary = "mapped \(mapped.regions.count) regions; entry=\(mapped.entryAddress.map { String(describing: $0) } ?? "unresolved")"
            errorText = nil
        } catch {
            mappedImage = nil
            errorText = error.localizedDescription
        }
    }

    private func analyzeFixups(_ image: MachOImageInfo) {
        do {
            guard let importedData else { return }
            fixupPlan = try FixupPlanner.make(data: importedData, image: image)
            guestSpace = nil
            appliedFixups = nil
            errorText = nil
        } catch {
            fixupPlan = nil
            guestSpace = nil
            appliedFixups = nil
            errorText = error.localizedDescription
        }
    }

    private func loadGuest(_ image: MachOImageInfo) {
        do {
            guard let importedData, let fixupPlan else { return }
            let space = try GuestAddressSpace(data: importedData, image: image)
            let applied = try FixupApplier.apply(plan: fixupPlan, to: space)
            guestSpace = space
            appliedFixups = applied
            errorText = nil
        } catch {
            guestSpace = nil
            appliedFixups = nil
            errorText = error.localizedDescription
        }
    }
}
