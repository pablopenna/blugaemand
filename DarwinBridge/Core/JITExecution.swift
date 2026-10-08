import Foundation
import Darwin

struct JITExecutionResult {
    let debuggerAttached: Bool
    let regionAllocated: Bool
    let regionPrepared: Bool
    let executionAttempted: Bool
    let executed: Bool
    let returnValue: Int32?
    let errnoValue: Int32
    let note: String
}

@_silgen_name("DBJIT26CreateDualMapping")
private func DBJIT26CreateDualMapping(
    _ size: Int,
    _ outRX: UnsafeMutablePointer<UnsafeMutableRawPointer?>,
    _ outRW: UnsafeMutablePointer<UnsafeMutableRawPointer?>
) -> Int32

struct JITExecutionBackend {
    static func runReturn42SelfTest() -> JITExecutionResult {
        guard isDebuggerAttached() else {
            return JITExecutionResult(
                debuggerAttached: false,
                regionAllocated: false,
                regionPrepared: false,
                executionAttempted: false,
                executed: false,
                returnValue: nil,
                errnoValue: 0,
                note: "No live debugger is attached. Launch with LiveContainer JIT and the DarwinBridge universal script."
            )
        }

        let size = Int(getpagesize())
        var rx: UnsafeMutableRawPointer?
        var rw: UnsafeMutableRawPointer?
        let status = DBJIT26CreateDualMapping(size, &rx, &rw)

        guard status == 0, let executable = rx, let writable = rw else {
            return JITExecutionResult(
                debuggerAttached: true,
                regionAllocated: false,
                regionPrepared: false,
                executionAttempted: false,
                executed: false,
                returnValue: nil,
                errnoValue: status,
                note: "The debugger did not provide a usable RX region or vm_remap could not create its RW alias."
            )
        }

        // Write through RW alias; execute through debugger-created RX mapping.
        let instructions: [UInt32] = [0x52800540, 0xD65F03C0] // mov w0,#42 ; ret
        _ = instructions.withUnsafeBytes { bytes in
            memcpy(writable, bytes.baseAddress!, bytes.count)
        }

        typealias GuestFunction = @convention(c) () -> Int32
        let function = unsafeBitCast(executable, to: GuestFunction.self)
        let value = function()

        // Do NOT detach the debugger. TXM/SPTM executable permission is tied to
        // the live debug session. The tiny mappings intentionally remain alive.
        return JITExecutionResult(
            debuggerAttached: true,
            regionAllocated: true,
            regionPrepared: true,
            executionAttempted: true,
            executed: true,
            returnValue: value,
            errnoValue: 0,
            note: "Debugger-created RX memory executed through a writable vm_remap alias and returned normally."
        )
    }

    private static func isDebuggerAttached() -> Bool {
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        var mib = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]

        let result = mib.withUnsafeMutableBufferPointer { ptr in
            sysctl(ptr.baseAddress, 4, &info, &size, nil, 0)
        }
        guard result == 0 else { return false }
        return (info.kp_proc.p_flag & P_TRACED) != 0
    }
}


struct RuntimeCallResult {
    let debuggerAttached: Bool
    let regionPrepared: Bool
    let executed: Bool
    let returnValue: UInt64?
    let expectedValue: UInt64
    let note: String
}

