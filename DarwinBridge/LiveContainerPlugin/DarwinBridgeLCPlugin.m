#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <CFNetwork/CFNetwork.h>
#import <Security/Security.h>
#import <objc/runtime.h>
#include <signal.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <pthread.h>
#include <stdlib.h>

static NSString * const DBPluginLogPrefix = @"[DarwinBridgeLC]";
static int DBRuntimeLogFD = -1;
static void DBWriteRaw(const char *text);
static volatile int DBBackgroundHeartbeatStarted = 0;

static void *DBBackgroundHeartbeatMain(void *unused) {
    (void)unused;
    const int checkpoints[] = {1, 3, 10, 30, 60, 120, 180};
    int elapsed = 0;
    for (unsigned i = 0; i < sizeof(checkpoints) / sizeof(checkpoints[0]); i++) {
        int target = checkpoints[i];
        sleep((unsigned)(target - elapsed));
        elapsed = target;
        char line[128];
        snprintf(line, sizeof(line), "[DarwinBridgeLC] background-heartbeat +%ds\n", target);
        DBWriteRaw(line);
    }
    return NULL;
}

static void DBStartBackgroundHeartbeat(void) {
    if (__sync_lock_test_and_set(&DBBackgroundHeartbeatStarted, 1)) return;
    pthread_t thread;
    if (pthread_create(&thread, NULL, DBBackgroundHeartbeatMain, NULL) == 0) {
        pthread_detach(thread);
        DBWriteRaw("[DarwinBridgeLC] background-heartbeat-thread=started\n");
    } else {
        DBWriteRaw("[DarwinBridgeLC] background-heartbeat-thread=failed\n");
    }
}

static void DBNormalExitMarker(void) {
    DBWriteRaw("\n=== DARWINBRIDGE NORMAL EXIT ===\n");
}

static void DBWriteRaw(const char *text) {
    if (!text || DBRuntimeLogFD < 0) return;
    write(DBRuntimeLogFD, text, strlen(text));
    fsync(DBRuntimeLogFD);
}

static void DBLog(NSString *message) {
    NSLog(@"%@ %@", DBPluginLogPrefix, message);
    if (DBRuntimeLogFD >= 0 && message) {
        NSString *line = [NSString stringWithFormat:@"%@ %@\n", DBPluginLogPrefix, message];
        const char *utf8 = line.UTF8String;
        if (utf8) DBWriteRaw(utf8);
    }
}

// Stage 21X-C: a visible, non-destructive startup probe. A black screen no
// longer means "unknown": this overlay proves how far the real macOS client
// progressed through the compatibility layer.
static UIWindow *DBDiagnosticWindow = nil;

static void DBShowRuntimeStatus(NSString *phase) {
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            UIApplication *app = UIApplication.sharedApplication;
            DBLog([NSString stringWithFormat:@"visible-probe phase=%@ appState=%ld scenes=%lu",
                   phase ?: @"unknown",
                   (long)app.applicationState,
                   (unsigned long)app.connectedScenes.count]);

            UIWindow *window = nil;
            for (UIScene *scene in app.connectedScenes) {
                if (![scene isKindOfClass:UIWindowScene.class]) continue;
                UIWindowScene *windowScene = (UIWindowScene *)scene;
                for (UIWindow *candidate in windowScene.windows) {
                    if (!candidate.hidden) { window = candidate; break; }
                }
                if (!DBDiagnosticWindow) {
                    DBDiagnosticWindow = [[UIWindow alloc] initWithWindowScene:windowScene];
                    DBDiagnosticWindow.frame = windowScene.coordinateSpace.bounds;
                    DBDiagnosticWindow.windowLevel = UIWindowLevelAlert + 100;
                    UIViewController *vc = [UIViewController new];
                    vc.view.backgroundColor = UIColor.clearColor;
                    DBDiagnosticWindow.rootViewController = vc;
                    DBDiagnosticWindow.hidden = NO;
                }
                break;
            }

            UIView *host = DBDiagnosticWindow.rootViewController.view ?: window;
            if (!host) {
                DBLog(@"visible-probe no UIKit window available");
                return;
            }

            const NSInteger tag = 0xDB21C;
            UILabel *label = (UILabel *)[host viewWithTag:tag];
            if (![label isKindOfClass:UILabel.class]) {
                label = [[UILabel alloc] initWithFrame:CGRectMake(0, 0, 0, 0)];
                label.tag = tag;
                label.numberOfLines = 2;
                label.textAlignment = NSTextAlignmentCenter;
                label.font = [UIFont monospacedSystemFontOfSize:12 weight:UIFontWeightSemibold];
                label.textColor = UIColor.whiteColor;
                label.backgroundColor = [UIColor colorWithWhite:0 alpha:0.82];
                label.layer.cornerRadius = 10;
                label.layer.masksToBounds = YES;
                [host addSubview:label];
            }
            CGFloat width = MIN(MAX(host.bounds.size.width - 32.0, 260.0), 560.0);
            label.frame = CGRectMake((host.bounds.size.width - width) * 0.5, 24.0, width, 54.0);
            label.text = [NSString stringWithFormat:@"DarwinBridge 21G\n%@", phase ?: @"unknown"];
        }
    });
}

static void DBScheduleRuntimeWatchdog(void) {
    NSArray<NSNumber *> *delays = @[@1, @3, @10];
    for (NSNumber *seconds in delays) {
        int64_t delay = seconds.longLongValue;
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay * NSEC_PER_SEC),
                       dispatch_get_main_queue(), ^{
            UIApplication *app = UIApplication.sharedApplication;
            NSUInteger windows = 0;
            for (UIScene *scene in app.connectedScenes) {
                if ([scene isKindOfClass:UIWindowScene.class]) {
                    windows += ((UIWindowScene *)scene).windows.count;
                }
            }
            DBLog([NSString stringWithFormat:@"heartbeat +%llds appState=%ld windows=%lu",
                   delay, (long)app.applicationState, (unsigned long)windows]);
        });
    }
}

static void DBFatalSignalHandler(int sig) {
    const char *name = "SIGNAL";
    if (sig == SIGABRT) name = "SIGABRT";
    else if (sig == SIGSEGV) name = "SIGSEGV";
    else if (sig == SIGBUS) name = "SIGBUS";
    else if (sig == SIGILL) name = "SIGILL";
    else if (sig == SIGFPE) name = "SIGFPE";
    else if (sig == SIGTRAP) name = "SIGTRAP";
    DBWriteRaw("\n=== DARWINBRIDGE FATAL ===\n");
    DBWriteRaw(name);
    DBWriteRaw("\n");
    signal(sig, SIG_DFL);
    kill(getpid(), sig);
}

static void DBUncaughtExceptionHandler(NSException *exception) {
    DBWriteRaw("\n=== DARWINBRIDGE OBJC EXCEPTION ===\n");
    DBLog([NSString stringWithFormat:@"name=%@ reason=%@", exception.name, exception.reason]);
    for (NSString *line in exception.callStackSymbols) DBLog(line);
}

