package org.example

object Assembler {
    fun assemble(source: List<String>): AssemblyResult {
        val machineCode = mutableListOf<Byte>()
        val labels = mutableMapOf<String, Int>()
        val dataSegment = mutableListOf<Byte>()  // Данные будут добавлены после кода
        val dataLabels = mutableMapOf<String, Int>()  // Метки данных с их адресами
        val instructions = mutableListOf<Pair<String, String>>()

        // Первый проход: обработка меток и инструкций
        var codeAddress = 0
        var dataAddress = 0  // Адрес внутри сегмента данных

        for (line in source) {
            val cleanLine = line.trim().split("//")[0].trim()
            if (cleanLine.isEmpty()) continue

            var lineToProcess = cleanLine

            // Проверяем наличие метки
            if (lineToProcess.contains(":")) {
                val parts = lineToProcess.split(":", limit = 2)
                val label = parts[0].trim()
                lineToProcess = parts.getOrNull(1)?.trim() ?: ""
                if (lineToProcess.isEmpty()) {
                    // Метка без инструкции или директивы
                    labels[label] = codeAddress
                    continue
                } else if (lineToProcess.startsWith("DB") || lineToProcess.startsWith("DW") || lineToProcess.startsWith("DD")) {
                    // Метка данных
                    dataLabels[label] = dataAddress
                } else {
                    // Метка инструкции
                    labels[label] = codeAddress
                }
            }

            if (lineToProcess.startsWith("DB") || lineToProcess.startsWith("DW") || lineToProcess.startsWith("DD")) {
                // Директивы данных
                val parts = lineToProcess.split("\\s+".toRegex(), limit = 2)
                val directive = parts[0].toUpperCase()
                val values = if (parts.size > 1) parts[1] else ""
                val dataValues = parseDataDirective(directive, values)
                dataSegment.addAll(dataValues)
                dataAddress += dataValues.size
            } else {
                // Инструкция
                val tokens = lineToProcess.split("\\s+".toRegex(), limit = 2)
                val instr = tokens[0].toUpperCase()
                val args = if (tokens.size > 1) tokens[1] else ""
                instructions.add(Pair(instr, args))
                println("$instr $codeAddress")
                codeAddress += when (instr) {
                    "NOP", "HLT" -> 1
                    "JMP", "JE", "JNE" -> 5
                    "MOV", "ADD", "SUB", "MUL", "DIV", "CMP" -> {
                        val operands = args.split(",").map { it.trim() }
                        println("operations lengths $instr $args ${operands[0].length} ${operands[1].length}")
                        val operandLengths = parseOperandLengths(args)

                        13 + operandLengths.first + operandLengths.second
                    }
                    else -> {
                        throw IllegalArgumentException("Неизвестная инструкция: $instr")
                    }
                }
            }
        }

        // После первого прохода
        val codeSize = codeAddress  // Размер кода
        val dataStartAddress = codeSize  // Адрес начала данных
        val adjustedDataLabels = dataLabels.mapValues { it.value + dataStartAddress }
        val allLabels = labels + adjustedDataLabels

        // Второй проход: генерация машинного кода
        for ((instr, args) in instructions) {
            when (instr) {
                "NOP" -> {
                    machineCode.add(OpCode.NOP.code.toByte())
                }
                "HLT" -> {
                    machineCode.add(OpCode.HLT.code.toByte())
                }
                "JMP", "JE", "JNE" -> {
                    val opcode = when (instr) {
                        "JMP" -> OpCode.JMP
                        "JE"  -> OpCode.JE
                        "JNE" -> OpCode.JNE
                        else -> throw IllegalArgumentException("Неизвестная инструкция: $instr")
                    }
                    val addressValue = parseOperandValue(args.trim(), allLabels)
                    machineCode.add(opcode.code.toByte())
                    writeIntToCode(machineCode, addressValue)
                }
                "MOV", "ADD", "SUB", "MUL", "DIV", "CMP" -> {
                    val operands = args.split(",").map { it.trim() }
                    if (operands.size != 2) {
                        throw IllegalArgumentException("Неверное количество операндов в инструкции $instr")
                    }
                    val dest = parseOperand(operands[0], allLabels)
                    val src = parseOperand(operands[1], allLabels)
                    val opcode = when (instr) {
                        "MOV" -> OpCode.MOV
                        "ADD" -> OpCode.ADD
                        "SUB" -> OpCode.SUB
                        "MUL" -> OpCode.MUL
                        "DIV" -> OpCode.DIV
                        "CMP" -> OpCode.CMP
                        else -> throw IllegalArgumentException("Неизвестная инструкция: $instr")
                    }
                    machineCode.add(opcode.code.toByte())
                    // Запись первого операнда
                    machineCode.add(dest.type.ordinal.toByte())
                    val destNameBytes = dest.name?.toByteArray() ?: byteArrayOf()
                    machineCode.add(destNameBytes.size.toByte())
                    machineCode.addAll(destNameBytes.toTypedArray())
                    writeIntToCode(machineCode, dest.value)
                    // Запись второго операнда
                    machineCode.add(src.type.ordinal.toByte())
                    val srcNameBytes = src.name?.toByteArray() ?: byteArrayOf()
                    machineCode.add(srcNameBytes.size.toByte())
                    machineCode.addAll(srcNameBytes.toTypedArray())
                    writeIntToCode(machineCode, src.value)
                }
                else -> {
                    throw IllegalArgumentException("Неизвестная инструкция: $instr")
                }
            }
        }

        // Добавляем данные в конец машинного кода
        machineCode.addAll(dataSegment)

        return AssemblyResult(machineCode.toByteArray(), allLabels)
    }

