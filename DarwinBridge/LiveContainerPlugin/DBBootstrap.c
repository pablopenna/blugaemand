// Deliberately no Objective-C, Foundation, UIKit or compatibility dependency.
// ObjC image validation can run before ANY dylib constructor, so the debugger
// remains the only observation path that covers a rejected image graph.
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <limits.h>
#include <errno.h>
#include <mach-o/dyld.h>

__attribute__((visibility("default")))
void DBBootstrapCheckpoint(const char *phase) {
    char path[PATH_MAX];
    const char *home = getenv("HOME");
    if (!home) home = getenv("CFFIXED_USER_HOME");
    if (!home) home = ".";
    int n = snprintf(path, sizeof(path), "%s/Documents/DarwinBridge-runtime.log", home);
    int fd = n > 0 && n < (int)sizeof(path) ? open(path, O_CREAT|O_WRONLY|O_APPEND, 0644) : -1;
    int saved = errno;
    dprintf(STDERR_FILENO, "[DBBootstrap 21G] %s pid=%d file=%s fd=%d errno=%d\n",
            phase, getpid(), path, fd, fd < 0 ? saved : 0);
    if (fd >= 0) {
        dprintf(fd, "[DBBootstrap 21G] %s pid=%d images=%u\n", phase, getpid(), _dyld_image_count());
        for (uint32_t i = 0; i < _dyld_image_count(); ++i) {
            const char *name = _dyld_get_image_name(i);
            dprintf(fd, "image=%p slide=%p %s\n", _dyld_get_image_header(i),
                    (void *)_dyld_get_image_vmaddr_slide(i), name ? name : "?");
        }
        fsync(fd);
        close(fd);
    }
}
__attribute__((constructor))
static void DBBootstrapInit(void) { DBBootstrapCheckpoint("bootstrap-constructor"); }
