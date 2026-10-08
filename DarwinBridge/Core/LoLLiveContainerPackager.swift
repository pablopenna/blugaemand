import Foundation
import CryptoKit

enum LoLLiveContainerPackageError: Error, LocalizedError {
    case malformed(String)
    case pluginMissing
    case unsupportedDependency(String)
    case missingPayloadDependency(String)
    case installerIsNotClient

    var errorDescription: String? {
        switch self {
        case .malformed(let message): return "LoL package error: \(message)"
        case .pluginMissing: return "DarwinBridgeLCPlugin.dylib is not embedded in this DarwinBridge build."
        case .installerIsNotClient: return "RiotClientServices installer/bootstrap detected. A standalone installer executable cannot be packaged as League of Legends. Its system.yaml and resources are missing from an executable-only import; a complete client bundle and dependency graph are required."
        case .missingPayloadDependency(let path): return "Minimal package cannot include this required payload dependency: \(path). Importing one executable is not a complete app bundle."
        case .unsupportedDependency(let path): return "Dependency path is too short to redirect safely: \(path)"
        }
    }
}

struct LoLLiveContainerPackageResult {
    let ipaURL: URL
    let patchedDependencies: [String]
    let executableBytes: Int
    let pluginBytes: Int
}

struct LoLLiveContainerPackager {
    private static let lcLoadDylib: UInt32 = 0x0C
    private static let lcIdDylib: UInt32 = 0x0D
    private static let lcLoadWeakDylib: UInt32 = 0x80000018
    private static let lcReexportDylib: UInt32 = 0x8000001F
    private static let lcLoadUpwardDylib: UInt32 = 0x80000023
    private static let lcBuildVersion: UInt32 = 0x32
    private static let lcVersionMinMacOSX: UInt32 = 0x24
    private static let lcVersionMinIPhoneOS: UInt32 = 0x25