    fun parseOperand(operand: String, labels: Map<String, Int>): Operand {
        return when {
            operand.matches(Regex("E?[ABCD]X")) -> {
                // Register EAX, EBX, ECX, EDX or AX, BX, CX, DX
                val isWord = !operand.startsWith("E")
                Operand(OperandType.REGISTER, operand.toUpperCase(), 0, isWord)
            }
            operand.matches(Regex("\\[.*\\]")) -> {
                // Memory, e.g., [100] or [label]
                val addressStr = operand.removeSurrounding("[", "]")
                val address = parseOperandValue(addressStr, labels)
                // Assume 16-bit data
                val isWord = true
                Operand(OperandType.MEMORY, null, address, isWord)
            }
            operand.matches(Regex("\\d+")) -> {
                // Immediate value
                Operand(OperandType.IMMEDIATE, null, operand.toInt())
            }
            labels.containsKey(operand) -> {
                // Label address
                Operand(OperandType.IMMEDIATE, null, labels[operand]!!)
            }
            else -> throw IllegalArgumentException("Unknown operand: $operand")
        }
    }

    fun parseOperandValue(operand: String, labels: Map<String, Int>): Int {
        return operand.toIntOrNull() ?: labels[operand]
        ?: throw IllegalArgumentException("Неизвестный операнд или метка: $operand")
    }

    fun parseOperandLengths(args: String): Pair<Int, Int> {
        val operands = args.split(",").map { it.trim() }

        val destLength = calculateOperandLenght(operands[0])
        val srcLength = calculateOperandLenght(operands[1])
        return Pair(destLength, srcLength)
    }

    fun calculateOperandLenght(operand: String): Int{
        return when {
            operand.matches(Regex("E?[ABCD]X")) -> {
                operand.length
            }
            operand.matches(Regex("\\[.*\\]")) -> {
                operand.length
            }
            operand.matches(Regex("\\d+")) -> {
                0
            }
            else -> throw IllegalArgumentException("Unknown operand: $operand")
        }
    }

    fun parseDataDirective(directive: String, values: String): List<Byte> {
        return when (directive) {
            "DB" -> {
                val bytes = mutableListOf<Byte>()
                val parts = values.split(",").map { it.trim() }
                for (part in parts) {
                    if (part.startsWith("'") && part.endsWith("'")) {
                        // Строка
                        val str = part.substring(1, part.length - 1)
                        bytes.addAll(str.toByteArray().toTypedArray())
                    } else {
                        // Число
                        bytes.add(part.toInt().toByte())
                    }
                }
                bytes
            }
            "DW" -> {
                println("directive $directive values $values")
                val bytes = mutableListOf<Byte>()
                val parts = values.split(",").map { it.trim() }
                for (part in parts) {
                    val value = part.toInt()
                    bytes.add((value and 0xFF).toByte())
                    bytes.add(((value shr 8) and 0xFF).toByte())
                }
                bytes
            }
            "DD" -> {
                val bytes = mutableListOf<Byte>()
                val parts = values.split(",").map { it.trim() }
                for (part in parts) {
                    val value = part.toInt()
                    bytes.add((value and 0xFF).toByte())
                    bytes.add(((value shr 8) and 0xFF).toByte())
                    bytes.add(((value shr 16) and 0xFF).toByte())
                    bytes.add(((value shr 24) and 0xFF).toByte())
                }
                bytes
            }
            else -> throw IllegalArgumentException("Неизвестная директива данных: $directive")
        }
    }

    fun writeIntToCode(code: MutableList<Byte>, value: Int) {
        code.add((value and 0xFF).toByte())
        code.add(((value shr 8) and 0xFF).toByte())
        code.add(((value shr 16) and 0xFF).toByte())
        code.add(((value shr 24) and 0xFF).toByte())
    }
}