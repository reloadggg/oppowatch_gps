package com.kartlap.se.persistence

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * Helper for working with [AtomicFile] from Kotlin. Provides scoped writers and safe commit/rollback helpers.
 */
object AtomicFileUtils {

    fun create(context: Context, fileName: String): AtomicFile {
        val dir = File(context.filesDir, "tracks")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return AtomicFile(File(dir, fileName))
    }

    inline fun writeText(file: AtomicFile, charset: Charset = Charsets.UTF_8, crossinline block: OutputStream.() -> Unit) {
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.block()
            stream.flush()
            file.finishWrite(stream)
        } catch (t: Throwable) {
            stream?.let { file.failWrite(it) }
            throw t
        }
    }

    fun ensureParentExists(file: File) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
    }
}
