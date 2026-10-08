import Foundation
import UIKit

@_silgen_name("DBAppKitConsumeDemoWindowRequest")
private func DBAppKitConsumeDemoWindowRequest() -> Int32

@MainActor
final class AppKitUIKitBridge {
    static let shared = AppKitUIKitBridge()

    private var guestOverlays: [UIView] = []

    struct Snapshot {
        let windowCount: Int
        let lastWindowVisible: Bool
        let lastViewClass: String
    }

    private(set) var snapshot = Snapshot(windowCount: 0,
                                         lastWindowVisible: false,
                                         lastViewClass: "none")

    @discardableResult
    func createDemoWindowFacade() -> Int32 {
        guard let hostWindow = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { !$0.isHidden && $0.alpha > 0 }),
              let hostView = hostWindow.rootViewController?.view else {
            return -10
        }

        let overlay = UIView(frame: hostView.bounds)
        overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        overlay.backgroundColor = UIColor.black.withAlphaComponent(0.18)

        let panelWidth = min(hostView.bounds.width - 40, 420)
        let panel = UIView(frame: CGRect(x: (hostView.bounds.width - panelWidth) / 2,
                                         y: 120,
                                         width: panelWidth,
                                         height: 220))
        panel.autoresizingMask = [.flexibleLeftMargin, .flexibleRightMargin]
        panel.backgroundColor = .secondarySystemBackground
        panel.layer.cornerRadius = 20
        panel.layer.borderWidth = 1

        let label = UILabel(frame: CGRect(x: 20, y: 24, width: panelWidth - 40, height: 36))
        label.text = "DarwinBridge NSView → UIView"
        label.font = .systemFont(ofSize: 19, weight: .semibold)
        panel.addSubview(label)

        let detail = UILabel(frame: CGRect(x: 20, y: 72, width: panelWidth - 40, height: 88))
        detail.text = "Guest ARM64 requested an NSWindow/NSView facade. DarwinBridge rendered it inside the current UIKit host window."
        detail.numberOfLines = 4
        detail.font = .systemFont(ofSize: 14)
        panel.addSubview(detail)

        let badge = UILabel(frame: CGRect(x: 20, y: 172, width: panelWidth - 40, height: 24))
        badge.text = "AppKit facade active"
        badge.font = .systemFont(ofSize: 13, weight: .medium)
        badge.textAlignment = .center
        panel.addSubview(badge)

        overlay.addSubview(panel)
        hostView.addSubview(overlay)

        guestOverlays.append(overlay)
        snapshot = Snapshot(windowCount: guestOverlays.count,
                            lastWindowVisible: true,
                            lastViewClass: String(describing: type(of: panel)))
        return 1
    }

    @discardableResult
    func consumePendingGuestRequest() -> Int32 {
        guard DBAppKitConsumeDemoWindowRequest() == 1 else { return 0 }
        return createDemoWindowFacade()
    }

    func dismissAllGuestWindows() {
        for overlay in guestOverlays {
            overlay.removeFromSuperview()
        }
        guestOverlays.removeAll()
        snapshot = Snapshot(windowCount: 0,
                            lastWindowVisible: false,
                            lastViewClass: "none")
    }
}

@_cdecl("DBAppKitCreateDemoWindow")
public func DBAppKitCreateDemoWindow() -> Int32 {
    // Never mutate UIKit synchronously from inside the JIT guest call.
    // Queue the facade creation so guest ARM64 can unwind first.
    DispatchQueue.main.async {
        AppKitUIKitBridge.shared.createDemoWindowFacade()
    }
    return 1
}

@_cdecl("DBAppKitDismissGuestWindows")
public func DBAppKitDismissGuestWindows() {
    DispatchQueue.main.async {
        AppKitUIKitBridge.shared.dismissAllGuestWindows()
    }
}
