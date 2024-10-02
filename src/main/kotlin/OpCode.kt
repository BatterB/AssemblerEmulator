package org.example

enum class OpCode(val code: Int) {
    NOP(0x00),
    MOV(0x01),
    ADD(0x02),
    SUB(0x03),
    MUL(0x04),
    DIV(0x05),
    JMP(0x06),
    CMP(0x07),
    JE(0x08),
    JNE(0x09),
    HLT(0xFF)
}