    static func buildMinimalIPA(executable source: Data,
                                displayName: String = "DarwinBridge Guest Probe",
                                sourceFileName: String? = nil,
                                runtimeDirectory: URL? = nil,
                                outputURL: URL? = nil) throws -> LoLLiveContainerPackageResult {
        var executable = try MachOParser.preferredArm64Slice(source)
        let image = try MachOParser.parse(executable)
        guard image.isArm64, image.fileType == 2, !image.encrypted else {
            throw LoLLiveContainerPackageError.malformed("expected an unencrypted ARM64 executable")
        }
        let sourceHash = SHA256.hash(data: executable).map { String(format: "%02x", $0) }.joined()
        // Check the bytes, not the user-controlled filename. The official Mac
        // download is RiotClientServices, even when renamed LeagueOfLegends.
        let knownInstaller = sourceHash == "199f80081c87b9dd0f3a996ea85dbbdedd0680d14dc2f003f90d1ef9f29da782"
        let installerMarkers = ["Running Dev Feature 2 Installer.",
                                "'publisher' not found in system-settings",
                                "Loaded system.yaml from embedded resource in executable."]
        if knownInstaller || installerMarkers.allSatisfy({ executable.range(of: Data(($0 + "\0").utf8)) != nil }) {
            throw LoLLiveContainerPackageError.installerIsNotClient
        }
        var sourceUUIDs: [String] = []
        var commandOffset = 32
        for _ in 0..<image.commands {
            let command = try u32(executable, commandOffset)
            let size = Int(try u32(executable, commandOffset + 4))
            if command == 0x1B, size >= 24 {
                let h = executable[commandOffset+8..<commandOffset+24].map { String(format: "%02x", $0) }
                sourceUUIDs.append([h[0..<4], h[4..<6], h[6..<8], h[8..<10], h[10..<16]].map { $0.joined() }.joined(separator: "-"))
            }
            commandOffset += size
        }
        let identity: [String: Any] = [
            "schema": "darwinbridge-package-identity-v1",
            "source_file_name": sourceFileName.map { URL(fileURLWithPath: $0).lastPathComponent } ?? "unknown",
            "source_arm64_sha256": sourceHash,
            "source_arm64_bytes": executable.count,
            "source_uuid": sourceUUIDs,
            "source_lc_main": image.entryOffset.map { String(format: "0x%llx", $0) } ?? "missing",
            "source_dependencies": image.dependencies.map(\.path),
            "role": "unclassified-executable",
            "scope": "executable-only diagnostic; complete Riot payload and device execution unverified"
        ]
        try removeCodeSignatureCommand(&executable)
        let redirects = try patchForLiveContainer(&executable)
        try injectBootstrapLoadCommand(&executable,
                                       path: "@executable_path/Frameworks/DBBootstrap.dylib")

        let runtime = runtimeDirectory ?? Bundle.main.bundleURL.appendingPathComponent("DarwinBridgeRuntime")
        let pluginURL = runtime.appendingPathComponent("DarwinBridgeLCPlugin.dylib")
        guard FileManager.default.fileExists(atPath: pluginURL.path) else {
            throw LoLLiveContainerPackageError.pluginMissing
        }
        let plugin = try Data(contentsOf: pluginURL)

        let executableName = "LeagueOfLegends"
        let info = makeInfoPlist(executableName: executableName, displayName: displayName)

        var zip = StoreZipWriter()
        try zip.add(path: "Payload/LeagueOfLegends.app/Info.plist", data: info)
        try zip.add(path: "Payload/LeagueOfLegends.app/DarwinBridge-package.json",
                    data: JSONSerialization.data(withJSONObject: identity, options: [.prettyPrinted, .sortedKeys]))
        try zip.add(path: "Payload/LeagueOfLegends.app/\(executableName)", data: executable)
        // Copy pre-linked, pre-signed forwarding dylibs byte for byte. Cloning
        // the implementation duplicated every ObjC class and invalidated signatures.
        var files = Set(redirects.compactMap(\.fileName))
        files.insert("DBBootstrap.dylib")
        files.insert("DarwinBridgeLCPlugin.dylib")
        for name in files.sorted() {
            let bytes = try Data(contentsOf: runtime.appendingPathComponent(name))
            try zip.add(path: "Payload/LeagueOfLegends.app/Frameworks/\(name)", data: bytes)
        }

        let out = outputURL ?? FileManager.default.temporaryDirectory
            .appendingPathComponent("DarwinBridge-LoL-first-run.ipa")
        try zip.finalize().write(to: out, options: .atomic)

        return LoLLiveContainerPackageResult(ipaURL: out,
                                             patchedDependencies: redirects.map(\.originalPath),
                                             executableBytes: executable.count,
                                             pluginBytes: plugin.count)
    }

    private struct DependencyRedirect {
        let originalPath: String
        let replacementPath: String
        let fileName: String?
        let installName: String?
    }

    private static func patchForLiveContainer(_ data: inout Data) throws -> [DependencyRedirect] {
        guard data.count >= 32, try u32(data, 0) == MachOParser.mhMagic64 else {
            throw LoLLiveContainerPackageError.malformed("not a thin 64-bit Mach-O")
        }

        let ncmds = Int(try u32(data, 16))
        var cursor = 32
        var redirects: [DependencyRedirect] = []

        for _ in 0..<ncmds {
            guard cursor + 8 <= data.count else {
                throw LoLLiveContainerPackageError.malformed("truncated load commands")
            }

            let cmd = try u32(data, cursor)
            let cmdsize = Int(try u32(data, cursor + 4))
            guard cmdsize >= 8, cursor + cmdsize <= data.count else {
                throw LoLLiveContainerPackageError.malformed("invalid load command")
            }

            if cmd == lcBuildVersion {
                guard cmdsize >= 24 else {
                    throw LoLLiveContainerPackageError.malformed("short LC_BUILD_VERSION")
                }
                put32(&data, cursor + 8, 2) // PLATFORM_IOS
                put32(&data, cursor + 12, version(17, 0, 0))
                put32(&data, cursor + 16, version(17, 0, 0))
            } else if cmd == lcVersionMinMacOSX {
                // version_min_command has the same 16-byte layout on iOS.
                put32(&data, cursor, lcVersionMinIPhoneOS)
                if cmdsize >= 16 {
                    put32(&data, cursor + 8, version(17, 0, 0))
                    put32(&data, cursor + 12, version(17, 0, 0))
                }
            } else if cmd == lcLoadDylib || cmd == lcLoadWeakDylib ||
                        cmd == lcReexportDylib || cmd == lcLoadUpwardDylib {
                guard cmdsize >= 24 else {
                    throw LoLLiveContainerPackageError.malformed("short dylib command")
                }
                let nameOffset = Int(try u32(data, cursor + 8))
                guard nameOffset >= 8, nameOffset < cmdsize else {
                    throw LoLLiveContainerPackageError.malformed("bad dylib name offset")
                }
                let start = cursor + nameOffset
                let end = cursor + cmdsize
                let current = cString(data, start, end)

                if let redirect = dependencyRedirect(for: current) {
                    let replacement = redirect.replacementPath
                    let capacity = end - start
                    guard replacement.utf8.count + 1 <= capacity else {
                        throw LoLLiveContainerPackageError.unsupportedDependency(current)
                    }
                    for i in start..<end { data[i] = 0 }
                    for (index, byte) in replacement.utf8.enumerated() {
                        data[start + index] = byte
                    }
                    if redirect.fileName != nil {
                        // A redirected dependency requests DarwinBridge ABI 1,
                        // not the original desktop framework's version number.
                        put32(&data, cursor + 16, version(1, 0, 0))
                        put32(&data, cursor + 20, version(1, 0, 0))
                    }
                    redirects.append(redirect)
                } else if current.hasPrefix("@") || current.contains("/Versions/") {
                    // An executable-only package must not pretend to include the
                    // Riot framework/resource graph or desktop-only frameworks.
                    throw LoLLiveContainerPackageError.missingPayloadDependency(current)
                }
            }

            cursor += cmdsize
        }

        return redirects
    }

