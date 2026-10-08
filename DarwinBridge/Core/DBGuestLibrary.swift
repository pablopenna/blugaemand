import Foundation

struct DBGuestApp: Codable, Identifiable {
    let id: String
    let name: String
    let executable: String
    let bundlePath: String
    let dependencies: [String]
    let minimumOS: String?
    let importCount: Int
    let unresolvedCount: Int
    let blockers: [String]
}

enum DBGuestLibrary {
    static var root: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("DarwinBridge/Guests", isDirectory: true)
    }

    static func installed() -> [DBGuestApp] {
        guard let urls = try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil) else { return [] }
        return urls.compactMap { url in
            guard let data = try? Data(contentsOf: url.appendingPathComponent("guest.json")) else { return nil }
            return try? JSONDecoder().decode(DBGuestApp.self, from: data)
        }.sorted { $0.name < $1.name }
    }

    static func install(bundle source: URL) throws -> DBGuestApp {
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        guard source.pathExtension.lowercased() == "app", source.hasDirectoryPath else {
            throw NSError(domain: "DarwinBridge", code: 1, userInfo: [NSLocalizedDescriptionKey: "Select a complete .app bundle, not an executable or IPA."])
        }
        let infoURL = source.appendingPathComponent("Contents/Info.plist")
        guard let plist = NSDictionary(contentsOf: infoURL),
              let executable = plist["CFBundleExecutable"] as? String else {
            throw NSError(domain: "DarwinBridge", code: 2, userInfo: [NSLocalizedDescriptionKey: "macOS app Info.plist or CFBundleExecutable missing."])
        }
        let binaryURL = source.appendingPathComponent("Contents/MacOS").appendingPathComponent(executable)
        let bytes = try Data(contentsOf: binaryURL)
        let arm64 = try MachOParser.preferredArm64Slice(bytes)
        let image = try MachOParser.parse(arm64)
        guard image.isArm64, !image.encrypted else {
            throw NSError(domain: "DarwinBridge", code: 3, userInfo: [NSLocalizedDescriptionKey: "Requires unencrypted macOS ARM64 Mach-O."])
        }
        let deep = try DeepSymbolScanner.scan(data: arm64, image: image)
        let blockers = image.dependencies.filter {
            $0.path.hasPrefix("@rpath/") || $0.path.hasPrefix("@loader_path/")
        }.map { "Bundle dependency must be validated: " + $0.path }
        let id = UUID().uuidString
        let dest = root.appendingPathComponent(id, isDirectory: true)
        try FileManager.default.createDirectory(at: dest, withIntermediateDirectories: true)
        do {
            try FileManager.default.copyItem(at: source, to: dest.appendingPathComponent(source.lastPathComponent))
            let record = DBGuestApp(id: id,
                                    name: (plist["CFBundleDisplayName"] as? String) ?? (plist["CFBundleName"] as? String) ?? source.deletingPathExtension().lastPathComponent,
                                    executable: executable,
                                    bundlePath: source.lastPathComponent,
                                    dependencies: image.dependencies.map(\.path),
                                    minimumOS: image.minimumOS,
                                    importCount: deep.imports.count,
                                    unresolvedCount: deep.unresolvedCount,
                                    blockers: blockers)
            let encoded = try JSONEncoder().encode(record)
            try encoded.write(to: dest.appendingPathComponent("guest.json"), options: .atomic)
            return record
        } catch {
            try? FileManager.default.removeItem(at: dest)
            throw error
        }
    }
}
