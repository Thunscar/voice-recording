package com.stitchcodes.recording.vad.webrtc

class VadNative private constructor(){
    init {
        System.loadLibrary("recording")
    }

    companion object {
        private val instance = VadNative()
        fun getInstance(): VadNative {
            return instance
        }
    }

    external fun init(mode: Int)

    external fun hasVoice(fs: Int, audioFrame: ShortArray): Boolean
}