static void DBInstallCrashDiagnostics(void) {
    @autoreleasepool {
        NSString *documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES).firstObject;
        if (!documents.length) documents = NSTemporaryDirectory();
        NSString *path = [documents stringByAppendingPathComponent:@"DarwinBridge-runtime.log"];
        DBRuntimeLogFD = open(path.fileSystemRepresentation, O_CREAT | O_WRONLY | O_APPEND, 0644);
        if (DBRuntimeLogFD >= 0) DBWriteRaw("\n=== DARWINBRIDGE RUN BEGIN ===\n");
        DBStartBackgroundHeartbeat();
        atexit(DBNormalExitMarker);
        NSSetUncaughtExceptionHandler(DBUncaughtExceptionHandler);
        signal(SIGABRT, DBFatalSignalHandler);
        signal(SIGSEGV, DBFatalSignalHandler);
        signal(SIGBUS, DBFatalSignalHandler);
        signal(SIGILL, DBFatalSignalHandler);
        signal(SIGFPE, DBFatalSignalHandler);
        signal(SIGTRAP, DBFatalSignalHandler);
        DBLog([NSString stringWithFormat:@"persistent log=%@", path]);
    }
}

@interface DBCompatObject : NSObject
@end

@implementation DBCompatObject
+ (BOOL)resolveInstanceMethod:(SEL)sel {
    DBLog([NSString stringWithFormat:@"unhandled instance selector %@ on %@", NSStringFromSelector(sel), NSStringFromClass(self)]);
    return [super resolveInstanceMethod:sel];
}
+ (BOOL)resolveClassMethod:(SEL)sel {
    DBLog([NSString stringWithFormat:@"unhandled class selector %@ on %@", NSStringFromSelector(sel), NSStringFromClass(self)]);
    return [super resolveClassMethod:sel];
}
@end

#define DB_EMPTY_CLASS(name) @interface name : DBCompatObject @end @implementation name @end

DB_EMPTY_CLASS(NSAlert)
DB_EMPTY_CLASS(NSBitmapImageRep)

__attribute__((objc_runtime_name("DBShimNSColor")))
@interface NSColor : DBCompatObject
@property(nonatomic, strong) UIColor *dbColor;
+ (instancetype)blackColor;
+ (instancetype)whiteColor;
+ (instancetype)clearColor;
+ (instancetype)redColor;
+ (instancetype)greenColor;
+ (instancetype)blueColor;
+ (instancetype)grayColor;
+ (instancetype)lightGrayColor;
+ (instancetype)darkGrayColor;
+ (instancetype)colorWithCalibratedRed:(CGFloat)red green:(CGFloat)green blue:(CGFloat)blue alpha:(CGFloat)alpha;
+ (instancetype)colorWithDeviceRed:(CGFloat)red green:(CGFloat)green blue:(CGFloat)blue alpha:(CGFloat)alpha;
- (CGColorRef)CGColor;
@end

@implementation NSColor
+ (instancetype)db_wrap:(UIColor *)color {
    NSColor *value = [NSColor new];
    value.dbColor = color;
    return value;
}
+ (instancetype)blackColor { return [self db_wrap:UIColor.blackColor]; }
+ (instancetype)whiteColor { return [self db_wrap:UIColor.whiteColor]; }
+ (instancetype)clearColor { return [self db_wrap:UIColor.clearColor]; }
+ (instancetype)redColor { return [self db_wrap:UIColor.redColor]; }
+ (instancetype)greenColor { return [self db_wrap:UIColor.greenColor]; }
+ (instancetype)blueColor { return [self db_wrap:UIColor.blueColor]; }
+ (instancetype)grayColor { return [self db_wrap:UIColor.grayColor]; }
+ (instancetype)lightGrayColor { return [self db_wrap:UIColor.lightGrayColor]; }
+ (instancetype)darkGrayColor { return [self db_wrap:UIColor.darkGrayColor]; }
+ (instancetype)colorWithCalibratedRed:(CGFloat)red green:(CGFloat)green blue:(CGFloat)blue alpha:(CGFloat)alpha {
    return [self db_wrap:[UIColor colorWithRed:red green:green blue:blue alpha:alpha]];
}
+ (instancetype)colorWithDeviceRed:(CGFloat)red green:(CGFloat)green blue:(CGFloat)blue alpha:(CGFloat)alpha {
    return [self colorWithCalibratedRed:red green:green blue:blue alpha:alpha];
}
- (CGColorRef)CGColor { return self.dbColor.CGColor; }
@end

__attribute__((objc_runtime_name("DBShimNSFont")))
@interface NSFont : DBCompatObject
@property(nonatomic, strong) UIFont *dbFont;
+ (instancetype)systemFontOfSize:(CGFloat)size;
+ (instancetype)boldSystemFontOfSize:(CGFloat)size;
+ (instancetype)fontWithName:(NSString *)name size:(CGFloat)size;
- (CGFloat)pointSize;
@end

@implementation NSFont
+ (instancetype)db_wrap:(UIFont *)font {
    NSFont *value = [NSFont new];
    value.dbFont = font;
    return value;
}
+ (instancetype)systemFontOfSize:(CGFloat)size { return [self db_wrap:[UIFont systemFontOfSize:size]]; }
+ (instancetype)boldSystemFontOfSize:(CGFloat)size { return [self db_wrap:[UIFont boldSystemFontOfSize:size]]; }
+ (instancetype)fontWithName:(NSString *)name size:(CGFloat)size {
    UIFont *font = [UIFont fontWithName:name size:size] ?: [UIFont systemFontOfSize:size];
    return [self db_wrap:font];
}
- (CGFloat)pointSize { return self.dbFont.pointSize; }
@end

@interface NSBezierPath : DBCompatObject
@property(nonatomic, strong) UIBezierPath *dbPath;
+ (instancetype)bezierPath;
- (void)moveToPoint:(CGPoint)point;
- (void)lineToPoint:(CGPoint)point;
- (void)closePath;
@end
@implementation NSBezierPath
+ (instancetype)bezierPath {
    NSBezierPath *value = [NSBezierPath new];
    value.dbPath = [UIBezierPath bezierPath];
    return value;
}
- (void)moveToPoint:(CGPoint)point { [self.dbPath moveToPoint:point]; }
- (void)lineToPoint:(CGPoint)point { [self.dbPath addLineToPoint:point]; }
- (void)closePath { [self.dbPath closePath]; }
@end

DB_EMPTY_CLASS(NSWorkspace)
DB_EMPTY_CLASS(NSRunningApplication)
DB_EMPTY_CLASS(NSColorSpace)
DB_EMPTY_CLASS(NSCursor)
DB_EMPTY_CLASS(NSEvent)
DB_EMPTY_CLASS(NSFontManager)
DB_EMPTY_CLASS(NSGraphicsContext)
DB_EMPTY_CLASS(NSImage)
DB_EMPTY_CLASS(NSMenu)
DB_EMPTY_CLASS(NSMenuItem)
DB_EMPTY_CLASS(NSOpenPanel)
DB_EMPTY_CLASS(NSScreen)
DB_EMPTY_CLASS(NSView)
DB_EMPTY_CLASS(NSWindow)
DB_EMPTY_CLASS(SBApplication)
DB_EMPTY_CLASS(NSAppleEventManager)

