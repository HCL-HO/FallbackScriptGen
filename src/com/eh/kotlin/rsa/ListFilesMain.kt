package com.eh.kotlin.rsa

import com.eh.kotlin.commandhelperkt.CommandHelper
import java.io.File

class ListFilesMain {
    companion object {
        lateinit var rootDir: String
        const val MY_ROOT = "C:\\workspace\\RSA keys\\2020 PTP merh RSA key update\\"

        @JvmStatic
        fun main(args: Array<String>) {
            rootDir = CommandHelper.waitInput("Directory Path: ")
            val dir = File(rootDir)
            loopAndPrint(dir)
        }

        private fun loopAndPrint(dir: File) {
            if (dir.isDirectory) {
                if (dir.parentFile.absolutePath == rootDir) {
//                    println(dir.name)
                }
                val files = dir.listFiles()
                if(files.isNullOrEmpty()){
                    println(" ")
                }
                files?.forEach {
                    if (!it.isDirectory) {
                        println(it.absolutePath.replace(MY_ROOT, ""))
                    } else {
                        loopAndPrint(it)
                    }
                }
            }
        }
    }
}
