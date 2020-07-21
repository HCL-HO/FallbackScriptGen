package com.eh.kotlin.fallbackScript

import com.eh.kotlin.commandhelperkt.CommandHelper
import com.eh.kotlin.db.ConnectionManager
import com.sun.org.apache.xpath.internal.operations.Bool
import org.apache.commons.io.FileUtils
import java.io.File
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Types
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class FallbackScriptGen {

    companion object {
        private const val REG_SET_PARM = " set (.*?)where "
        private const val REG_UPDAET_SQL = "update(.*?);"
        private const val REG_TABLE = "update(.*?) set "
        private const val REG_WHERE = "where(.*?);"

        private const val REG_INSERT_SQL = "insert(.*?);"
        private const val REG_TABLE_INSERT = "into(.*?)(| )\\("
        private const val REG_COL_INSERT = "\\((.*?)\\) values"
        private const val REG_VAL_INSERT = "values \\((.*?)\\);"


        @JvmStatic
        fun main(args: Array<String>) {
            val path = CommandHelper.waitInput("Enter SQL File Path")
            val isChooseDeleteParm = CommandHelper.waitBoolean("Choose Keys In Delete Statements? ")
//            val path = "C:\\workspace\\Data_extraction_or_patch\\Franker_Data_Patch\\Updating Franking Machine PF10865\\PF10865_Data_Patch.sql"
            genFallbackScript(path, isChooseDeleteParm)
        }

        /**
         *  Generate Fallback Script from Update SQL and write file
         */
        private fun genFallbackScript(pathtoread: String, chooseDeleteParm: Boolean) {
            val SQLs = FileUtils.readFileToString(File(pathtoread), "UTF-8")
            val updateFallbackSQLs = genUpdateFallBack(SQLs)
            val insertFallbackSQLs = genInsertFallBack(SQLs, chooseDeleteParm)
            val fallbackScript = insertFallbackSQLs + updateFallbackSQLs
            writeFallBackFile(fallbackScript, pathtoread)
        }

        private fun writeFallBackFile(fallbackScript: String, pathtoread: String) {
            val pathToWrite =
                File(pathtoread).parentFile.absolutePath + File.separator + File(pathtoread).nameWithoutExtension + "_fallback.sql"
            FileUtils.write(File(pathToWrite), fallbackScript, "UTF-8")
            println("Fallback script:\n $fallbackScript")
            println("Created ${File(pathToWrite).absolutePath}")
        }

        private fun genInsertFallBack(sqLs: String, chooseDeleteParm: Boolean): String {
            val insertSQLs: Array<String> = getInsertSQLs(sqLs)
            val insertTableMap: Array<InsertModel> = getInsertMap(insertSQLs)
            val insertFallBackSQL: String = getInsertFallback(insertTableMap, chooseDeleteParm)
            return insertFallBackSQL + System.lineSeparator()
        }

        private fun genUpdateFallBack(sqLs: String): String {
            val updateSQLs: Array<String> = getUpdateSQLs(sqLs)
            val updateTableMap: Array<UpdateModel> = getUpdateMap(updateSQLs)
            val updateFallBackSQL: String = getUpdateFallback(updateTableMap)
            return updateFallBackSQL + System.lineSeparator()
        }


        private fun getInsertFallback(
            insertTableMap: Array<InsertModel>,
            chooseDeleteParm: Boolean
        ): String {
            val sb = StringBuilder()
            val template = "delete from %s where %s ;"
            val tableToKeys = Hashtable<String, Collection<String>>()

            insertTableMap.forEach {
                val table = it.table
                var whereClause: String = ""
                if (chooseDeleteParm && !tableToKeys.containsKey(it.table)) {
                    val keys = CommandHelper.chooseMany(
                        "Choose Keys For Delete Statements Table(${it.table}): ",
                        it.parms.toList()
                    )
                    if (keys.isNotEmpty()) {
                        tableToKeys[it.table] = keys
                    }
                }

                it.parms.forEachIndexed { index, s ->
                    val value = it.values[index]
                    val appendParm: Boolean =
                        if (chooseDeleteParm) (tableToKeys[it.table] != null && tableToKeys[it.table]!!.contains(s)) else true
                    val appendAnd: Boolean =
                        if (chooseDeleteParm) (index != tableToKeys[it.table]!!.size - 1) else (index != it.parms.size - 1)
                    if (appendParm) {
                        whereClause += if (value.toLowerCase() == "sysdate") {
                            "TRUNC($s) = TRUNC($value)"
                        } else {
                            "$s = $value"
                        }
                        if (appendAnd) {
                            whereClause += " and "
                        }
                    }
                }

                val sql = String.format(template, table, whereClause)
                sb.append(sql + System.lineSeparator())
            }
            return sb.toString()
        }

        private fun getUpdateFallback(updateTableMap: Array<UpdateModel>): String {
            val sb = StringBuilder()
            updateTableMap.forEach { model ->
                var selectParm = ""
                model.parms.forEachIndexed { index, s ->
                    selectParm += s
                    if (index < model.parms.size - 1) {
                        selectParm += ", "
                    }
                }
                val selectSQL = "select $selectParm from ${model.table} where ${model.whereClause}"
                println("selectSQL: $selectSQL")
                val fallbackSQLFormat = "update${model.table} set %s where${model.whereClause};"
                val fallbackSQL = ConnectionManager.query(selectSQL) { rs ->
                    var parms = ""
                    if (rs.next()) {
                        val meta = rs.metaData
                        for (x in 1..meta.columnCount) {
                            val setParm: String =
                                getSetParm(
                                    x, meta, rs
                                )
                            parms += setParm
                            if (x < meta.columnCount) {
                                parms += ","
                            }
                        }
                    }
                    return@query String.format(fallbackSQLFormat, parms)
                }
                println("update fallback sql: $fallbackSQL")
                sb.append(fallbackSQL)
            }
            return sb.toString()
        }

        /**
         *  Parse ResultSet and make Set statement and fallback SQL
         */
        private fun getSetParm(x: Int, meta: ResultSetMetaData, rs: ResultSet): String {
            return when (meta.getColumnType(x)) {
                Types.CHAR, Types.VARCHAR -> "${meta.getColumnLabel(x)}='${rs.getString(x)}'"
                Types.NUMERIC -> "${meta.getColumnLabel(x)}=${rs.getInt(x)}"
                Types.DATE -> {
                    val dateFormatter = SimpleDateFormat("yyyyMMdd")
                    val date = rs.getDate(x)
                    val dateStr: String = dateFormatter.format(date)
                    "${meta.getColumnLabel(x)}=to_date('$dateStr','yyyyMMdd')"
                }
                else -> ""
            }

        }

        /**
         *  Extract required Information from Update SQLs
         */
        private fun getUpdateMap(updateSQLs: Array<String>): Array<UpdateModel> {
            var updateModels = arrayOf<UpdateModel>()
            val patternTable = Pattern.compile(REG_TABLE, Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
            val patternPARM = Pattern.compile(REG_SET_PARM, Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
            val patternWhere = Pattern.compile(REG_WHERE, Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))

            updateSQLs.forEach { sql ->
                val mt: Matcher = patternTable.matcher(sql)
                val mp: Matcher = patternPARM.matcher(sql)
                val mw = patternWhere.matcher(sql)
                var table: String = ""
                var parms: String = ""
                var where: String = ""
                if (mt.find()) {
                    table = mt.group(1)
                }
                if (mp.find()) {
                    parms = mp.group(1)
                }
                if (mw.find()) {
                    where = mw.group(1)
                }
                println("sql: $sql")
                println(table)
                println(parms)
                println(where)
                if (table.isNotEmpty() && parms.isNotEmpty() && where.isNotEmpty()) {
                    val parmArray: Array<String> =
                        getUpdateParmArray(parms)
                    updateModels = updateModels.plus(
                        UpdateModel(
                            table,
                            parmArray,
                            where
                        )
                    )
                }
            }

            return updateModels

        }

        /**
         * Form array from set data extracted from SQL
         */
        private fun getUpdateParmArray(parms: String): Array<String> {
            var result = arrayOf<String>()
            val parmTrim = parms.replace(" ", "")
            parmTrim.split(",").forEach { parmPair ->
                if (parmPair.split("=").size == 2) {
                    result = result.plus(parmPair.split("=")[0])
                }
            }
            return result
        }

        /**
         * Extract all update SQLs from plan text
         */
        private fun getUpdateSQLs(SQLs: String): Array<String> {
//            println(SQLs)
            val pattern: Pattern = Pattern.compile(REG_UPDAET_SQL, Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
            val m: Matcher = pattern.matcher(SQLs)
            var result = arrayOf<String>()
            while (m.find()) {
                val statment = m.group(0)
                println("Found value: $statment")
                result = result.plus(statment)
            }
            return result
        }

        private fun getInsertMap(insertSQLs: Array<String>): Array<InsertModel> {
            var insertModels = arrayOf<InsertModel>()
            val patternTable = Pattern.compile(REG_TABLE_INSERT, Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
            val patternPARM = Pattern.compile(REG_COL_INSERT, Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
            val patternWhere = Pattern.compile(REG_VAL_INSERT, Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))

            insertSQLs.forEach { sql ->
                val mt: Matcher = patternTable.matcher(sql)
                val mp: Matcher = patternPARM.matcher(sql)
                val mw = patternWhere.matcher(sql)
                var table: String = ""
                var parms: String = ""
                var values: String = ""
                if (mt.find()) {
                    table = mt.group(1)
                }
                if (mp.find()) {
                    parms = mp.group(1)
                }
                if (mw.find()) {
                    values = mw.group(1)
                }
                println("sql: $sql")
                println(table)
                println(parms)
                println(values)
                if (table.isNotEmpty() && parms.isNotEmpty() && values.isNotEmpty()) {
                    val parmArray: Array<String> = getParmArray(parms.trim())
                    val valuesArray: Array<String> = getParmArray(values.trim())
                    insertModels = insertModels.plus(
                        InsertModel(
                            table.trim(),
                            parmArray,
                            valuesArray
                        )
                    )
                }
            }
            return insertModels
        }

        private fun getParmArray(parms: String): Array<String> {
            val raw = parms.split(",").reversed()
            var array = arrayOf<String>()
            raw.forEachIndexed { index, s ->
                if (s.toLowerCase().startsWith("to_date") || s.toLowerCase().startsWith("to_char")) {
                    val joined = s + "," + raw[index - 1]
                    array[array.size - 1] = joined
                } else {
                    array = array.plus(s)
                }
            }
            return array.reversedArray()
        }

        private fun getInsertSQLs(SQLs: String): Array<String> {
//            println(SQLs)
            val pattern: Pattern = Pattern.compile(REG_INSERT_SQL, Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
            val m: Matcher = pattern.matcher(SQLs)
            var result = arrayOf<String>()
            while (m.find()) {
                val statment = m.group(0)
                println("Found value: $statment")
                result = result.plus(statment)
            }
            return result
        }

    }

}