__attribute__((objc_runtime_name("DBShimNSTask")))
@interface NSTask : DBCompatObject
@property(copy) NSString *launchPath;
@property(copy) NSArray<NSString *> *arguments;
@property(copy) NSDictionary<NSString *, NSString *> *environment;
@property(strong) id standardInput;
@property(strong) id standardOutput;
@property(strong) id standardError;
@property(copy) NSURL *executableURL;
@property(copy) NSArray<NSString *> *currentDirectoryPathComponents;
+ (instancetype)launchedTaskWithLaunchPath:(NSString *)path arguments:(NSArray<NSString *> *)arguments;
- (void)launch;
- (void)launchAndReturnError:(NSError **)error;
- (void)terminate;
- (void)interrupt;
- (void)suspend;
- (void)resume;
- (void)waitUntilExit;
- (BOOL)isRunning;
- (int)terminationStatus;
- (int)processIdentifier;
@end

@implementation NSTask {
    BOOL _dbRunning;
    int _dbTerminationStatus;
}
+ (instancetype)launchedTaskWithLaunchPath:(NSString *)path arguments:(NSArray<NSString *> *)arguments {
    NSTask *task = [NSTask new];
    task.launchPath = path;
    task.arguments = arguments;
    [task launch];
    return task;
}
- (void)launch {
    _dbRunning = NO;
    _dbTerminationStatus = 0;
    DBLog([NSString stringWithFormat:@"NSTask launch suppressed path=%@ args=%@", self.launchPath ?: self.executableURL.path, self.arguments]);
}
- (void)launchAndReturnError:(NSError **)error {
    if (error) *error = nil;
    [self launch];
}
- (void)terminate { _dbRunning = NO; }
- (void)interrupt { _dbRunning = NO; }
- (void)suspend {}
- (void)resume {}
- (void)waitUntilExit {}
- (BOOL)isRunning { return _dbRunning; }
- (int)terminationStatus { return _dbTerminationStatus; }
- (int)processIdentifier { return 0; }
@end

@interface DBNSHTTPURLResponseShim : NSURLResponse
- (instancetype)initWithURL:(NSURL *)URL
                 statusCode:(NSInteger)statusCode
                HTTPVersion:(NSString *)HTTPVersion
               headerFields:(NSDictionary<NSString *, NSString *> *)headerFields;
+ (NSString *)localizedStringForStatusCode:(NSInteger)statusCode;
- (NSInteger)statusCode;
- (NSDictionary *)allHeaderFields;
@end

@implementation DBNSHTTPURLResponseShim {
    NSInteger _dbStatusCode;
    NSDictionary *_dbHeaderFields;
}
- (instancetype)initWithURL:(NSURL *)URL
                 statusCode:(NSInteger)statusCode
                HTTPVersion:(NSString *)HTTPVersion
               headerFields:(NSDictionary<NSString *,NSString *> *)headerFields {
    self = [super initWithURL:URL MIMEType:nil expectedContentLength:-1 textEncodingName:nil];
    if (self) {
        _dbStatusCode = statusCode;
        _dbHeaderFields = [headerFields copy] ?: @{};
        DBLog([NSString stringWithFormat:@"NSHTTPURLResponse shim status=%ld", (long)statusCode]);
    }
    return self;
}
+ (NSString *)localizedStringForStatusCode:(NSInteger)statusCode {
    return [NSString stringWithFormat:@"HTTP %ld", (long)statusCode];
}
- (NSInteger)statusCode { return _dbStatusCode; }
- (NSDictionary *)allHeaderFields { return _dbHeaderFields ?: @{}; }
@end

@interface NSApplication : DBCompatObject
+ (instancetype)sharedApplication;
- (void)run;
- (void)terminate:(id)sender;
- (void)finishLaunching;
- (BOOL)isRunning;
- (void)activateIgnoringOtherApps:(BOOL)flag;
@end

static volatile BOOL DBNSApplicationShouldRun = YES;

@implementation NSApplication
+ (instancetype)sharedApplication {
    static NSApplication *app;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{ app = [NSApplication new]; });
    DBLog(@"NSApplication sharedApplication");
    return app;
}
- (void)finishLaunching {
    DBLog(@"checkpoint=nsapplication-finish-launching");
    DBShowRuntimeStatus(@"NSApplication.finishLaunching");
}
- (BOOL)isRunning {
    return DBNSApplicationShouldRun;
}
- (void)activateIgnoringOtherApps:(BOOL)flag {
    DBLog([NSString stringWithFormat:@"NSApplication activateIgnoringOtherApps=%@", flag ? @"YES" : @"NO"]);
    DBShowRuntimeStatus(@"NSApplication activated");
}
- (void)run {
    DBLog(@"checkpoint=nsapplication-run-enter");
    DBShowRuntimeStatus(@"NSApplication.run reached");
    DBScheduleRuntimeWatchdog();
    DBNSApplicationShouldRun = YES;

    // The original macOS executable expects NSApplication.run to own an event
    // loop. Returning immediately makes main unwind before queued UIKit/CEF
    // bootstrap work can execute. LiveContainer already supplies the UIKit
    // process; keep the guest thread/run loop alive without invoking a second
    // UIApplicationMain.
    NSRunLoop *loop = [NSRunLoop currentRunLoop];
    while (DBNSApplicationShouldRun) {
        @autoreleasepool {
            NSDate *until = [NSDate dateWithTimeIntervalSinceNow:0.05];
            [loop runMode:NSDefaultRunLoopMode beforeDate:until];
        }
    }
    DBLog(@"checkpoint=nsapplication-run-exit");
}
- (void)terminate:(id)sender {
    DBLog(@"NSApplication terminate requested");
    DBNSApplicationShouldRun = NO;
    CFRunLoopStop(CFRunLoopGetCurrent());
}
@end


// Native Objective-C symbols must be re-exported by the linker. A Class
// variable has an extra level of indirection and is NOT an objc_class object.
// In particular it cannot be used as a superclass or a constant-string isa.

// CFNetwork on macOS exposes a number of real data globals that older/native
// clients bind by dylib ordinal.  iOS may provide them from a different image
// or omit the legacy export entirely, so keep compatible exports in the
// DarwinBridge CFNetwork alias. Values match Apple's historical CFNetwork ABI.
__attribute__((visibility("default"), used))
const SInt32 DB_kCFStreamErrorDomainFTP __asm__("_kCFStreamErrorDomainFTP") = 6;
__attribute__((visibility("default"), used))
const SInt32 DB_kCFStreamErrorDomainHTTP __asm__("_kCFStreamErrorDomainHTTP") = 4;
__attribute__((visibility("default"), used))
const SInt32 DB_kCFStreamErrorDomainNetDB __asm__("_kCFStreamErrorDomainNetDB") = 12;
__attribute__((visibility("default"), used))
const SInt32 DB_kCFStreamErrorDomainSystemConfiguration __asm__("_kCFStreamErrorDomainSystemConfiguration") = 13;
__attribute__((visibility("default"), used))
const SInt32 DB_kCFStreamErrorDomainMach __asm__("_kCFStreamErrorDomainMach") = 11;
__attribute__((visibility("default"), used))
const SInt32 DB_kCFStreamErrorDomainNetServices __asm__("_kCFStreamErrorDomainNetServices") = 10;
__attribute__((visibility("default"), used))
const CFIndex DB_kCFStreamErrorDomainWinSock __asm__("_kCFStreamErrorDomainWinSock") = 7;
__attribute__((visibility("default"), used))
const int DB_kCFStreamErrorDomainSOCKS __asm__("_kCFStreamErrorDomainSOCKS") = 5;
__attribute__((visibility("default"), used))
const int DB_kCFStreamErrorDomainSSL __asm__("_kCFStreamErrorDomainSSL") = 3;

