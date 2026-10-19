/*
 * WinKit A4 synthetic x86 BIOS MBR smoke test. Free/open test code only.
 * When executed by a BIOS from LBA 0 at physical 0000:7C00, prints the
 * identifying banner using INT 10h, then halts forever. No DOS/Windows,
 * partition/filesystem, NIC, I/O writes, or proprietary code is included.
 * This is a BOOT-FIXTURE, NOT an operating system.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
.code16
.section .text
.global _start
_start:
    cli
    xorw %ax, %ax
    movw %ax, %ds
    movw %ax, %ss
    movw $0x7c00, %sp
    sti
    movw $banner, %si
print_character:
    lodsb
    testb %al, %al
    jz idle
    movb $0x0e, %ah
    movw $0x0007, %bx
    int $0x10
    jmp print_character
idle:
    hlt
    jmp idle
banner:
    .ascii "WINKIT A4 BIOS HDD BOOT OK - NO MICROSOFT FILES"
    .byte 0
    .org 510
    .byte 0x55, 0xaa
