import Foundation
import UIKit

enum DBGraphicsOpcode: UInt32 {
    case createView = 1
    case setFrame = 2
    case setStyle = 3
    case setTitle = 4
}

struct DBGraphicsCommand {
    let opcode: UInt32
    let a: UInt32
    let b: UInt32
    let c: UInt32
    let d: UInt32
}

struct DBInputSnapshot {
    let sequence: UInt64
    let x: Double
    let y: Double
    let kind: String
}

@MainActor
final class GraphicsInputBridge: NSObject {
    static let shared = GraphicsInputBridge()

    private var overlay: UIView?
    private var panel: UIView?
    private var titleLabel: UILabel?
    private var inputSequence: UInt64 = 0

    private(set) var lastInput = DBInputSnapshot(sequence: 0,
                                                  x: 0,
                                                  y: 0,
                                                  kind: "none")

    private func hostView() -> UIView? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { !$0.isHidden && $0.alpha > 0 }?
            .rootViewController?.view
    }

    func execute(_ commands: [DBGraphicsCommand]) -> Int {
        guard let host = hostView() else { return -10 }

        for command in commands {
            guard let opcode = DBGraphicsOpcode(rawValue: command.opcode) else { continue }

            switch opcode {
            case .createView:
                if overlay == nil {
                    let root = UIView(frame: host.bounds)
                    root.autoresizingMask = [.flexibleWidth, .flexibleHeight]
                    root.backgroundColor = .clear

                    let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
                    root.addGestureRecognizer(tap)

                    host.addSubview(root)
                    overlay = root
                }

                if panel == nil, let overlay {
                    let view = UIView(frame: CGRect(x: 40, y: 140, width: 300, height: 170))
                    view.layer.cornerRadius = 18
                    view.layer.borderWidth = 1
                    overlay.addSubview(view)
                    panel = view

                    let label = UILabel(frame: CGRect(x: 16, y: 16, width: 268, height: 36))
                    label.font = .systemFont(ofSize: 18, weight: .semibold)
                    view.addSubview(label)
                    titleLabel = label
                }

            case .setFrame:
                panel?.frame = CGRect(x: Int(command.a),
                                      y: Int(command.b),
                                      width: Int(command.c),
                                      height: Int(command.d))

            case .setStyle:
                if command.a == 1 {
                    panel?.backgroundColor = .systemIndigo
                    titleLabel?.textColor = .white
                } else {
                    panel?.backgroundColor = .secondarySystemBackground
                    titleLabel?.textColor = .label
                }

            case .setTitle:
                switch command.a {
                case 1:
                    titleLabel?.text = "DarwinBridge Guest Surface"
                case 2:
                    titleLabel?.text = "Graphics Command Queue PASS"
                default:
                    titleLabel?.text = "Guest View"
                }
            }
        }

        return commands.count
    }

    func dismiss() {
        overlay?.removeFromSuperview()
        overlay = nil
        panel = nil
        titleLabel = nil
    }

    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        guard let view = gesture.view else { return }
        let p = gesture.location(in: view)
        inputSequence &+= 1
        lastInput = DBInputSnapshot(sequence: inputSequence,
                                    x: p.x,
                                    y: p.y,
                                    kind: "tap")
    }
}
