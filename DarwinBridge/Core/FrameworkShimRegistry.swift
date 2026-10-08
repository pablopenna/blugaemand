import Foundation

struct FrameworkShimRegistry {
    static func assess(_ dependency: MachODependency) -> DependencyAssessment {
        let p = dependency.path.lowercased()

        let nativeFamilies = [
            "foundation.framework","corefoundation.framework","coregraphics.framework","quartzcore.framework",
            "metal.framework","metalkit.framework","avfoundation.framework","coretext.framework","cfnetwork.framework",
            "security.framework","systemconfiguration.framework","accelerate.framework","audiotoolbox.framework",
            "audiounit.framework","coreaudio.framework","corebluetooth.framework","coreimage.framework",
            "corelocation.framework","coremedia.framework","corevideo.framework","gamecontroller.framework",
            "iosurface.framework","localauthentication.framework","mediaaccessibility.framework","mediaplayer.framework",
            "network.framework","safariservices.framework","usernotifications.framework","videotoolbox.framework",
            "vision.framework","webkit.framework"
        ]
        if nativeFamilies.contains(where: p.contains) || p.hasSuffix("/libobjc.a.dylib") ||
            p.contains("libresolv") || p.contains("libbz2") || p.contains("/libz.") {
            return .init(dependency: dependency, disposition: .native, replacement: nil,
                         note: "Stage 21X-B: matching iOS runtime/framework family; symbol broker validates the actual import.")
        }

        if p.contains("appkit.framework") || p.contains("cocoa.framework") {
            return .init(dependency: dependency, disposition: .shim,
                         replacement: "UIKit + DarwinBridge batch AppKit facade",
                         note: "All harvested AppKit classes/globals are pre-registered; semantic UI translation remains UIKit-backed.")
        }

        let desktopShimFamilies = [
            "applicationservices.framework","coreservices.framework","diskarbitration.framework",
            "scriptingbridge.framework","corewlan.framework","iobluetooth.framework","iokit.framework",
            "imagecapturecore.framework","opendirectory.framework","securityinterface.framework",
            "servicemanagement.framework","screencapturekit.framework","screentime.framework",
            "forcfeedback.framework","forcefeedback.framework","ldap.framework"
        ]
        if desktopShimFamilies.contains(where: p.contains) {
            return .init(dependency: dependency, disposition: .shim,
                         replacement: "DarwinBridge Stage 21X desktop compatibility facade",
                         note: "Desktop-only family observed in Riot payload; launch-critical symbols are handled in batch rather than crash-by-crash.")
        }

        if p.contains("opengl.framework") || p.contains("quartz.framework") {
            return .init(dependency: dependency, disposition: .partial,
                         replacement: "Metal/QuartzCore + bundled CEF ANGLE where applicable",
                         note: "Desktop rendering surface cannot be ABI-forwarded wholesale; bundled ANGLE/CEF and Metal paths are preferred.")
        }

        if p.contains("libsystem") || p.contains("libc++") || p.contains("libsandbox") ||
            p.contains("libbsm") || p.contains("libcups") || p.contains("libpmenergy") || p.contains("libpmsample") {
            return .init(dependency: dependency, disposition: .partial,
                         replacement: "iOS system runtime / DarwinBridge syscall facade",
                         note: "Desktop dylib name differs; imports are resolved individually against iOS or the batch shim registry.")
        }

        if p.hasPrefix("@rpath/") || p.hasPrefix("@executable_path/") {
            return .init(dependency: dependency, disposition: .partial,
                         replacement: "Bundle harvested Riot framework/dylib beside executable",
                         note: "Riot/CEF private dependency; preserve and load the matching macOS ARM64 payload with its rpath topology.")
        }

        return .init(dependency: dependency, disposition: .unknown, replacement: nil,
                     note: "Not present in the Stage 21X classification table.")
    }
}