__attribute__((visibility("default"), used))
const CFStringRef DB_kCFErrorDomainCFNetwork __asm__("_kCFErrorDomainCFNetwork") = CFSTR("kCFErrorDomainCFNetwork");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFHTTPVersion1_0 __asm__("_kCFHTTPVersion1_0") = CFSTR("HTTP/1.0");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFHTTPVersion1_1 __asm__("_kCFHTTPVersion1_1") = CFSTR("HTTP/1.1");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFProxyHostNameKey __asm__("_kCFProxyHostNameKey") = CFSTR("kCFProxyHostNameKey");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFProxyPortNumberKey __asm__("_kCFProxyPortNumberKey") = CFSTR("kCFProxyPortNumberKey");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamNetworkServiceType __asm__("_kCFStreamNetworkServiceType") = CFSTR("kCFStreamNetworkServiceType");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamNetworkServiceTypeVoIP __asm__("_kCFStreamNetworkServiceTypeVoIP") = CFSTR("kCFStreamNetworkServiceTypeVoIP");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamPropertyHTTPProxy __asm__("_kCFStreamPropertyHTTPProxy") = CFSTR("kCFStreamPropertyHTTPProxy");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamPropertyHTTPResponseHeader __asm__("_kCFStreamPropertyHTTPResponseHeader") = CFSTR("kCFStreamPropertyHTTPResponseHeader");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamPropertyHTTPShouldAutoredirect __asm__("_kCFStreamPropertyHTTPShouldAutoredirect") = CFSTR("kCFStreamPropertyHTTPShouldAutoredirect");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamPropertySSLSettings __asm__("_kCFStreamPropertySSLSettings") = CFSTR("kCFStreamPropertySSLSettings");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamSSLAllowsAnyRoot __asm__("_kCFStreamSSLAllowsAnyRoot") = CFSTR("kCFStreamSSLAllowsAnyRoot");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamSSLAllowsExpiredCertificates __asm__("_kCFStreamSSLAllowsExpiredCertificates") = CFSTR("kCFStreamSSLAllowsExpiredCertificates");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamSSLAllowsExpiredRoots __asm__("_kCFStreamSSLAllowsExpiredRoots") = CFSTR("kCFStreamSSLAllowsExpiredRoots");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamSSLCertificates __asm__("_kCFStreamSSLCertificates") = CFSTR("kCFStreamSSLCertificates");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamSSLIsServer __asm__("_kCFStreamSSLIsServer") = CFSTR("kCFStreamSSLIsServer");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamSSLLevel __asm__("_kCFStreamSSLLevel") = CFSTR("kCFStreamSSLLevel");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamSSLPeerName __asm__("_kCFStreamSSLPeerName") = CFSTR("kCFStreamSSLPeerName");
__attribute__((visibility("default"), used))
const CFStringRef DB_kCFStreamSSLValidatesCertificateChain __asm__("_kCFStreamSSLValidatesCertificateChain") = CFSTR("kCFStreamSSLValidatesCertificateChain");


// Security.framework compatibility globals.  Keep the historical wire values
// used by SecItem dictionaries so macOS-linked clients can bind through the
// DBSecurity ordinal without depending on where modern iOS exports them.
#define DB_SECSTR(sym, value) \
    __attribute__((visibility("default"), used, weak)) \
    const CFStringRef sym = CFSTR(value)

DB_SECSTR(kSecClass, "class");
DB_SECSTR(kSecClassCertificate, "cert");
DB_SECSTR(kSecClassGenericPassword, "genp");
DB_SECSTR(kSecClassIdentity, "idnt");
DB_SECSTR(kSecClassInternetPassword, "inet");
DB_SECSTR(kSecClassKey, "keys");

DB_SECSTR(kSecAttrAccount, "acct");
DB_SECSTR(kSecAttrService, "svce");
DB_SECSTR(kSecAttrAccessGroup, "agrp");
DB_SECSTR(kSecAttrLabel, "labl");
DB_SECSTR(kSecAttrDescription, "desc");
DB_SECSTR(kSecAttrComment, "icmt");
DB_SECSTR(kSecAttrCreator, "crtr");
DB_SECSTR(kSecAttrType, "type");
DB_SECSTR(kSecAttrCreationDate, "cdat");
DB_SECSTR(kSecAttrModificationDate, "mdat");
DB_SECSTR(kSecAttrGeneric, "gena");
DB_SECSTR(kSecAttrSynchronizable, "sync");
DB_SECSTR(kSecAttrSynchronizableAny, "syna");

DB_SECSTR(kSecMatchLimit, "m_Limit");
DB_SECSTR(kSecMatchLimitAll, "m_LimitAll");
DB_SECSTR(kSecMatchLimitOne, "m_LimitOne");

DB_SECSTR(kSecReturnData, "r_Data");
DB_SECSTR(kSecReturnAttributes, "r_Attributes");
DB_SECSTR(kSecReturnPersistentRef, "r_PersistentRef");
DB_SECSTR(kSecReturnRef, "r_Ref");

DB_SECSTR(kSecValueData, "v_Data");
DB_SECSTR(kSecValueRef, "v_Ref");
DB_SECSTR(kSecValuePersistentRef, "v_PersistentRef");

