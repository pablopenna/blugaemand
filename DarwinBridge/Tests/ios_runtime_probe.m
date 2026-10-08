#import <UIKit/UIKit.h>
#include <dlfcn.h>
#include <objc/runtime.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
@interface DBProbeDelegate : UIResponder <UIApplicationDelegate>
@property(nonatomic,strong) UIWindow *window;
@end
@implementation DBProbeDelegate
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)options {
    self.window=[[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
    UIViewController *vc=[UIViewController new];
    vc.view.backgroundColor=UIColor.systemBackgroundColor;
    self.window.rootViewController=vc; [self.window makeKeyAndVisible];
    const char *names[]={"DBAppKit.dylib","DBFoundation.dylib","DBCFNetwork.dylib"};
    void *implementation=NULL;
    for(int i=0;i<3;i++) {
        NSString *path=[NSBundle.mainBundle.bundlePath stringByAppendingPathComponent:[@"Frameworks/" stringByAppendingString:@(names[i])]];
        void *h=dlopen(path.UTF8String,RTLD_NOW|RTLD_LOCAL|RTLD_FIRST);
        if(!h){fprintf(stderr,"DB_PROBE_FAIL dlopen: %s\n",dlerror());exit(2);}
        if(dlsym(h,"OBJC_CLASS_$_NSObject") != (__bridge void *)NSObject.class ||
           dlsym(h,"OBJC_CLASS_$_NSString") != (__bridge void *)NSString.class) {
            fprintf(stderr,"DB_PROBE_FAIL native class identity\n");exit(3);
        }
        const char *aliases[]={"NSColor","NSFont","NSTask"};
        for(int j=0;j<3;j++) {
            char symbol[128], expected[128];
            snprintf(symbol,sizeof(symbol),"OBJC_CLASS_$_%s",aliases[j]);
            snprintf(expected,sizeof(expected),"DBShim%s",aliases[j]);
            Class cls=(__bridge Class)dlsym(h,symbol);
            if(!cls || strcmp(class_getName(cls),expected)!=0 ||
               cls==objc_getClass(aliases[j])) {
                fprintf(stderr,"DB_PROBE_FAIL class identity collision: %s\n",aliases[j]);exit(6);
            }
        }
        void *entry=dlsym(h,"DBDarwinBridgePluginVersion");
        if(!entry || (implementation && entry!=implementation)) {
            fprintf(stderr,"DB_PROBE_FAIL duplicate/missing implementation\n");exit(4);
        }
        implementation=entry;
    }
    NSString *path=[NSHomeDirectory() stringByAppendingPathComponent:@"Documents/DarwinBridge-runtime.log"];
    NSString *contents=[NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil];
    if(![contents containsString:@"bootstrap-constructor"] || ![contents containsString:@"plugin-constructor-complete"]) {
        fprintf(stderr,"DB_PROBE_FAIL initializer log missing\n");exit(5);
    }
    fprintf(stderr,"DB_IOS_RUNTIME_PROBE_PASS: native identities; one implementation; both constructors; UIKit main\n");
    fflush(stderr);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW,2*NSEC_PER_SEC),dispatch_get_main_queue(),^{ exit(0); });
    return YES;
}
@end
int main(int argc,char **argv) {
    fprintf(stderr,"DB_PROBE_MAIN_REACHED\n");
    @autoreleasepool { return UIApplicationMain(argc,argv,nil,NSStringFromClass(DBProbeDelegate.class)); }
}
