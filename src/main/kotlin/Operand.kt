package org.example

data class Operand(
    val type: OperandType,
    val name: String?,
    val value: Int,
    val isWord: Boolean = false
)
