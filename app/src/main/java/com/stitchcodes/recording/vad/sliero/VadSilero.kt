package com.stitchcodes.recording.vad.sliero

import ai.onnxruntime.*
import com.stitchcodes.recording.ContextHolder
import java.nio.FloatBuffer

class VadSilero private constructor() {

    companion object {
        private val instance = VadSilero()
        fun getInstance(): VadSilero {
            return instance
        }
    }

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private var state: FloatArray = FloatArray(2 * 1 * 128) { 0f }


    init {
        val modelBytes = ContextHolder.appContext().assets.open("silero_vad.onnx").readBytes()
        session = env.createSession(modelBytes)
    }

    fun shortToFloat(input: ShortArray): FloatArray {
        val output = FloatArray(input.size)
        for (i in input.indices) {
            output[i] = input[i] / 32768.0f
        }
        return output
    }

    fun hasVoice(sampleRate: Int, frame: ShortArray): FloatArray {
        val floatData = shortToFloat(frame)
        val inputShape = longArrayOf(1, frame.size.toLong())
        val inputTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(floatData), inputShape
        )
        val stateTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(state), longArrayOf(2, 1, 128)
        )
        val srTensor = OnnxTensor.createTensor(
            env, longArrayOf(sampleRate.toLong())
        )
        val inputs = mapOf(
            "input" to inputTensor, "sr" to srTensor, "state" to stateTensor
        )
        val result = session.run(inputs)
        val output = result[0].value as Array<FloatArray>
        return output[0] // speech probability (0.0 ~ 1.0)
    }

}