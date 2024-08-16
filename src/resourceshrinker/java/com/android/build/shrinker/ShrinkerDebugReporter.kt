/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.shrinker

import java.io.File
import java.io.PrintWriter
import java.util.function.Supplier

interface ShrinkerDebugReporter : AutoCloseable {
    fun debug(f: Supplier<String>)
    fun info(f: Supplier<String>)
}

object NoDebugReporter : ShrinkerDebugReporter {
    override fun debug(f: Supplier<String>) = Unit

    override fun info(f: Supplier<String>) = Unit

    override fun close() = Unit
}

class FileReporter(
    reportFile: File
) : ShrinkerDebugReporter {
    private val writer: PrintWriter = reportFile.let { PrintWriter(it) }
    override fun debug(f: Supplier<String>) {
        writer.println(f.get())
    }

    override fun info(f: Supplier<String>) {
        writer.println(f.get())
    }

    override fun close() {
        writer.close()
    }
}

class LoggerAndFileDebugReporter(
    private val logDebug: (String) -> Unit,
    private val logInfo: (String) -> Unit,
    reportFile: File?
) : ShrinkerDebugReporter {
    private val writer: PrintWriter? = reportFile?.let { PrintWriter(it) }

     override fun debug(f: Supplier<String>) {
        val message = f.get()
        writer?.println(message)
        logDebug(message)
    }

    override fun info(f: Supplier<String>) {
        val message = f.get()
        writer?.println(message)
        logInfo(message)
    }

    override fun close() {
        writer?.close()
    }
}