    private static func injectBootstrapLoadCommand(_ data: inout Data,
                                                   path: String) throws {
        // Add a real LC_LOAD_DYLIB without moving any existing segment bytes.
        // Mach-O executables normally have padding between the load-command table
        // and the first section. We consume only verified zero padding.
        guard data.count >= 32, try u32(data, 0) == MachOParser.mhMagic64 else {
            throw LoLLiveContainerPackageError.malformed("bootstrap injection requires thin Mach-O 64")
        }

        let ncmds = Int(try u32(data, 16))
        let sizeofcmds = Int(try u32(data, 20))
        let commandsEnd = 32 + sizeofcmds
        guard commandsEnd <= data.count else {
            throw LoLLiveContainerPackageError.malformed("load command table exceeds file during bootstrap injection")
        }

        var cursor = 32
        var firstFileData = data.count
        for _ in 0..<ncmds {
            guard cursor + 8 <= commandsEnd else {
                throw LoLLiveContainerPackageError.malformed("truncated load command during bootstrap injection")
            }
            let cmd = try u32(data, cursor)
            let cmdsize = Int(try u32(data, cursor + 4))
            guard cmdsize >= 8, cmdsize % 8 == 0, cursor + cmdsize <= commandsEnd else {
                throw LoLLiveContainerPackageError.malformed("invalid load command during bootstrap injection")
            }
            if cmd == 0x19, cmdsize >= 72 { // LC_SEGMENT_64
                let fileoff = try u64(data, cursor + 40)
                let filesize = try u64(data, cursor + 48)
                if filesize > 0, fileoff > 0, fileoff <= UInt64(Int.max) {
                    firstFileData = min(firstFileData, Int(fileoff))
                }
                let nsects = Int(try u32(data, cursor + 64))
                var section = cursor + 72
                for _ in 0..<nsects {
                    guard section + 80 <= cursor + cmdsize else {
                        throw LoLLiveContainerPackageError.malformed("section table exceeds command")
                    }
                    let offset = Int(try u32(data, section + 48))
                    let size = try u64(data, section + 40)
                    if size > 0, offset > 0 {
                        firstFileData = min(firstFileData, offset)
                    }
                    section += 80
                }
            }
            if [UInt32(0x1D), 0x1E, 0x26, 0x29, 0x2B, 0x2E, 0x80000033, 0x80000034].contains(cmd), cmdsize >= 16 {
                let off = Int(try u32(data, cursor + 8)), size = Int(try u32(data, cursor + 12))
                if size > 0 { firstFileData = min(firstFileData, off) }
            } else if cmd == 0x22 || cmd == 0x80000022 {
                guard cmdsize >= 48 else { throw LoLLiveContainerPackageError.malformed("short dyld info") }
                for pair in stride(from: 8, to: 48, by: 8) {
                    if try u32(data, cursor + pair + 4) > 0 { firstFileData = min(firstFileData, Int(try u32(data, cursor + pair))) }
                }
            } else if cmd == 0x2, cmdsize >= 24 {
                if try u32(data, cursor + 12) > 0 { firstFileData = min(firstFileData, Int(try u32(data, cursor + 8))) }
                if try u32(data, cursor + 20) > 0 { firstFileData = min(firstFileData, Int(try u32(data, cursor + 16))) }
            }
            cursor += cmdsize
        }
        guard cursor == commandsEnd else { throw LoLLiveContainerPackageError.malformed("command size mismatch") }

        let nameBytes = Array(path.utf8) + [0]
        let rawSize = 24 + nameBytes.count
        let cmdsize = (rawSize + 7) & ~7
        guard commandsEnd + cmdsize <= firstFileData,
              commandsEnd + cmdsize <= data.count else {
            throw LoLLiveContainerPackageError.malformed(
                "not enough Mach-O header padding for forced bootstrap LC_LOAD_DYLIB"
            )
        }

        // Never overwrite meaningful bytes in header padding.
        guard data[commandsEnd..<(commandsEnd + cmdsize)].allSatisfy({ $0 == 0 }) else {
            throw LoLLiveContainerPackageError.malformed(
                "bootstrap load-command padding is not empty"
            )
        }

        put32(&data, commandsEnd, lcLoadDylib)
        put32(&data, commandsEnd + 4, UInt32(cmdsize))
        put32(&data, commandsEnd + 8, 24) // dylib.name offset
        put32(&data, commandsEnd + 12, 0) // timestamp
        put32(&data, commandsEnd + 16, 0) // current_version
        put32(&data, commandsEnd + 20, 0) // compatibility_version
        for (index, byte) in nameBytes.enumerated() {
            data[commandsEnd + 24 + index] = byte
        }
        for index in (commandsEnd + rawSize)..<(commandsEnd + cmdsize) {
            data[index] = 0
        }

        put32(&data, 16, UInt32(ncmds + 1))
        put32(&data, 20, UInt32(sizeofcmds + cmdsize))

        // Verify our own mutation before producing the IPA.
        var verifyCursor = 32
        var found = false
        for _ in 0..<(ncmds + 1) {
            let cmd = try u32(data, verifyCursor)
            let size = Int(try u32(data, verifyCursor + 4))
            guard size >= 8, verifyCursor + size <= 32 + sizeofcmds + cmdsize else {
                throw LoLLiveContainerPackageError.malformed("bootstrap verification found invalid load command")
            }
            if cmd == lcLoadDylib, size >= 24 {
                let off = Int(try u32(data, verifyCursor + 8))
                if off >= 8, off < size,
                   cString(data, verifyCursor + off, verifyCursor + size) == path {
                    found = true
                }
            }
            verifyCursor += size
        }
        guard found else {
            throw LoLLiveContainerPackageError.malformed("forced bootstrap LC_LOAD_DYLIB verification failed")
        }
    }

