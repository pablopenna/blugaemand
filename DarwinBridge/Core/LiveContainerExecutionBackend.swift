import Foundation
import Darwin

struct LiveContainerBackendReport {
    let compatible: Bool
    let hostDlopenAvailable: Bool
    let dyldImageAPIAvailable: Bool
    let debuggerAttached: Bool
    let clientReady: Bool
    let importsReady: Bool
    let shimReady: Bool
    let requirements: [String]
    let notes: [String]
}

struct LiveContainerExecutionBackend {
    static func inspect(image: MachOImageInfo,
                        deepScan: DeepSymbolScanReport?,
                        runtimeValidation: LoLRuntimeShimValidation?,
                        launchDryRun: LoLLaunchDryRunReport?) -> LiveContainerBackendReport {
        let dlopenAvailable = RuntimeCompatibility.address(of: "dlopen") != nil
        let dyldCount = RuntimeCompatibility.address(of: "_dyld_image_count") != nil
        let dyldHeader = RuntimeCompatibility.address(of: "_dyld_get_image_header") != nil
        let dyldSlide = RuntimeCompatibility.address(of: "_dyld_get_image_vmaddr_slide") != nil
        let dyldReady = dyldCount && dyldHeader && dyldSlide
        let debugger = debuggerAttached()

        let clientReady = image.isArm64 && !image.encrypted && image.entryOffset != nil
        let importsReady = deepScan?.imports.isEmpty == false &&
                           launchDryRun?.unresolvedImports == 0
        let shimReady = runtimeValidation?.remaining.isEmpty == true

        var requirements: [String] = []
        if !dlopenAvailable { requirements.append("dlopen host API") }
        if !dyldReady { requirements.append("dyld image APIs") }
        if !debugger { requirements.append("LiveContainer/StikDebug JIT session") }
        if !clientReady { requirements.append("Stage 17 ARM64 client readiness") }
        if !importsReady { requirements.append("launch import resolution for the selected image") }
        if !shimReady { requirements.append("Stage 16C LoL shim readiness") }

        var notes: [String] = []
        notes.append("This is a static capability report, not a trace of LiveContainer or the guest executable.")
        notes.append("Backend contract: DarwinBridge owns macOS compatibility analysis and LoL shims; LiveContainer supplies the native guest-loading/execution environment.")
        notes.append("Integration intentionally does not duplicate LiveContainer's executable patching or entry-transfer implementation.")
        notes.append("The adapter expects a LiveContainer-capable host with dyld loading support and an active JIT/debug session on iPadOS 27.")

        return LiveContainerBackendReport(
            compatible: requirements.isEmpty,
            hostDlopenAvailable: dlopenAvailable,
            dyldImageAPIAvailable: dyldReady,
            debuggerAttached: debugger,
            clientReady: clientReady,
            importsReady: importsReady,
            shimReady: shimReady,
            requirements: requirements,
            notes: notes
        )
    }

    private static func debuggerAttached() -> Bool {
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        var mib = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        let result = mib.withUnsafeMutableBufferPointer {
            sysctl($0.baseAddress, 4, &info, &size, nil, 0)
        }
        return result == 0 && (info.kp_proc.p_flag & P_TRACED) != 0
    }
}
