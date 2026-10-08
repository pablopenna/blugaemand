import Foundation

// Stage 22B: fail-closed guest execution readiness. Do not jump to LC_MAIN
// until dependency closure, initialization, and executable mappings are proven.
struct DBGuestRunReport {
    let title: String
    let executablePath: String
    let entryOffset: UInt64?
    let dependencyCount: Int
    let unresolvedImports: Int
    let ready: Bool
    let findings: [String]

    var description: String {
        let status = ready ? "READY" : "BLOCKED"
        return ([status + " — " + title,
                 "Executable: " + executablePath,
                 "LC_MAIN: " + (entryOffset.map { String(format: "0x%llx", $0) } ?? "missing"),
                 "Dependencies: " + String(dependencyCount),
                 "Unresolved imports: " + String(unresolvedImports)] + findings).joined(separator: "\n")
    }
}

enum DBGuestExecution {
    static func preflight(_ guest: DBGuestApp) throws -> DBGuestRunReport {
        let app = DBGuestLibrary.root.appendingPathComponent(guest.id)
            .appendingPathComponent(guest.bundlePath)
        let binary = app.appendingPathComponent("Contents/MacOS").appendingPathComponent(guest.executable)
        let source = try Data(contentsOf: binary)
        let data = try MachOParser.preferredArm64Slice(source)
        let image = try MachOParser.parse(data)
        let deep = try DeepSymbolScanner.scan(data: data, image: image)
        var findings: [String] = []
        if !image.isArm64 { findings.append("Requires ARM64 guest.") }
        if image.encrypted { findings.append("Encrypted Mach-O cannot execute.") }
        if image.entryOffset == nil { findings.append("LC_MAIN missing.") }
        if deep.unresolvedCount > 0 {
            findings.append("Unresolved guest imports must be bound before execution.")
        }
        for dep in image.dependencies {
            let path = dep.path
            if path.hasPrefix("@rpath/") {
                let suffix = String(path.dropFirst("@rpath/".count))
                let roots = image.rpaths.map { value -> URL in
                    let resolved = value.replacingOccurrences(of: "@executable_path", with: binary.deletingLastPathComponent().path)
                        .replacingOccurrences(of: "@loader_path", with: binary.deletingLastPathComponent().path)
                    return URL(fileURLWithPath: resolved).appendingPathComponent(suffix)
                }
                if !roots.contains(where: { FileManager.default.fileExists(atPath: $0.path) }) {
                    findings.append("Missing @rpath dependency: " + path)
                }
            } else if path.hasPrefix("@loader_path/") || path.hasPrefix("@executable_path/") {
                let prefix = path.hasPrefix("@loader_path/") ? "@loader_path/" : "@executable_path/"
                let relative = String(path.dropFirst(prefix.count))
                let target = binary.deletingLastPathComponent().appendingPathComponent(relative)
                if !FileManager.default.fileExists(atPath: target.path) {
                    findings.append("Missing bundled dependency: " + path)
                }
            }
        }
        // The current loader only maps for inspection and cannot safely enter
        // an arbitrary macOS process with dyld, TLS, stack, ObjC and AppKit state.
        findings.append("Guest entry transfer unavailable: macOS dyld/initializers/TLS and executable mapping not implemented.")
        return DBGuestRunReport(title: guest.name, executablePath: binary.path,
                                entryOffset: image.entryOffset,
                                dependencyCount: image.dependencies.count,
                                unresolvedImports: deep.unresolvedCount,
                                ready: false, findings: findings)
    }
}
