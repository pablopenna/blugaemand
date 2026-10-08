#include <mach/mach.h>
#include <mach/vm_map.h>
#include <stdint.h>
#include <stddef.h>

extern void* DBJIT26PrepareRegion(void* address, size_t length);

int DBJIT26CreateDualMapping(size_t size, void** out_rx, void** out_rw) {
    if(!out_rx || !out_rw || size == 0) return -1;

    void* rx = DBJIT26PrepareRegion(NULL, size);
    uintptr_t value = (uintptr_t)rx;

    if(value == 0 || value == (uintptr_t)-1) return -2;
    if((value & 0x3FFFu) != 0) return -3;

    vm_address_t rw = 0;
    vm_prot_t cur = VM_PROT_NONE;
    vm_prot_t max = VM_PROT_NONE;

    kern_return_t kr = vm_remap(
        mach_task_self(),
        &rw,
        (vm_size_t)size,
        0,
        VM_FLAGS_ANYWHERE,
        mach_task_self(),
        (vm_address_t)rx,
        FALSE,
        &cur,
        &max,
        VM_INHERIT_NONE
    );
    if(kr != KERN_SUCCESS) return (int)kr;

    kr = vm_protect(
        mach_task_self(),
        rw,
        (vm_size_t)size,
        FALSE,
        VM_PROT_READ | VM_PROT_WRITE
    );
    if(kr != KERN_SUCCESS) {
        vm_deallocate(mach_task_self(), rw, (vm_size_t)size);
        return (int)kr;
    }

    *out_rx = rx;
    *out_rw = (void*)rw;
    return 0;
}