    private static func removeCodeSignatureCommand(_ data: inout Data) throws {
        guard data.count >= 32 else { throw LoLLiveContainerPackageError.malformed("short header") }
        let count = Int(try u32(data, 16))
        let length = Int(try u32(data, 20))
        guard 32 + length <= data.count else { throw LoLLiveContainerPackageError.malformed("commands past EOF") }
        var commands = Data()
        var cursor = 32
        var kept: UInt32 = 0
        for _ in 0..<count {
            let size = Int(try u32(data, cursor + 4))
            guard size >= 8, size % 8 == 0, cursor + size <= 32 + length else {
                throw LoLLiveContainerPackageError.malformed("invalid signing load command")
            }
            if try u32(data, cursor) != 0x1D {
                commands.append(data[cursor..<cursor + size]); kept += 1
            }
            cursor += size
        }
        guard cursor == 32 + length else { throw LoLLiveContainerPackageError.malformed("command count/size mismatch") }
        data.replaceSubrange(32..<32 + length, with: commands + Data(repeating: 0, count: length - commands.count))
        put32(&data, 16, kept)
        put32(&data, 20, UInt32(commands.count))
        // Preserve every file/section offset. The detached old signature bytes
        // in LINKEDIT are inert; the destination signer rebuilds the signature.
    }

