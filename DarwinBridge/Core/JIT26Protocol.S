.text
.align 2

.globl _DBJIT26PrepareRegion
_DBJIT26PrepareRegion:
    mov x16, #1
    brk #0xf00d
    ret

.globl _DBJIT26Detach
_DBJIT26Detach:
    mov x16, #0
    brk #0xf00d
    ret
