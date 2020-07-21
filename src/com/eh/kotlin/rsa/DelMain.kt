package com.eh.kotlin.rsa

import com.eh.kotlin.commandhelperkt.CommandHelper
import java.io.File

class DelMain {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val dirPath = CommandHelper.waitInput("Directory Path: ")
            val isConfirmed = CommandHelper.waitInput("Confirm to Del all files recursively? ")
            if (isConfirmed != "Y") {
                return
            }
            val dir = File(dirPath)
            loopAndDelete(dir)
        }

        private fun loopAndDelete(dir: File) {
            if (dir.isDirectory) {
                println("Looping ${dir.absolutePath}")
                dir.listFiles()?.forEach {
                    if (!it.isDirectory) {
                        it.deleteOnExit()
                        println("Deleting ${it.absolutePath}")
                    } else {
                        loopAndDelete(it)
                    }
                }
            }
        }
    }
}