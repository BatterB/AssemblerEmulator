package org.example

import org.example.Assembler.assemble

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val sourceCode = listOf(
        "        MOV AX, 1   // Загружаем data1 в регистр AX (16 бит)",
        "        MOV BX, 2    // Загружаем data2 в регистр BX (16 бит)",
        "        loop:                                                  ",
        "        ADD AX, BX        // Складываем AX и BX, результат в AX",
        "        CMP AX, 11        // Складываем AX и BX, результат в AX",
        "        JNE loop       // Складываем AX и BX, результат в AX",
        "        MOV [result], AX   // Сохраняем результат в память",
        "        HLT                // Остановка программы",
        "data1:  DW   9          // Данные 1 (16 бит)",
        "data2:  DW   8           // Данные 2 (16 бит)",
        "result: DW   8             // Результат (16 бит)",
    )

    // Инициализация CPU и памяти
    val cpu = CPU()

    // Ассемблирование программы
    val assemblyResult = assemble(sourceCode)
    cpu.loadProgram(assemblyResult.machineCode)

    // Запуск программы
    cpu.run()

    // Получение адреса метки "result"
    val resultAddress = assemblyResult.labels["result"] ?: throw IllegalArgumentException("Метка 'result' не найдена")
    println(assemblyResult)
    // Вывод результата
    val result = cpu.readInt(resultAddress) and 0xFFFF
    println("Результат вычисления: $result")
}