DB_SECSTR(kSecAttrAccessControl, "accc");
DB_SECSTR(kSecAttrAccessGroupToken, "com.apple.token");
DB_SECSTR(kSecAttrAuthenticationType, "atyp");
DB_SECSTR(kSecAttrAuthenticationTypeDPA, "dpaa");
DB_SECSTR(kSecAttrAuthenticationTypeDefault, "dflt");
DB_SECSTR(kSecAttrAuthenticationTypeHTMLForm, "form");
DB_SECSTR(kSecAttrAuthenticationTypeHTTPBasic, "http");
DB_SECSTR(kSecAttrAuthenticationTypeHTTPDigest, "httd");
DB_SECSTR(kSecAttrAuthenticationTypeMSN, "msna");
DB_SECSTR(kSecAttrAuthenticationTypeNTLM, "ntlm");
DB_SECSTR(kSecAttrAuthenticationTypeRPA, "rpaa");
DB_SECSTR(kSecAttrCertificateEncoding, "cenc");
DB_SECSTR(kSecAttrCertificateType, "ctyp");
DB_SECSTR(kSecAttrIsExtractable, "extr");
DB_SECSTR(kSecAttrIsInvisible, "invi");
DB_SECSTR(kSecAttrIsNegative, "nega");
DB_SECSTR(kSecAttrIsSensitive, "sens");
DB_SECSTR(kSecAttrIssuer, "issr");
DB_SECSTR(kSecAttrKeyTypeEC, "73");
DB_SECSTR(kSecAttrKeyTypeECSECPrimeRandom, "73");
DB_SECSTR(kSecAttrPath, "path");
DB_SECSTR(kSecAttrPort, "port");
DB_SECSTR(kSecAttrProtocol, "ptcl");
DB_SECSTR(kSecAttrProtocolAFP, "afp ");
DB_SECSTR(kSecAttrProtocolAppleTalk, "atlk");
DB_SECSTR(kSecAttrProtocolDAAP, "daap");
DB_SECSTR(kSecAttrProtocolEPPC, "eppc");
DB_SECSTR(kSecAttrProtocolFTP, "ftp ");
DB_SECSTR(kSecAttrProtocolFTPAccount, "ftpa");
DB_SECSTR(kSecAttrProtocolFTPProxy, "ftpx");
DB_SECSTR(kSecAttrProtocolFTPS, "ftps");
DB_SECSTR(kSecAttrProtocolHTTP, "http");
DB_SECSTR(kSecAttrProtocolHTTPProxy, "htpx");
DB_SECSTR(kSecAttrProtocolHTTPS, "htps");
DB_SECSTR(kSecAttrProtocolHTTPSProxy, "htsx");
DB_SECSTR(kSecAttrProtocolIMAP, "imap");
DB_SECSTR(kSecAttrProtocolIMAPS, "imps");
DB_SECSTR(kSecAttrProtocolIPP, "ipp ");
DB_SECSTR(kSecAttrProtocolIRC, "irc ");
DB_SECSTR(kSecAttrProtocolIRCS, "ircs");
DB_SECSTR(kSecAttrProtocolLDAP, "ldap");
DB_SECSTR(kSecAttrProtocolLDAPS, "ldps");
DB_SECSTR(kSecAttrProtocolNNTP, "nntp");
DB_SECSTR(kSecAttrProtocolNNTPS, "ntps");
DB_SECSTR(kSecAttrProtocolPOP3, "pop3");
DB_SECSTR(kSecAttrProtocolPOP3S, "pops");
DB_SECSTR(kSecAttrProtocolRTSP, "rtsp");
DB_SECSTR(kSecAttrProtocolRTSPProxy, "rtsx");
DB_SECSTR(kSecAttrProtocolSMB, "smb ");
DB_SECSTR(kSecAttrProtocolSMTP, "smtp");
DB_SECSTR(kSecAttrProtocolSOCKS, "sox ");
DB_SECSTR(kSecAttrProtocolSSH, "ssh ");
DB_SECSTR(kSecAttrProtocolTelnet, "teln");
DB_SECSTR(kSecAttrProtocolTelnetS, "tels");
DB_SECSTR(kSecAttrPublicKeyHash, "pkhh");
DB_SECSTR(kSecAttrSecurityDomain, "sdmn");
DB_SECSTR(kSecAttrSerialNumber, "slnr");
DB_SECSTR(kSecAttrServer, "srvr");
DB_SECSTR(kSecAttrSubject, "subj");
DB_SECSTR(kSecAttrSubjectKeyID, "skid");
DB_SECSTR(kSecAttrSyncViewHint, "vwht");
DB_SECSTR(kSecAttrTokenID, "tkid");
DB_SECSTR(kSecAttrTokenIDSecureEnclave, "com.apple.setoken");

DB_SECSTR(kSecImportExportPassphrase, "passphrase");
DB_SECSTR(kSecImportItemCertChain, "chain");
DB_SECSTR(kSecImportItemIdentity, "identity");
DB_SECSTR(kSecImportItemKeyID, "keyid");
DB_SECSTR(kSecImportItemLabel, "label");
DB_SECSTR(kSecImportItemTrust, "trust");

DB_SECSTR(kSecMatchCaseInsensitive, "m_CaseInsensitive");
DB_SECSTR(kSecMatchEmailAddressIfPresent, "m_EmailAddressIfPresent");
DB_SECSTR(kSecMatchIssuers, "m_Issuers");
DB_SECSTR(kSecMatchItemList, "m_ItemList");
DB_SECSTR(kSecMatchPolicy, "m_Policy");
DB_SECSTR(kSecMatchSearchList, "m_SearchList");
DB_SECSTR(kSecMatchSubjectContains, "m_SubjectContains");
DB_SECSTR(kSecMatchTrustedOnly, "m_TrustedOnly");
DB_SECSTR(kSecMatchValidOnDate, "m_ValidOnDate");

DB_SECSTR(kSecUseAuthenticationContext, "u_AuthCtx");
DB_SECSTR(kSecUseAuthenticationUI, "u_AuthUI");
DB_SECSTR(kSecUseAuthenticationUIAllow, "u_AuthUIA");
DB_SECSTR(kSecUseAuthenticationUIFail, "u_AuthUIF");
DB_SECSTR(kSecUseAuthenticationUISkip, "u_AuthUIS");
DB_SECSTR(kSecUseItemList, "u_ItemList");

DB_SECSTR(kSecPolicyAppleCodeSigning, "1.2.840.113635.100.1.16");
DB_SECSTR(kSecPolicyAppleEAP, "1.2.840.113635.100.1.9");
DB_SECSTR(kSecPolicyAppleIDValidation, "1.2.840.113635.100.1.18");
DB_SECSTR(kSecPolicyAppleIPsec, "1.2.840.113635.100.1.11");
DB_SECSTR(kSecPolicyApplePKINITClient, "1.2.840.113635.100.1.14");
DB_SECSTR(kSecPolicyApplePKINITServer, "1.2.840.113635.100.1.15");
DB_SECSTR(kSecPolicyApplePassbookSigning, "1.2.840.113635.100.1.22");
DB_SECSTR(kSecPolicyApplePayIssuerEncryption, "1.2.840.113635.100.1.39");
DB_SECSTR(kSecPolicyAppleRevocation, "1.2.840.113635.100.1.21");
DB_SECSTR(kSecPolicyAppleSMIME, "1.2.840.113635.100.1.8");
DB_SECSTR(kSecPolicyAppleSSL, "1.2.840.113635.100.1.3");
DB_SECSTR(kSecPolicyAppleTimeStamping, "1.2.840.113635.100.1.20");
DB_SECSTR(kSecPolicyAppleX509Basic, "1.2.840.113635.100.1.2");
DB_SECSTR(kSecPolicyClient, "SecPolicyClient");
DB_SECSTR(kSecPolicyMacAppStoreReceipt, "1.2.840.113635.100.1.19");
DB_SECSTR(kSecPolicyName, "SecPolicyName");
DB_SECSTR(kSecPolicyOid, "SecPolicyOid");
DB_SECSTR(kSecPolicyRevocationFlags, "SecPolicyRevocationFlags");
DB_SECSTR(kSecPolicyTeamIdentifier, "SecPolicyTeamIdentifier");

DB_SECSTR(kSecPrivateKeyAttrs, "private");
DB_SECSTR(kSecPublicKeyAttrs, "public");
DB_SECSTR(kSecPropertyTypeError, "error");
DB_SECSTR(kSecPropertyTypeTitle, "title");
DB_SECSTR(kSecSharedPassword, "spwd");