extension JITExecutionBackend {
    static func runStrlenRuntimeTest() -> RuntimeCallResult {
        guard isDebuggerAttachedForRuntime() else {
            return RuntimeCallResult(debuggerAttached: false,
                                     regionPrepared: false,
                                     executed: false,
                                     returnValue: nil,
                                     expectedValue: 12,
                                     note: "No live debugger is attached.")
        }

        guard let strlenPointer = RuntimeCompatibility.pointer(to: "strlen") else {
            return RuntimeCallResult(debuggerAttached: true,
                                     regionPrepared: false,
                                     executed: false,
                                     returnValue: nil,
                                     expectedValue: 12,
                                     note: "Host strlen could not be resolved.")
        }

        let pageSize = Int(getpagesize())
        var rx: UnsafeMutableRawPointer?
        var rw: UnsafeMutableRawPointer?
        let status = DBJIT26CreateDualMapping(pageSize, &rx, &rw)
        guard status == 0, let executable = rx, let writable = rw else {
            return RuntimeCallResult(debuggerAttached: true,
                                     regionPrepared: false,
                                     executed: false,
                                     returnValue: nil,
                                     expectedValue: 12,
                                     note: "JIT26 could not create the runtime-call code page (status \(status)).")
        }

        let message = Array("DarwinBridge\0".utf8)
        let stringOffset = 0x100
        message.withUnsafeBytes { bytes in
            memcpy(writable.advanced(by: stringOffset), bytes.baseAddress!, bytes.count)
        }

        // AArch64 ABI-safe guest stub:
        //   stp x29, x30, [sp, #-16]!
        //   mov x29, sp
        //   ldr x0,  literal_string_address
        //   ldr x16, literal_strlen_address
        //   blr x16
        //   ldp x29, x30, [sp], #16
        //   ret
        //   nop
        //   .quad string
        //   .quad strlen
        //
        // BLR overwrites x30 (LR), so preserving/restoring x30 is required.
        let instructions: [UInt32] = [
            0xA9BF7BFD, // stp x29, x30, [sp, #-16]!
            0x910003FD, // mov x29, sp
            0x580000C0, // ldr x0,  #24 -> literal at offset 32
            0x580000F0, // ldr x16, #28 -> literal at offset 40
            0xD63F0200, // blr x16
            0xA8C17BFD, // ldp x29, x30, [sp], #16
            0xD65F03C0, // ret
            0xD503201F  // nop / align literals
        ]

        _ = instructions.withUnsafeBytes { bytes in
            memcpy(writable, bytes.baseAddress!, bytes.count)
        }

        let stringAddress = UInt64(UInt(bitPattern: executable.advanced(by: stringOffset)))
        let strlenAddress = UInt64(UInt(bitPattern: strlenPointer))
        writable.advanced(by: 32).storeBytes(of: stringAddress.littleEndian, as: UInt64.self)
        writable.advanced(by: 40).storeBytes(of: strlenAddress.littleEndian, as: UInt64.self)

        typealias GuestFunction = @convention(c) () -> UInt64
        let function = unsafeBitCast(executable, to: GuestFunction.self)
        let value = function()

        return RuntimeCallResult(debuggerAttached: true,
                                 regionPrepared: true,
                                 executed: true,
                                 returnValue: value,
                                 expectedValue: 12,
                                 note: value == 12
                                    ? "Guest ARM64 called host strlen successfully."
                                    : "Guest ARM64 returned an unexpected strlen result.")
    }

    private static func isDebuggerAttachedForRuntime() -> Bool {
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        var mib = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        let result = mib.withUnsafeMutableBufferPointer { ptr in
            sysctl(ptr.baseAddress, 4, &info, &size, nil, 0)
        }
        guard result == 0 else { return false }
        return (info.kp_proc.p_flag & P_TRACED) != 0
    }
}


@_silgen_name("DBRuntimeChainAddress")
private func DBRuntimeChainAddress() -> UnsafeMutableRawPointer?

