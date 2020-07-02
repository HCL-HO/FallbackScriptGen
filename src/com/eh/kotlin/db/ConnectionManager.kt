package com.eh.kotlin.db

import com.eh.kotlin.DBInfo
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class ConnectionManager {
    companion object{
        // JDBC driver name and database URL
        const val JDBC_DRIVER = DBInfo.JDBC_DRIVER
        const val DB_URL = DBInfo.DB_URL

        //  Database credentials
        const val USER = DBInfo.USER
        const val PASS = DBInfo.PASS

        fun query(sql: String, handleRS: (rs: ResultSet)-> Any): Any{
            Class.forName(JDBC_DRIVER)
            val myConn = connection()
            val stat = myConn.createStatement()
            val rs = stat.executeQuery(sql)
            val result = handleRS(rs)
            stat.close()
            myConn.close()
            return result
        }

        fun connection(): Connection{
            return DriverManager.getConnection(
                DB_URL,
                USER,
                PASS
            );
        }

        fun closeConnection(conn: Connection){
            conn.close()
        }
    }
}