DB_SECSTR(kSecTrustCertificateTransparency, "TrustCertificateTransparency");
DB_SECSTR(kSecTrustCertificateTransparencyWhiteList, "TrustCertificateTransparencyWhiteList");
DB_SECSTR(kSecTrustEvaluationDate, "TrustEvaluationDate");
DB_SECSTR(kSecTrustInfoExtendedValidationKey, "ExtendedValidation");
DB_SECSTR(kSecTrustExtendedValidation, "TrustExtendedValidation");
DB_SECSTR(kSecTrustOrganizationName, "Organization");
DB_SECSTR(kSecTrustResultValue, "TrustResultValue");
DB_SECSTR(kSecTrustRevocationChecked, "TrustRevocationChecked");
DB_SECSTR(kSecTrustRevocationValidUntilDate, "TrustExpirationDate");

#undef DB_SECSTR


// Foundation data constants frequently bind to the Foundation dylib ordinal.
// Export the common URL/error/defaults/stream globals through DBFoundation.
#define DB_FNDSTR(sym, value) \
    __attribute__((visibility("default"), used, weak)) \
    NSString * const sym = @value

DB_FNDSTR(NSURLErrorDomain, "NSURLErrorDomain");
DB_FNDSTR(NSURLErrorKey, "NSURL");
DB_FNDSTR(NSPOSIXErrorDomain, "NSPOSIXErrorDomain");
DB_FNDSTR(NSOSStatusErrorDomain, "NSOSStatusErrorDomain");
DB_FNDSTR(NSMachErrorDomain, "NSMachErrorDomain");
DB_FNDSTR(NSCocoaErrorDomain, "NSCocoaErrorDomain");
DB_FNDSTR(NSUnderlyingErrorKey, "NSUnderlyingError");
DB_FNDSTR(NSLocalizedDescriptionKey, "NSLocalizedDescription");
DB_FNDSTR(NSLocalizedFailureReasonErrorKey, "NSLocalizedFailureReason");
DB_FNDSTR(NSLocalizedRecoverySuggestionErrorKey, "NSLocalizedRecoverySuggestion");
DB_FNDSTR(NSLocalizedRecoveryOptionsErrorKey, "NSLocalizedRecoveryOptions");
DB_FNDSTR(NSRecoveryAttempterErrorKey, "NSRecoveryAttempter");
DB_FNDSTR(NSHelpAnchorErrorKey, "NSHelpAnchor");
DB_FNDSTR(NSStringEncodingErrorKey, "NSStringEncodingErrorKey");
DB_FNDSTR(NSFilePathErrorKey, "NSFilePathErrorKey");
DB_FNDSTR(NSErrorFailingURLStringKey, "NSErrorFailingURLStringKey");
DB_FNDSTR(NSURLErrorFailingURLPeerTrustErrorKey, "NSURLErrorFailingURLPeerTrustErrorKey");
DB_FNDSTR(NSURLErrorBackgroundTaskCancelledReasonKey, "NSURLErrorBackgroundTaskCancelledReasonKey");

DB_FNDSTR(NSGlobalDomain, "NSGlobalDomain");
DB_FNDSTR(NSArgumentDomain, "NSArgumentDomain");
DB_FNDSTR(NSRegistrationDomain, "NSRegistrationDomain");
DB_FNDSTR(NSUserDefaultsDidChangeNotification, "NSUserDefaultsDidChangeNotification");
DB_FNDSTR(NSUserDefaultsSizeLimitExceededNotification, "NSUserDefaultsSizeLimitExceededNotification");

DB_FNDSTR(NSFileType, "NSFileType");
DB_FNDSTR(NSFileSize, "NSFileSize");
DB_FNDSTR(NSFileModificationDate, "NSFileModificationDate");
DB_FNDSTR(NSURLFileScheme, "file");

DB_FNDSTR(NSStreamSocketSecurityLevelKey, "NSStreamSocketSecurityLevelKey");
DB_FNDSTR(NSStreamSocketSecurityLevelNone, "NSStreamSocketSecurityLevelNone");
DB_FNDSTR(NSStreamSocketSecurityLevelSSLv2, "NSStreamSocketSecurityLevelSSLv2");
DB_FNDSTR(NSStreamSocketSecurityLevelSSLv3, "NSStreamSocketSecurityLevelSSLv3");
DB_FNDSTR(NSStreamSocketSecurityLevelTLSv1, "NSStreamSocketSecurityLevelTLSv1");
DB_FNDSTR(NSStreamSocketSecurityLevelNegotiatedSSL, "NSStreamSocketSecurityLevelNegotiatedSSL");
DB_FNDSTR(NSStreamSOCKSProxyConfigurationKey, "NSStreamSOCKSProxyConfigurationKey");
DB_FNDSTR(NSStreamSOCKSProxyHostKey, "NSStreamSOCKSProxyHostKey");
DB_FNDSTR(NSStreamSOCKSProxyPortKey, "NSStreamSOCKSProxyPortKey");
DB_FNDSTR(NSStreamSOCKSProxyVersionKey, "NSStreamSOCKSProxyVersionKey");
DB_FNDSTR(NSStreamSOCKSProxyUserKey, "NSStreamSOCKSProxyUserKey");
DB_FNDSTR(NSStreamSOCKSProxyPasswordKey, "NSStreamSOCKSProxyPasswordKey");
DB_FNDSTR(NSStreamSOCKSProxyVersion4, "NSStreamSOCKSProxyVersion4");
DB_FNDSTR(NSStreamSOCKSProxyVersion5, "NSStreamSOCKSProxyVersion5");
DB_FNDSTR(NSStreamSocketSSLErrorDomain, "NSStreamSocketSSLErrorDomain");
DB_FNDSTR(NSStreamSOCKSErrorDomain, "NSStreamSOCKSErrorDomain");
DB_FNDSTR(NSStreamNetworkServiceType, "NSStreamNetworkServiceType");
DB_FNDSTR(NSStreamNetworkServiceTypeVoIP, "NSStreamNetworkServiceTypeVoIP");
DB_FNDSTR(NSStreamNetworkServiceTypeVideo, "NSStreamNetworkServiceTypeVideo");
DB_FNDSTR(NSStreamNetworkServiceTypeBackground, "NSStreamNetworkServiceTypeBackground");
DB_FNDSTR(NSStreamNetworkServiceTypeVoice, "NSStreamNetworkServiceTypeVoice");
DB_FNDSTR(NSStreamNetworkServiceTypeCallSignaling, "NSStreamNetworkServiceTypeCallSignaling");

#undef DB_FNDSTR


// macOS Foundation exports the NSGeometry zero constants as data symbols.
// iOS uses the CoreGraphics geometry ABI, which is layout-compatible on arm64.
__attribute__((visibility("default"), used, weak))
const CGPoint NSZeroPoint = {0.0, 0.0};
__attribute__((visibility("default"), used, weak))
const CGSize NSZeroSize = {0.0, 0.0};
__attribute__((visibility("default"), used, weak))
const CGRect NSZeroRect = {{0.0, 0.0}, {0.0, 0.0}};


// macOS AppKit attributed-string/document constants required by the LoL client.
// These historical data symbols are not exported from UIKit under the same
// AppKit dylib ordinal, so DBAppKit provides stable compatibility values.
#define DB_APPKITSTR(sym, value) \
    __attribute__((visibility("default"), used)) \
    NSString * const sym = @value

