package com.eh.kotlin

import java.lang.StringBuilder

class CommandHelper {
    companion object {
        fun waitInput(msg: String): String {
            println(msg)
            val input = readLine()
            return input ?: ""
        }

        fun waitNumber(msg: String, default: Int = 0): Int {
            println(msg)
            val input = readLine()?.toIntOrNull()
            return input ?: default
        }

        fun chooseOne(mails: Collection<String>): String {
            val sb = StringBuilder()
            mails.forEachIndexed { index, s ->
                sb.append("${index}: $s  ")
            }
            println(sb.toString())
            var index = readLine()?.toIntOrNull()
            while (index == null || index > mails.size - 1) {
                println(sb.toString())
                index = readLine()?.toIntOrNull()
            }
            return mails.elementAt(index)
        }

        fun chooseMany(mails: Collection<String>): Collection<String> {
            val chosen = HashSet<String>()
            val sb = StringBuilder()
            mails.forEachIndexed { index, s ->
                sb.append("${index}: $s  ")
            }
            println(sb.toString())
            var index = readLine()?.toIntOrNull()
            while (index != null) {
                if (index < mails.size) {
                    chosen.add(mails.elementAt(index))
                }
                println(sb.toString())
                index = readLine()?.toIntOrNull()
            }
            return chosen
        }
    }
}