extension JITExecutionBackend {
    static func runRuntimeChainTest() -> RuntimeCallResult {
        guard isDebuggerAttachedForRuntime() else {
            return RuntimeCallResult(debuggerAttached: false,
                                     regionPrepared: false,
                                     executed: false,
                                     returnValue: nil,
                                     expectedValue: 20,
                                     note: "No live debugger is attached.")
        }

        guard let chainPointer = DBRuntimeChainAddress() else {
            return RuntimeCallResult(debuggerAttached: true,
                                     regionPrepared: false,
                                     executed: false,
                                     returnValue: nil,
                                     expectedValue: 20,
                                     note: "Runtime chain helper address is unavailable.")
        }

        let pageSize = Int(getpagesize())
        var rx: UnsafeMutableRawPointer?
        var rw: UnsafeMutableRawPointer?
        let status = DBJIT26CreateDualMapping(pageSize, &rx, &rw)
        guard status == 0, let executable = rx, let writable = rw else {
            return RuntimeCallResult(debuggerAttached: true,
                                     regionPrepared: false,
                                     executed: false,
                                     returnValue: nil,
                                     expectedValue: 20,
                                     note: "JIT26 runtime-chain page failed (status \(status)).")
        }

        let message = Array("DarwinBridge runtime\0".utf8)
        let stringOffset = 0x100
        _ = message.withUnsafeBytes { bytes in
            memcpy(writable.advanced(by: stringOffset), bytes.baseAddress!, bytes.count)
        }

        // ABI-safe guest -> runtime bridge:
        // save FP/LR, load C string and helper address, BLR, restore FP/LR, RET.
        let instructions: [UInt32] = [
            0xA9BF7BFD,
            0x910003FD,
            0x580000C0,
            0x580000F0,
            0xD63F0200,
            0xA8C17BFD,
            0xD65F03C0,
            0xD503201F
        ]
        _ = instructions.withUnsafeBytes { bytes in
            memcpy(writable, bytes.baseAddress!, bytes.count)
        }

        let stringAddress = UInt64(UInt(bitPattern: executable.advanced(by: stringOffset)))
        let helperAddress = UInt64(UInt(bitPattern: chainPointer))
        writable.advanced(by: 32).storeBytes(of: stringAddress.littleEndian, as: UInt64.self)
        writable.advanced(by: 40).storeBytes(of: helperAddress.littleEndian, as: UInt64.self)

        typealias GuestFunction = @convention(c) () -> UInt64
        let function = unsafeBitCast(executable, to: GuestFunction.self)
        let value = function()

        return RuntimeCallResult(debuggerAttached: true,
                                 regionPrepared: true,
                                 executed: true,
                                 returnValue: value,
                                 expectedValue: 20,
                                 note: value == 20
                                    ? "Guest completed malloc → memcpy → strlen → free."
                                    : "Runtime chain returned an unexpected value.")
    }
}


@_silgen_name("DBAppKitRequestDemoWindowAddress")
private func DBAppKitRequestDemoWindowAddress() -> UnsafeMutableRawPointer?

struct AppKitBridgeTestResult {
    let debuggerAttached: Bool
    let regionPrepared: Bool
    let guestExecuted: Bool
    let bridgeReturnValue: Int32?
    let passed: Bool
    let note: String
}

extension JITExecutionBackend {
    static func runAppKitWindowBridgeTest() -> AppKitBridgeTestResult {
        guard isDebuggerAttachedForRuntime() else {
            return AppKitBridgeTestResult(debuggerAttached: false,
                                          regionPrepared: false,
                                          guestExecuted: false,
                                          bridgeReturnValue: nil,
                                          passed: false,
                                          note: "No live debugger is attached.")
        }

        let pageSize = Int(getpagesize())
        var rx: UnsafeMutableRawPointer?
        var rw: UnsafeMutableRawPointer?
        let status = DBJIT26CreateDualMapping(pageSize, &rx, &rw)
        guard status == 0, let executable = rx, let writable = rw else {
            return AppKitBridgeTestResult(debuggerAttached: true,
                                          regionPrepared: false,
                                          guestExecuted: false,
                                          bridgeReturnValue: nil,
                                          passed: false,
                                          note: "JIT26 could not create the AppKit mailbox page (status \(status)).")
        }

        // Stage-5 mailbox:
        // Guest ARM64 does not call C, Swift or UIKit here.
        // It only writes 1 to a host-owned UInt32 and returns.
        let mailbox = UnsafeMutablePointer<UInt32>.allocate(capacity: 1)
        mailbox.initialize(to: 0)
        defer {
            mailbox.deinitialize(count: 1)
            mailbox.deallocate()
        }

        //   ldr x16, mailbox_literal
        //   mov w0, #1
        //   str w0, [x16]
        //   ret
        //   nop
        //   nop
        //   .quad mailbox
        let instructions: [UInt32] = [
            0x580000D0, // ldr x16, #24
            0x52800020, // mov w0, #1
            0xB9000200, // str w0, [x16]
            0xD65F03C0, // ret
            0xD503201F, // nop
            0xD503201F  // nop
        ]

        _ = instructions.withUnsafeBytes { bytes in
            memcpy(writable, bytes.baseAddress!, bytes.count)
        }

        let mailboxAddress = UInt64(UInt(bitPattern: mailbox))
        writable.advanced(by: 24).storeBytes(of: mailboxAddress.littleEndian, as: UInt64.self)

        typealias GuestFunction = @convention(c) () -> Int32
        let function = unsafeBitCast(executable, to: GuestFunction.self)
        let value = function()

        let posted = mailbox.pointee == 1
        return AppKitBridgeTestResult(debuggerAttached: true,
                                      regionPrepared: true,
                                      guestExecuted: true,
                                      bridgeReturnValue: value,
                                      passed: posted && value == 1,
                                      note: posted && value == 1
                                        ? "Guest ARM64 posted an AppKit request through a memory mailbox."
                                        : "Guest returned, but the AppKit mailbox was not updated as expected.")
    }
}


