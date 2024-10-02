package org.example

import java.util.*

class CPU {
    var pc: Int = 0                        // Счетчик команд
    var zeroFlag: Boolean = false          // Флаг нуля
    var memory = ByteArray(MEMORY_SIZE)    // Память
    var registers = mutableMapOf(
        "EAX" to 0,  // 32-битный регистр
        "EBX" to 0,
        "ECX" to 0,
        "EDX" to 0
    )

    fun loadProgram(program: ByteArray) {
        program.copyInto(memory)
        println("${Arrays.toString(memory)}")
    }

    fun run() {
        while (true) {
            val opcode = memory[pc].toInt() and 0xFF
            when (opcode) {
                OpCode.NOP.code -> {
                    pc += 1
                }
                OpCode.MOV.code, OpCode.ADD.code, OpCode.SUB.code, OpCode.MUL.code, OpCode.DIV.code, OpCode.CMP.code -> {
                    val instr = decodeInstruction(opcode)
                    executeALUInstruction(instr)
                }
                OpCode.JMP.code, OpCode.JE.code, OpCode.JNE.code -> {
                    val instr = decodeJumpInstruction(opcode)
                    executeJumpInstruction(instr)
                }
                OpCode.HLT.code -> {
                    println("Программа завершена.")
                    return
                }
                else -> {
                    println("Неизвестная команда: $opcode по адресу $pc")
                    return
                }
            }
        }
    }

    private fun decodeInstruction(opcode: Int): ALUInstruction {
        val operandType1 = memory[pc + 1].toInt()
        val operandName1Length = memory[pc + 2].toInt()
        val operandName1 = if (operandName1Length > 0) {
            val bytes = memory.copyOfRange(pc + 3, pc + 3 + operandName1Length)
            String(bytes)
        } else null
        val operandValue1 = readInt(pc + 3 + operandName1Length)
        val operandType2 = memory[pc + 7 + operandName1Length].toInt()
        val operandName2Length = memory[pc + 8 + operandName1Length].toInt()
        val operandName2 = if (operandName2Length > 0) {
            val bytes = memory.copyOfRange(pc + 9 + operandName1Length, pc + 9 + operandName1Length + operandName2Length)
            String(bytes)
        } else null
        val operandValue2 = readInt(pc + 9 + operandName1Length + operandName2Length)
        pc += 13 + operandName1Length + operandName2Length
        println(
            ALUInstruction(
                opcode,
                Operand(OperandType.entries[operandType1], operandName1, operandValue1),
                Operand(OperandType.entries[operandType2], operandName2, operandValue2)
            )
        )
        return ALUInstruction(
            opcode,
            Operand(OperandType.entries[operandType1], operandName1, operandValue1),
            Operand(OperandType.entries[operandType2], operandName2, operandValue2)
        )
    }

    private fun executeALUInstruction(instr: ALUInstruction) {
        val destValue = getOperandValue(instr.dest)
        val srcValue = getOperandValue(instr.src)
        when (instr.opcode) {
            OpCode.MOV.code -> {
                println(instr)
                setOperandValue(instr.dest, srcValue)
            }
            OpCode.ADD.code -> {
                val result = destValue + srcValue
                setOperandValue(instr.dest, result)
                zeroFlag = result == 0
            }
            OpCode.SUB.code -> {
                val result = destValue - srcValue
                setOperandValue(instr.dest, result)
                zeroFlag = result == 0
            }
            OpCode.MUL.code -> {
                val result = destValue * srcValue
                setOperandValue(instr.dest, result)
                zeroFlag = result == 0
            }
            OpCode.DIV.code -> {
                if (srcValue == 0) {
                    throw ArithmeticException("Деление на ноль")
                }
                val result = destValue / srcValue
                setOperandValue(instr.dest, result)
                zeroFlag = result == 0
            }
            OpCode.CMP.code -> {
                val result = destValue - srcValue
                zeroFlag = result == 0
                // Инструкция CMP не изменяет значение dest, только устанавливает флаги
            }
            else -> {
                throw IllegalArgumentException("Неизвестная ALU операция: ${instr.opcode}")
            }
        }
        println("После выполнения инструкции ${OpCode.entries.find { it.code == instr.opcode } }:")
        println("EAX = ${registers["EAX"]}")
        println("EBX = ${registers["EBX"]}")
        println("ECX = ${registers["ECX"]}")
        println("EDX = ${registers["EDX"]}")
    }