    private static func dependencyRedirect(for path: String) -> DependencyRedirect? {
        let p = path.lowercased()

        func shim(_ fileName: String) -> DependencyRedirect {
            DependencyRedirect(
                originalPath: path,
                replacementPath: "@loader_path/Frameworks/\(fileName)",
                fileName: fileName,
                installName: "@rpath/\(fileName)"
            )
        }

        func native(_ framework: String) -> DependencyRedirect {
            DependencyRedirect(
                originalPath: path,
                replacementPath: "/System/Library/Frameworks/\(framework).framework/\(framework)",
                fileName: nil,
                installName: nil
            )
        }

        // Desktop-only frameworks still route to DarwinBridge shims.
        if p.contains("appkit.framework") { return shim("DBAppKit.dylib") }
        if p.contains("coreservices.framework") { return shim("DBCoreServices.dylib") }
        if p.contains("cocoa.framework") { return shim("DBCocoa.dylib") }
        if p.contains("scriptingbridge.framework") { return shim("DBScriptingBridge.dylib") }
        if p.contains("diskarbitration.framework") { return shim("DBDiskArbitration.dylib") }
        if p.contains("iokit.framework") { return shim("DBIOKit.dylib") }

        // The macOS client can import desktop-only Objective-C classes from
        // otherwise shared frameworks (for example NSAppleEventManager from
        // Foundation). Route those ordinals through distinct compatibility
        // aliases. DarwinBridgeLCPlugin re-exports the corresponding native iOS
        // frameworks, so ordinary symbols flow through while desktop-only
        // additions are supplied by the shim.
        if p.contains("avfoundation.framework") { return shim("DBAVFoundation.dylib") }
        if p.contains("cfnetwork.framework") { return shim("DBCFNetwork.dylib") }
        if p.contains("corefoundation.framework") { return shim("DBCoreFoundation.dylib") }
        if p.contains("coregraphics.framework") { return shim("DBCoreGraphics.dylib") }
        if p.contains("coretext.framework") { return shim("DBCoreText.dylib") }
        if p.contains("foundation.framework") { return shim("DBFoundation.dylib") }
        if p.contains("security.framework") { return shim("DBSecurity.dylib") }
        if p.contains("systemconfiguration.framework") { return shim("DBSystemConfiguration.dylib") }

        for framework in ["WebKit", "AudioUnit", "CoreAudio", "CoreVideo", "Metal", "QuartzCore", "AudioToolbox"] {
            if p.contains("/" + framework.lowercased() + ".framework/") { return native(framework) }
        }
        return nil
    }

    private static func makeInfoPlist(executableName: String, displayName: String) -> Data {
        let object: [String: Any] = [
            "CFBundleDevelopmentRegion": "en",
            "CFBundleDisplayName": displayName,
            "CFBundleExecutable": executableName,
            "CFBundleIdentifier": "com.darwinbridge.lol.firstrun",
            "CFBundleInfoDictionaryVersion": "6.0",
            "CFBundleName": displayName,
            "CFBundlePackageType": "APPL",
            "CFBundleShortVersionString": "1.0",
            "CFBundleVersion": "21",
            "DBRuntimeStage": "21G-abi",
            "DBRequiresLiveContainerResign": true,
            "LSRequiresIPhoneOS": true,
            "UIFileSharingEnabled": true,
            "LSSupportsOpeningDocumentsInPlace": true,
            "MinimumOSVersion": "17.0",
            "UIDeviceFamily": [1, 2],
            "UIRequiredDeviceCapabilities": ["arm64"],
            "UISupportedInterfaceOrientations": [
                "UIInterfaceOrientationLandscapeLeft",
                "UIInterfaceOrientationLandscapeRight",
                "UIInterfaceOrientationPortrait"
            ],
            "UILaunchScreen": [:]
        ]
        return try! PropertyListSerialization.data(fromPropertyList: object,
                                                   format: .xml,
                                                   options: 0)
    }

