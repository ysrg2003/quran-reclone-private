package com.example.quranoffline.data

sealed interface RecognitionUpdate {
    data object Started : RecognitionUpdate
    data class Text(
        /** Text accumulated for the recitation state machine. */
        val value: String,
        val timestamps: FloatArray,
        val isFinal: Boolean = false,
        val segmentCompleted: Boolean = false,
        /** The exact primary candidate from the most recent recognizer callback. */
        val providerText: String = value,
    ) : RecognitionUpdate
    data class Failed(val message: String) : RecognitionUpdate
    data object Stopped : RecognitionUpdate
}