    private fun decodeJumpInstruction(opcode: Int): JumpInstruction {
        val address = readInt(pc + 1)
        pc += 5
        return JumpInstruction(opcode, address)
    }

    private fun executeJumpInstruction(instr: JumpInstruction) {
        when (instr.opcode) {
            OpCode.JMP.code -> {
                pc = instr.address
            }
            OpCode.JE.code -> {
                pc = if (zeroFlag) instr.address else pc
            }
            OpCode.JNE.code -> {
                pc = if (!zeroFlag) instr.address else pc
            }
        }
    }

    private fun getOperandValue(operand: Operand): Int {
        return when (operand.type) {
            OperandType.REGISTER -> {
                println(operand)
                val fullRegName = operand.name!!.toUpperCase()
                when (fullRegName) {
                    "EAX", "EBX", "ECX", "EDX" -> registers[fullRegName]!!
                    "AX", "BX", "CX", "DX" -> registers["E$fullRegName"]!! and 0xFFFF
                    else -> throw IllegalArgumentException("Неизвестный регистр: ${operand.name}")
                }
            }
            OperandType.MEMORY -> {
                // Проверяем размер операнда (16 или 32 бита)
                if (operand.isWord) {
                    readWord(operand.value)
                } else {
                    readInt(operand.value)
                }
            }
            OperandType.IMMEDIATE -> operand.value
        }
    }

    private fun setOperandValue(operand: Operand, value: Int) {
        when (operand.type) {
            OperandType.REGISTER -> {
                val fullRegName = operand.name!!.toUpperCase()
                if (fullRegName in listOf("EAX", "EBX", "ECX", "EDX")) {
                    registers[fullRegName] = value
                } else if (fullRegName in listOf("AX", "BX", "CX", "DX")) {
                    val regName = "E$fullRegName"
                    val existingValue = registers[regName]!!
                    println(value)
                    registers[regName] = (existingValue and 0xFFFF0000.toInt()) or (value and 0xFFFF)
                } else {
                    throw IllegalArgumentException("Unknown register: ${operand.name}")
                }
            }
            OperandType.MEMORY -> {
                if (operand.isWord) {
                    writeWord(operand.value, value)
                } else {
                    writeInt(operand.value, value)
                }
            }
            OperandType.IMMEDIATE -> throw IllegalArgumentException("Cannot write to an immediate value")
        }
    }

    fun readInt(address: Int): Int {
        // Проверка выхода за пределы памяти
        if (address < 0 || address + 3 >= MEMORY_SIZE) {
            throw IllegalArgumentException("Выход за пределы памяти при чтении")
        }

        return (memory[address].toInt() and 0xFF) or
                ((memory[address + 1].toInt() and 0xFF) shl 8) or
                ((memory[address + 2].toInt() and 0xFF) shl 16) or
                ((memory[address + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeInt(address: Int, value: Int) {
        // Проверка выхода за пределы памяти
        if (address < 0 || address + 3 >= MEMORY_SIZE) {
            throw IllegalArgumentException("Выход за пределы памяти при записи")
        }

        memory[address] = (value and 0xFF).toByte()
        memory[address + 1] = ((value shr 8) and 0xFF).toByte()
        memory[address + 2] = ((value shr 16) and 0xFF).toByte()
        memory[address + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun readWord(address: Int): Int {
        if (address < 0 || address + 1 >= MEMORY_SIZE) {
            throw IllegalArgumentException("Выход за пределы памяти при чтении")
        }
        return (memory[address].toInt() and 0xFF) or
                ((memory[address + 1].toInt() and 0xFF) shl 8)
    }

    private fun writeWord(address: Int, value: Int) {
        if (address < 0 || address + 1 >= MEMORY_SIZE) {
            throw IllegalArgumentException("Выход за пределы памяти при записи")
        }
        memory[address] = (value and 0xFF).toByte()
        memory[address + 1] = ((value shr 8) and 0xFF).toByte()
    }

    companion object {
        const val MEMORY_SIZE = 1024
    }
}