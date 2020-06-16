package com.eh.kotlin

import org.apache.commons.io.FileUtils
import java.io.File
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Types
import java.text.SimpleDateFormat
import java.util.regex.Matcher
import java.util.regex.Pattern

class Main {

    companion object {
        private const val REG_SET_PARM = " set (.*?)where"
        private const val REG_UPDAET_SQL = "update(.*?);"
        private const val REG_TABLE = "update(.*?) set"
        private const val REG_WHERE = "where(.*?);"
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Enter SQL File Path")
                return
            }
            val path = args[0]
//            val path = "C:\\workspace\\Data_extraction_or_patch\\DMDIY\\invoiceid_update\\invoiceid_update.sql"
            genFallbackScript(path)
//            ConnectionManager.query("select 1, 'TEST', sysdate from dual", ::handleRS)
        }

        /**
         *  Generate Fallback Script from Update SQL and write file
         */
        private fun genFallbackScript(pathtoread: String) {
            val SQLs = FileUtils.readFileToString(File(pathtoread), "UTF-8").toLowerCase();
            val updateSQLs: Array<String> = getUpdateSQLs(SQLs)
            val updateTableMap = getUpdateMap(updateSQLs)
            val fallbackScript = getFallbackScript(updateTableMap)
            val pathToWrite =
                File(pathtoread).parentFile.absolutePath + File.separator + File(pathtoread).nameWithoutExtension + "_fallback.sql"
            FileUtils.write(File(pathToWrite), fallbackScript, "UTF-8")
            println("Created ${File(pathToWrite).absolutePath}")
        }

        /**
         * Get current value from DB and make Fallback SQLs
         */
        private fun getFallbackScript(models: Array<UpdateModel>): String {
            val sb = StringBuilder()
            models.forEach { model ->
                var selectParm = ""
                model.parms.forEachIndexed { index, s ->
                    selectParm += s
                    if (index < model.parms.size - 1) {
                        selectParm += ", "
                    }
                }
                val selectSQL = "select $selectParm from ${model.table} where ${model.whereClause}"
                println("selectSQL: $selectSQL")
                val fallbackSQLFormat = "update ${model.table} set %s where ${model.whereClause};"
                val fallbackSQL = ConnectionManager.query(selectSQL) { rs ->
                    var parms = ""
                    if (rs.next()) {
                        val meta = rs.metaData
                        for (x in 1..meta.columnCount) {
                            val setParm: String = getSetParm(x, meta, rs)
                            parms += setParm
                            if (x < meta.columnCount) {
                                parms += ","
                            }
                        }
                    }
                    return@query String.format(fallbackSQLFormat, parms)
                }
                println("fallback sql: $fallbackSQL")
                sb.append(fallbackSQL as String + System.lineSeparator())
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
                    val dateFormatter = SimpleDateFormat("dd/MM/yy")
                    val date = rs.getDate(x)
                    val dateStr: String = dateFormatter.format(date)
                    "${meta.getColumnLabel(x)}=to_date('$dateStr','dd/mm/yy')"
                }
                else -> ""
            }

        }

        /**
         *  Extract required Information from Update SQLs
         */
        private fun getUpdateMap(updateSQLs: Array<String>): Array<UpdateModel> {
            var updateModels = arrayOf<UpdateModel>()
            val patternTable = Pattern.compile(REG_TABLE)
            val patternPARM = Pattern.compile(REG_SET_PARM)
            val patternWhere = Pattern.compile(REG_WHERE)

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
                    val parmArray: Array<String> = getParmArray(parms)
                    updateModels = updateModels.plus(UpdateModel(table, parmArray, where))
                }
            }

            return updateModels

        }

        /**
         * Form array from set data extracted from SQL
         */
        private fun getParmArray(parms: String): Array<String> {
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
            val pattern: Pattern = Pattern.compile(REG_UPDAET_SQL)
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