struct GraphicsCommandTestResult {
    let debuggerAttached: Bool
    let regionPrepared: Bool
    let guestExecuted: Bool
    let commands: [DBGraphicsCommand]
    let passed: Bool
    let note: String
}

extension JITExecutionBackend {
    static func runGraphicsCommandQueueTest() -> GraphicsCommandTestResult {
        guard isDebuggerAttachedForRuntime() else {
            return GraphicsCommandTestResult(debuggerAttached: false,
                                             regionPrepared: false,
                                             guestExecuted: false,
                                             commands: [],
                                             passed: false,
                                             note: "No live debugger is attached.")
        }

        let pageSize = Int(getpagesize())
        var rx: UnsafeMutableRawPointer?
        var rw: UnsafeMutableRawPointer?
        let status = DBJIT26CreateDualMapping(pageSize, &rx, &rw)
        guard status == 0, let executable = rx, let writable = rw else {
            return GraphicsCommandTestResult(debuggerAttached: true,
                                             regionPrepared: false,
                                             guestExecuted: false,
                                             commands: [],
                                             passed: false,
                                             note: "JIT26 could not create the graphics command page (status \(status)).")
        }

        // Five UInt32 words per command: opcode + four arguments.
        let wordCount = 20
        let mailbox = UnsafeMutablePointer<UInt32>.allocate(capacity: wordCount)
        mailbox.initialize(repeating: 0, count: wordCount)
        defer {
            mailbox.deinitialize(count: wordCount)
            mailbox.deallocate()
        }

        // Guest writes a four-command packet:
        // CREATE_VIEW
        // SET_FRAME x=52 y=150 w=340 h=190
        // SET_STYLE style=1
        // SET_TITLE titleID=2
        //
        // The packet itself is prefilled by host for this first queue transport
        // test; guest commits it by writing commandCount=4 after executing.
        let packet: [UInt32] = [
            1, 0, 0, 0, 0,
            2, 52, 150, 340, 190,
            3, 1, 0, 0, 0,
            4, 2, 0, 0, 0
        ]
        for (index, word) in packet.enumerated() {
            mailbox[index] = word
        }

        let commandCount = UnsafeMutablePointer<UInt32>.allocate(capacity: 1)
        commandCount.initialize(to: 0)
        defer {
            commandCount.deinitialize(count: 1)
            commandCount.deallocate()
        }

        // ldr x16,countLiteral ; mov w0,#4 ; str w0,[x16] ; ret
        // nop ; nop ; .quad commandCount
        let instructions: [UInt32] = [
            0x580000D0,
            0x52800080,
            0xB9000200,
            0xD65F03C0,
            0xD503201F,
            0xD503201F
        ]
        _ = instructions.withUnsafeBytes { bytes in
            memcpy(writable, bytes.baseAddress!, bytes.count)
        }

        let countAddress = UInt64(UInt(bitPattern: commandCount))
        writable.advanced(by: 24).storeBytes(of: countAddress.littleEndian, as: UInt64.self)

        typealias GuestFunction = @convention(c) () -> Int32
        let function = unsafeBitCast(executable, to: GuestFunction.self)
        let value = function()

        guard value == 4, commandCount.pointee == 4 else {
            return GraphicsCommandTestResult(debuggerAttached: true,
                                             regionPrepared: true,
                                             guestExecuted: true,
                                             commands: [],
                                             passed: false,
                                             note: "Guest did not commit the expected command count.")
        }

        var commands: [DBGraphicsCommand] = []
        for i in 0..<4 {
            let base = i * 5
            commands.append(DBGraphicsCommand(opcode: mailbox[base],
                                              a: mailbox[base + 1],
                                              b: mailbox[base + 2],
                                              c: mailbox[base + 3],
                                              d: mailbox[base + 4]))
        }

        return GraphicsCommandTestResult(debuggerAttached: true,
                                         regionPrepared: true,
                                         guestExecuted: true,
                                         commands: commands,
                                         passed: commands.count == 4,
                                         note: "Guest committed a four-command graphics packet through shared memory.")
    }
}
