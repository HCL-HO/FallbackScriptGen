package com.eh.kotlin.fallbackScript

data class InsertModel(var table: String, var parms: Array<String>, var values: Array<String>) {
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InsertModel

        if (table != other.table) return false
        if (!parms.contentEquals(other.parms)) return false
        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = table.hashCode()
        result = 31 * result + parms.contentHashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}