DB_APPKITSTR(NSAttachmentAttributeName, "NSAttachment");
DB_APPKITSTR(NSBackgroundColorAttributeName, "NSBackgroundColor");
DB_APPKITSTR(NSBaselineOffsetAttributeName, "NSBaselineOffset");
DB_APPKITSTR(NSExpansionAttributeName, "NSExpansion");
DB_APPKITSTR(NSFontAttributeName, "NSFont");
DB_APPKITSTR(NSForegroundColorAttributeName, "NSColor");
DB_APPKITSTR(NSKernAttributeName, "NSKern");
DB_APPKITSTR(NSLigatureAttributeName, "NSLigature");
DB_APPKITSTR(NSLinkAttributeName, "NSLink");
DB_APPKITSTR(NSObliquenessAttributeName, "NSObliqueness");
DB_APPKITSTR(NSParagraphStyleAttributeName, "NSParagraphStyle");
DB_APPKITSTR(NSShadowAttributeName, "NSShadow");
DB_APPKITSTR(NSStrikethroughColorAttributeName, "NSStrikethroughColor");
DB_APPKITSTR(NSStrikethroughStyleAttributeName, "NSStrikethrough");
DB_APPKITSTR(NSStrokeColorAttributeName, "NSStrokeColor");
DB_APPKITSTR(NSStrokeWidthAttributeName, "NSStrokeWidth");
DB_APPKITSTR(NSTextEffectAttributeName, "NSTextEffect");
DB_APPKITSTR(NSUnderlineColorAttributeName, "NSUnderlineColor");
DB_APPKITSTR(NSUnderlineStyleAttributeName, "NSUnderline");
DB_APPKITSTR(NSVerticalGlyphFormAttributeName, "NSVerticalGlyphForm");
DB_APPKITSTR(NSWritingDirectionAttributeName, "NSWritingDirection");

DB_APPKITSTR(NSDocumentTypeDocumentAttribute, "DocumentType");
DB_APPKITSTR(NSCharacterEncodingDocumentAttribute, "CharacterEncoding");
DB_APPKITSTR(NSDefaultAttributesDocumentAttribute, "DefaultAttributes");
DB_APPKITSTR(NSDefaultTabIntervalDocumentAttribute, "DefaultTabInterval");
DB_APPKITSTR(NSHyphenationFactorDocumentAttribute, "HyphenationFactor");
DB_APPKITSTR(NSPaperMarginDocumentAttribute, "PaperMargin");
DB_APPKITSTR(NSPaperSizeDocumentAttribute, "PaperSize");
DB_APPKITSTR(NSReadOnlyDocumentAttribute, "ReadOnly");
DB_APPKITSTR(NSViewModeDocumentAttribute, "ViewMode");
DB_APPKITSTR(NSViewSizeDocumentAttribute, "ViewSize");
DB_APPKITSTR(NSViewZoomDocumentAttribute, "ViewZoom");
DB_APPKITSTR(NSPlainTextDocumentType, "NSPlainText");
DB_APPKITSTR(NSRTFTextDocumentType, "NSRTF");
DB_APPKITSTR(NSRTFDTextDocumentType, "NSRTFD");
DB_APPKITSTR(NSHTMLTextDocumentType, "NSHTML");

#undef DB_APPKITSTR


// CoreFoundation/Foundation run-loop mode aliases used by macOS binaries.
__attribute__((visibility("default"), used))
NSString * const NSDefaultRunLoopMode = @"kCFRunLoopDefaultMode";
__attribute__((visibility("default"), used))
NSString * const NSRunLoopCommonModes = @"kCFRunLoopCommonModes";
__attribute__((visibility("default"), used))
const CFRunLoopMode kCFRunLoopDefaultMode = CFSTR("kCFRunLoopDefaultMode");
__attribute__((visibility("default"), used))
const CFRunLoopMode kCFRunLoopCommonModes = CFSTR("kCFRunLoopCommonModes");


// CoreFoundation legacy data constants.  Several macOS clients bind these
// through the CoreFoundation ordinal even when modern iOS exposes equivalent
// values from a different image.
#define DB_CFSTR(sym, value) \
    __attribute__((visibility("default"), used)) \
    const CFStringRef sym = CFSTR(value)

DB_CFSTR(kCFBundleExecutableKey, "CFBundleExecutable");
DB_CFSTR(kCFBundleInfoDictionaryVersionKey, "CFBundleInfoDictionaryVersion");
DB_CFSTR(kCFBundleIdentifierKey, "CFBundleIdentifier");
DB_CFSTR(kCFBundleVersionKey, "CFBundleVersion");
DB_CFSTR(kCFBundleDevelopmentRegionKey, "CFBundleDevelopmentRegion");
DB_CFSTR(kCFBundleNameKey, "CFBundleName");
DB_CFSTR(kCFBundleLocalizationsKey, "CFBundleLocalizations");

DB_CFSTR(kCFErrorDomainMach, "NSMachErrorDomain");
DB_CFSTR(kCFErrorDomainOSStatus, "NSOSStatusErrorDomain");
DB_CFSTR(kCFErrorDomainPOSIX, "NSPOSIXErrorDomain");
DB_CFSTR(kCFErrorDomainCocoa, "NSCocoaErrorDomain");
DB_CFSTR(kCFErrorDescriptionKey, "NSDescription");
DB_CFSTR(kCFErrorLocalizedDescriptionKey, "NSLocalizedDescription");
DB_CFSTR(kCFErrorLocalizedFailureReasonKey, "NSLocalizedFailureReason");
DB_CFSTR(kCFErrorLocalizedRecoverySuggestionKey, "NSLocalizedRecoverySuggestion");
DB_CFSTR(kCFErrorUnderlyingErrorKey, "NSUnderlyingError");
DB_CFSTR(kCFErrorURLKey, "NSURL");
DB_CFSTR(kCFErrorFilePathKey, "NSFilePath");

DB_CFSTR(kCFGregorianCalendar, "gregorian");
DB_CFSTR(kCFLocaleCountryCode, "kCFLocaleCountryCodeKey");
DB_CFSTR(kCFStringTransformStripCombiningMarks, ")kCFStringTransformStripCombiningMarks");
DB_CFSTR(kCFStringTransformToLatin, ")kCFStringTransformToLatin");

DB_CFSTR(kCFPreferencesAnyApplication, "kCFPreferencesAnyApplication");
DB_CFSTR(kCFPreferencesCurrentApplication, "kCFPreferencesCurrentApplication");
DB_CFSTR(kCFPreferencesAnyHost, "kCFPreferencesAnyHost");
DB_CFSTR(kCFPreferencesCurrentHost, "kCFPreferencesCurrentHost");
DB_CFSTR(kCFPreferencesAnyUser, "kCFPreferencesAnyUser");
DB_CFSTR(kCFPreferencesCurrentUser, "kCFPreferencesCurrentUser");

DB_CFSTR(kCFURLIsExcludedFromBackupKey, "NSURLIsExcludedFromBackupKey");
DB_CFSTR(kCFURLFileDirectoryContents, "kCFURLFileDirectoryContents");
DB_CFSTR(kCFURLFileExists, "kCFURLFileExists");

