package org.example

data class AssemblyResult(val machineCode: ByteArray, val labels: Map<String, Int>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AssemblyResult

        if (!machineCode.contentEquals(other.machineCode)) return false
        if (labels != other.labels) return false

        return true
    }

    override fun hashCode(): Int {
        var result = machineCode.contentHashCode()
        result = 31 * result + labels.hashCode()
        return result
    }
}
