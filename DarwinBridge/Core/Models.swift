import Foundation

struct MachOSegment: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let vmAddress: UInt64
    let vmSize: UInt64
    let fileOffset: UInt64
    let fileSize: UInt64
    let maxProtection: Int32
    let initialProtection: Int32
}

struct MachODependency: Identifiable, Hashable {
    let id = UUID()
    let path: String
    let weak: Bool
    let reexported: Bool
}

struct LinkeditDataLocation: Hashable {
    let fileOffset: UInt32
    let size: UInt32
}

struct MachOImageInfo {
    let cpuType: Int32
    let cpuSubtype: Int32
    let fileType: UInt32
    let commands: UInt32
    let flags: UInt32
    let entryOffset: UInt64?
    let platform: UInt32?
    let minimumOS: String?
    let sdk: String?
    let encrypted: Bool
    let segments: [MachOSegment]
    let dependencies: [MachODependency]
    let rpaths: [String]
    let chainedFixups: LinkeditDataLocation?

    var isArm64: Bool { cpuType == 0x0100000C }

    var preferredImageBase: UInt64? {
        segments
            .filter { $0.name != "__PAGEZERO" && $0.vmSize > 0 }
            .map(\.vmAddress)
            .min()
    }
}

enum ShimDisposition: String {
    case native = "Native iOS framework"
    case shim = "Compatibility shim required"
    case partial = "Partial / risky"
    case blocked = "Currently blocked"
    case unknown = "Unknown"
}

struct DependencyAssessment: Identifiable {
    let id = UUID()
    let dependency: MachODependency
    let disposition: ShimDisposition
    let replacement: String?
    let note: String
}

struct CompatibilityReport {
    let score: Int
    let executableCandidate: Bool
    let blockers: [String]
    let warnings: [String]
    let assessments: [DependencyAssessment]
}

enum ChainedImportFormat: UInt32 {
    case basic = 1
    case addend = 2
    case addend64 = 3
}

struct ChainedImport: Identifiable, Hashable {
    let id = UUID()
    let libraryOrdinal: Int32
    let weak: Bool
    let name: String
    let addend: Int64
}

enum ChainedPointerFormat: UInt16, CustomStringConvertible {
    case arm64e = 1
    case ptr64 = 2
    case ptr32 = 3
    case ptr32Cache = 4
    case ptr32Firmware = 5
    case ptr64Offset = 6
    case arm64eKernel = 7
    case ptr64KernelCache = 8
    case arm64eUserland = 9
    case arm64eFirmware = 10
    case x86_64KernelCache = 11
    case arm64eUserland24 = 12

    var description: String {
        switch self {
        case .arm64e: return "ARM64E"
        case .ptr64: return "PTR_64"
        case .ptr32: return "PTR_32"
        case .ptr32Cache: return "PTR_32_CACHE"
        case .ptr32Firmware: return "PTR_32_FIRMWARE"
        case .ptr64Offset: return "PTR_64_OFFSET"
        case .arm64eKernel: return "ARM64E_KERNEL"
        case .ptr64KernelCache: return "PTR_64_KERNEL_CACHE"
        case .arm64eUserland: return "ARM64E_USERLAND"
        case .arm64eFirmware: return "ARM64E_FIRMWARE"
        case .x86_64KernelCache: return "X86_64_KERNEL_CACHE"
        case .arm64eUserland24: return "ARM64E_USERLAND24"
        }
    }

    var isStage1Supported: Bool {
        self == .ptr64 || self == .ptr64Offset
    }
}

enum FixupAction: Hashable {
    case rebase(target: UInt64, targetIsImageOffset: Bool)
    case bind(importIndex: Int, addend: Int64)
}

struct ChainedFixup: Identifiable, Hashable {
    let id = UUID()
    let segmentIndex: Int
    let segmentName: String
    let fileOffset: UInt64
    let guestVMAddress: UInt64
    let pointerFormat: ChainedPointerFormat
    let rawValue: UInt64
    let action: FixupAction
}

struct ChainedFixupsInfo {
    let version: UInt32
    let importsFormat: ChainedImportFormat
    let imports: [ChainedImport]
    let fixups: [ChainedFixup]
    let unsupportedPointerFormats: Set<UInt16>
}

struct SymbolResolution: Identifiable {
    let id = UUID()
    let name: String
    let libraryOrdinal: Int32
    let dependencyPath: String?
    let address: UInt64?
    let source: String
}
