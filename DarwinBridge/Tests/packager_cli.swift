import Foundation
@main
struct PackagerCLI {
    static func main() {
        guard CommandLine.arguments.count == 4 else { fatalError("usage: packager INPUT RUNTIME_DIR OUTPUT.ipa") }
        do {
            let result = try LoLLiveContainerPackager.buildMinimalIPA(
                executable: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])),
                sourceFileName: URL(fileURLWithPath: CommandLine.arguments[1]).lastPathComponent,
                runtimeDirectory: URL(fileURLWithPath: CommandLine.arguments[2]),
                outputURL: URL(fileURLWithPath: CommandLine.arguments[3]))
            print("packaged ", result.ipaURL.path, result.executableBytes)
        } catch {
            fputs("PACKAGER_REJECTED: \(error.localizedDescription)\n", stderr)
            exit(2)
        }
    }
}