    private static func version(_ major: UInt32, _ minor: UInt32, _ patch: UInt32) -> UInt32 {
        (major << 16) | (minor << 8) | patch
    }

    private static func u32(_ data: Data, _ offset: Int) throws -> UInt32 {
        guard offset + 4 <= data.count else {
            throw LoLLiveContainerPackageError.malformed("read past EOF")
        }
        return data[offset..<offset+4].enumerated().reduce(UInt32(0)) {
            $0 | (UInt32($1.element) << UInt32($1.offset * 8))
        }
    }

    private static func u64(_ data: Data, _ offset: Int) throws -> UInt64 {
        guard offset + 8 <= data.count else {
            throw LoLLiveContainerPackageError.malformed("read past EOF")
        }
        return data[offset..<offset+8].enumerated().reduce(UInt64(0)) {
            $0 | (UInt64($1.element) << UInt64($1.offset * 8))
        }
    }

    private static func put32(_ data: inout Data, _ offset: Int, _ value: UInt32) {
        for i in 0..<4 {
            data[offset + i] = UInt8((value >> UInt32(i * 8)) & 0xff)
        }
    }

    private static func cString(_ data: Data, _ start: Int, _ end: Int) -> String {
        guard start < end, start < data.count else { return "" }
        let bytes = data[start..<min(end, data.count)]
        return String(bytes: bytes.prefix { $0 != 0 }, encoding: .utf8) ?? ""
    }
}

private struct StoreZipWriter {
    private struct Entry {
        let path: String
        let crc: UInt32
        let size: UInt32
        let offset: UInt32
    }

    private var data = Data()
    private var entries: [Entry] = []

    mutating func add(path: String, data payload: Data) throws {
        let name = Data(path.utf8)
        guard payload.count <= Int(UInt32.max), self.data.count <= Int(UInt32.max) else {
            throw LoLLiveContainerPackageError.malformed("minimal IPA exceeds ZIP32 limits")
        }

        let crc = CRC32.compute(payload)
        let offset = UInt32(self.data.count)
        append32(0x04034b50)
        append16(20)
        append16(0)
        append16(0) // store
        append16(0)
        append16(0)
        append32(crc)
        append32(UInt32(payload.count))
        append32(UInt32(payload.count))
        append16(UInt16(name.count))
        append16(0)
        self.data.append(name)
        self.data.append(payload)

        entries.append(Entry(path: path,
                             crc: crc,
                             size: UInt32(payload.count),
                             offset: offset))
    }

    mutating func finalize() -> Data {
        let centralOffset = UInt32(data.count)

        for entry in entries {
            let name = Data(entry.path.utf8)
            append32(0x02014b50)
            append16(20)
            append16(20)
            append16(0)
            append16(0)
            append16(0)
            append16(0)
            append32(entry.crc)
            append32(entry.size)
            append32(entry.size)
            append16(UInt16(name.count))
            append16(0)
            append16(0)
            append16(0)
            append16(0)
            append32(0)
            append32(entry.offset)
            data.append(name)
        }

        let centralSize = UInt32(data.count) - centralOffset
        append32(0x06054b50)
        append16(0)
        append16(0)
        append16(UInt16(entries.count))
        append16(UInt16(entries.count))
        append32(centralSize)
        append32(centralOffset)
        append16(0)
        return data
    }

    private mutating func append16(_ value: UInt16) {
        data.append(UInt8(value & 0xff))
        data.append(UInt8((value >> 8) & 0xff))
    }

    private mutating func append32(_ value: UInt32) {
        for i in 0..<4 {
            data.append(UInt8((value >> UInt32(i * 8)) & 0xff))
        }
    }
}

private enum CRC32 {
    static let table: [UInt32] = (0..<256).map { index in
        var crc = UInt32(index)
        for _ in 0..<8 {
            crc = (crc & 1) != 0 ? (0xEDB88320 ^ (crc >> 1)) : (crc >> 1)
        }
        return crc
    }

    static func compute(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        for byte in data {
            let idx = Int((crc ^ UInt32(byte)) & 0xFF)
            crc = table[idx] ^ (crc >> 8)
        }
        return crc ^ 0xFFFFFFFF
    }
}
