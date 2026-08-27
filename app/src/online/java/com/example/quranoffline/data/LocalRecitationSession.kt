package com.example.quranoffline.data

import android.content.Context
import java.io.File

/**
 * The Online distribution intentionally has no local recognizer or sherpa-onnx
 * dependency. This source-compatible guard makes accidental offline selection
 * fail visibly instead of silently falling back to another recognizer.
 */
class LocalRecitationSession(
    private val context: Context,
    private val modelDirectory: File,
    private val onUpdate: (RecognitionUpdate) -> Unit,
) {
    fun start() {
        onUpdate(RecognitionUpdate.Failed("هذه نسخة Online ولا تحتوي محرك التسميع Offline."))
    }

    fun stop() {
        onUpdate(RecognitionUpdate.Stopped)
    }
}