DB_CFSTR(kCFStreamPropertyShouldCloseNativeSocket, "kCFStreamPropertyShouldCloseNativeSocket");
DB_CFSTR(kCFStreamPropertySocketNativeHandle, "kCFStreamPropertySocketNativeHandle");
DB_CFSTR(kCFStreamPropertySOCKSPassword, "kCFStreamPropertySOCKSPassword");
DB_CFSTR(kCFStreamPropertySOCKSProxy, "kCFStreamPropertySOCKSProxy");
DB_CFSTR(kCFStreamPropertySOCKSProxyHost, "SOCKSProxy");
DB_CFSTR(kCFStreamPropertySOCKSProxyPort, "SOCKSPort");
DB_CFSTR(kCFStreamPropertySOCKSUser, "kCFStreamPropertySOCKSUser");
DB_CFSTR(kCFStreamPropertySOCKSVersion, "kCFStreamPropertySOCKSVersion");
DB_CFSTR(kCFStreamPropertySocketSecurityLevel, "kCFStreamPropertySocketSecurityLevel");
DB_CFSTR(kCFStreamSocketSOCKSVersion4, "kCFStreamSocketSOCKSVersion4");
DB_CFSTR(kCFStreamSocketSOCKSVersion5, "kCFStreamSocketSOCKSVersion5");
DB_CFSTR(kCFStreamSocketSecurityLevelNegotiatedSSL, "kCFStreamSocketSecurityLevelNegotiatedSSL");
DB_CFSTR(kCFStreamSocketSecurityLevelNone, "kCFStreamSocketSecurityLevelNone");
DB_CFSTR(kCFStreamSocketSecurityLevelSSLv2, "kCFStreamSocketSecurityLevelSSLv2");
DB_CFSTR(kCFStreamSocketSecurityLevelSSLv3, "kCFStreamSocketSecurityLevelSSLv3");
DB_CFSTR(kCFStreamSocketSecurityLevelTLSv1, "kCFStreamSocketSecurityLevelTLSv1");
DB_CFSTR(kCFStreamPropertyDataWritten, "kCFStreamPropertyDataWritten");

#undef DB_CFSTR

__attribute__((visibility("default"), used))
const CFTimeInterval kCFAbsoluteTimeIntervalSince1970 = 978307200.0;

// CoreFoundation locale constants commonly bound through the desktop
// CoreFoundation ordinal.
#define DB_CFLOCALESTR(sym, value) \
    __attribute__((visibility("default"), used)) \
    const CFStringRef sym = CFSTR(value)

__attribute__((visibility("default"), used))
const CFAllocatorRef kCFAllocatorDefault = NULL;
DB_CFLOCALESTR(kCFLocaleIdentifier, "kCFLocaleIdentifierKey");
DB_CFLOCALESTR(kCFLocaleLanguageCode, "kCFLocaleLanguageCodeKey");
DB_CFLOCALESTR(kCFLocaleScriptCode, "kCFLocaleScriptCodeKey");
DB_CFLOCALESTR(kCFLocaleVariantCode, "kCFLocaleVariantCodeKey");
DB_CFLOCALESTR(kCFLocaleExemplarCharacterSet, "kCFLocaleExemplarCharacterSetKey");
DB_CFLOCALESTR(kCFLocaleCalendarIdentifier, "calendar");
DB_CFLOCALESTR(kCFLocaleCalendar, "kCFLocaleCalendarKey");
DB_CFLOCALESTR(kCFLocaleCollationIdentifier, "collation");
DB_CFLOCALESTR(kCFLocaleUsesMetricSystem, "kCFLocaleUsesMetricSystemKey");
DB_CFLOCALESTR(kCFLocaleMeasurementSystem, "kCFLocaleMeasurementSystemKey");
DB_CFLOCALESTR(kCFLocaleDecimalSeparator, "kCFLocaleDecimalSeparatorKey");
DB_CFLOCALESTR(kCFLocaleGroupingSeparator, "kCFLocaleGroupingSeparatorKey");
DB_CFLOCALESTR(kCFLocaleCurrencySymbol, "kCFLocaleCurrencySymbolKey");
DB_CFLOCALESTR(kCFLocaleCurrencyCode, "currency");
DB_CFLOCALESTR(kCFLocaleCollatorIdentifier, "kCFLocaleCollatorIdentifierKey");
DB_CFLOCALESTR(kCFLocaleQuotationBeginDelimiterKey, "kCFLocaleQuotationBeginDelimiterKey");
DB_CFLOCALESTR(kCFLocaleQuotationEndDelimiterKey, "kCFLocaleQuotationEndDelimiterKey");
DB_CFLOCALESTR(kCFLocaleAlternateQuotationBeginDelimiterKey, "kCFLocaleAlternateQuotationBeginDelimiterKey");
DB_CFLOCALESTR(kCFLocaleAlternateQuotationEndDelimiterKey, "kCFLocaleAlternateQuotationEndDelimiterKey");

#undef DB_CFLOCALESTR


id NSApp = nil;
NSString *NSCalibratedRGBColorSpace = @"NSCalibratedRGBColorSpace";
NSString *NSDeviceRGBColorSpace = @"NSDeviceRGBColorSpace";
NSString *NSEventTrackingRunLoopMode = @"NSEventTrackingRunLoopMode";
NSString *NSModalPanelRunLoopMode = @"NSModalPanelRunLoopMode";

int32_t Gestalt(uint32_t selector, int32_t *response) {
    if (response) *response = 0;
    DBLog([NSString stringWithFormat:@"Gestalt selector=0x%08x", selector]);
    return 0;
}

void DBDarwinBridgeRuntimeCheckpoint(const char *phase, const char *detail) {
    NSString *p = phase ? [NSString stringWithUTF8String:phase] : @"unknown";
    NSString *d = detail ? [NSString stringWithUTF8String:detail] : @"";
    DBLog([NSString stringWithFormat:@"checkpoint %@ %@", p, d]);
}

const char *DBDarwinBridgePluginVersion(void) {
    return "0.2.0-stage21G-abi";
}

__attribute__((constructor))
static void DBDarwinBridgePluginInit(void) {
    DBInstallCrashDiagnostics();
    DBLog(@"checkpoint=plugin-constructor-begin");
    NSApp = [NSApplication sharedApplication];
    DBLog(@"compatibility plugin loaded");
    DBLog(@"checkpoint=plugin-constructor-complete");
    DBShowRuntimeStatus(@"plugin constructor complete");
    DBScheduleRuntimeWatchdog();
    DBLog([NSString stringWithFormat:@"UIKit=%@ MetalClassProbe=%@",
           NSStringFromClass(UIApplication.class),
           NSClassFromString(@"MTLDevice") ? @"yes" : @"no"]);
}

// iOS 26.2 runtime testing proved these names are already owned by UIKit /
// UIFoundation / Foundation. Give our existing shims distinct runtime names.
// class-aliases.txt supplies linker aliases to the actual objc_class objects;
// they are NOT pointer variables and require no constructor-time assignment.
// Keep aliases at link time: assembler .set aliases split ObjC metadata atoms
// and are rejected by Apple's modern linker with "null objc class data".
