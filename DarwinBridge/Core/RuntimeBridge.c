#include <stdlib.h>
#include <string.h>
#include <stdint.h>

size_t DBRuntimeChain(const char* source) {
    if(!source) return 0;
    size_t len = strlen(source);
    char* copy = (char*)malloc(len + 1);
    if(!copy) return 0;
    memcpy(copy, source, len + 1);
    size_t result = strlen(copy);
    free(copy);
    return result;
}

void* DBRuntimeChainAddress(void) {
    return (void*)&DBRuntimeChain;
}


#include <stdatomic.h>

static _Atomic int gDBAppKitWindowRequest = 0;

int32_t DBAppKitRequestDemoWindow(void) {
    atomic_store_explicit(&gDBAppKitWindowRequest, 1, memory_order_release);
    return 1;
}

int32_t DBAppKitConsumeDemoWindowRequest(void) {
    return atomic_exchange_explicit(&gDBAppKitWindowRequest, 0, memory_order_acq_rel);
}

void* DBAppKitRequestDemoWindowAddress(void) {
    return (void*)&DBAppKitRequestDemoWindow;
}
