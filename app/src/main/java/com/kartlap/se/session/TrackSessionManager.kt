package com.kartlap.se.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

class TrackSessionManager(private val context: Context) {

    private val activeSessionIdFlow = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = activeSessionIdFlow

    fun startNewSession(): String {
        val sessionId = UUID.randomUUID().toString()
        markSessionState(sessionId, SessionState.ACTIVE)
        activeSessionIdFlow.value = sessionId
        return sessionId
    }

    fun resumeIncomplete(): String? {
        val dir = File(context.filesDir, "tracks")
        val incomplete = dir.listFiles()?.firstOrNull { it.name.endsWith(".incomplete") }
        return incomplete?.let {
            val sessionId = it.nameWithoutExtension
            markSessionState(sessionId, SessionState.ACTIVE)
            activeSessionIdFlow.value = sessionId
            sessionId
        }
    }

    fun markCompleted(sessionId: String) {
        markSessionState(sessionId, SessionState.COMPLETED)
        activeSessionIdFlow.value = null
    }

    fun markIncomplete(sessionId: String) {
        markSessionState(sessionId, SessionState.INCOMPLETE)
    }

    private fun markSessionState(sessionId: String, state: SessionState) {
        val dir = File(context.filesDir, "tracks")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir.listFiles { file ->
            file.name == "$sessionId.${SessionState.ACTIVE.extension}" ||
                file.name == "$sessionId.${SessionState.INCOMPLETE.extension}" ||
                file.name == "$sessionId.${SessionState.COMPLETED.extension}"
        }?.forEach { it.delete() }
        val marker = File(dir, "$sessionId.${state.extension}")
        marker.writeText(state.name)
    }

    enum class SessionState(val extension: String) {
        ACTIVE("active"),
        INCOMPLETE("incomplete"),
        COMPLETED("done")
    }
}
