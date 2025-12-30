package com.stitchcodes.recording.service

data class VoiceFrame(val bytes: ByteArray, val hasVoice: Boolean) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoiceFrame

        if (!bytes.contentEquals(other.bytes)) return false
        if (hasVoice != other.hasVoice) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + hasVoice.hashCode()
        return result
    }

}