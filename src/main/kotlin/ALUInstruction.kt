package org.example

data class ALUInstruction(
    val opcode: Int,
    val dest: Operand,
    val src: Operand
)
