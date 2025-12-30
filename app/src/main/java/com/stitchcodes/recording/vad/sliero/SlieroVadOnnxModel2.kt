package com.stitchcodes.recording.vad.sliero

import ai.onnxruntime.*
import com.stitchcodes.recording.ContextHolder


class SlieroVadOnnxModel2 {

    companion object {
        private const val TAG = "SlieroVadOnnxModel"
        private val SAMPLE_RATES = arrayOf(8000, 16000)
        private const val BASIC_SAMPLE_RATE = 16000
    }

    private var session: OrtSession
    private lateinit var state: Array<Array<FloatArray>>
    private lateinit var context: Array<FloatArray>
    private var lastSr = 0
    private var lastBatchSize = 0

    init {
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions()
        opts.setInterOpNumThreads(1)
        opts.setIntraOpNumThreads(1)
        opts.addCPU(true)
        session = env.createSession(ContextHolder.appContext().assets.open("silero_vad.onnx").readBytes(), opts)
        resetStates()
    }

    private fun resetStates() {
        resetStates(1)
    }

    private fun resetStates(batchSize: Int) {
        state = Array(2) { Array(batchSize) { FloatArray(128) { 0f } } }
        context = Array(0) { FloatArray(0) }
        lastSr = 0
        lastBatchSize = 0
    }

    fun close() {
        session.close()
    }

    //验证结果
    class ValidationResult(val input: Array<FloatArray>, val sr: Int)

    //校验输入的数据(将高采样率的数据降采样率)
    private fun validateInput(x: Array<FloatArray>, sampleRate: Int): ValidationResult {
        var input = x
        if (input.size == 1) {
            input = arrayOf(input[0])
        }
        //最多支持双通道
        if (input.size > 2) {
            throw IllegalArgumentException("Incorrect audio data dimension:${input.size}")
        }
        //降采样
        var sr = sampleRate
        if (sampleRate != BASIC_SAMPLE_RATE && (sampleRate % BASIC_SAMPLE_RATE == 0)) {
            val step = sampleRate / BASIC_SAMPLE_RATE
            val reducedX = Array<FloatArray>(input.size) { FloatArray(0) }
            for ((index, channel) in input.withIndex()) {
                val newArr = FloatArray((channel.size + step - 1) / step)
                for ((j, i) in (channel.indices step step).withIndex()) {
                    newArr[j] = channel[i]
                }
                reducedX[index] = newArr
            }
            input = reducedX
            sr = BASIC_SAMPLE_RATE
        }
        if (!SAMPLE_RATES.contains(sr)) {
            throw IllegalArgumentException("Unsupported Sample Rate")
        }
        if (sr.toFloat() / input[0].size > 31.25) {
            throw IllegalArgumentException("Input audio is too short")
        }
        return ValidationResult(input, sampleRate)
    }

    fun call(x: Array<FloatArray>, sr: Int): FloatArray {
        val result = validateInput(x, sr)
        val input = result.input
        val sampleRate = result.sr

        val batchSize = input.size
        val numSamples = if (sampleRate == BASIC_SAMPLE_RATE) 512 else 256
        val contextSize = if (sampleRate == BASIC_SAMPLE_RATE) 64 else 32

        if (lastSr != 0 && lastSr != sampleRate) {
            resetStates(batchSize)
        } else if (lastBatchSize != 0 && lastBatchSize != batchSize) {
            resetStates(batchSize)
        } else if (lastBatchSize == 0) {
            lastBatchSize = batchSize
        }

        if (context.size == 0) {
            context = Array(batchSize) { FloatArray(contextSize) }
        }

        val xWithContext = Array(batchSize) { FloatArray(contextSize + numSamples) }
        for (i in 0 until batchSize) {
            System.arraycopy(context[i], 0, xWithContext[i], 0, contextSize)
            System.arraycopy(input[i], 0, xWithContext[i], contextSize, numSamples)
        }

        val env = OrtEnvironment.getEnvironment()
        var inputTensor: OnnxTensor? = null
        var stateTensor: OnnxTensor? = null
        var srTensor: OnnxTensor? = null
        var ortOutputs: OrtSession.Result? = null
        try {
            inputTensor = OnnxTensor.createTensor(env, xWithContext)
            stateTensor = OnnxTensor.createTensor(env, state)
            srTensor = OnnxTensor.createTensor(env, longArrayOf(sampleRate.toLong()))

            val inputs = mapOf(
                "input" to inputTensor, "sr" to srTensor, "state" to stateTensor
            )

            ortOutputs = session.run(inputs)
            val output = (ortOutputs.get(0).value) as Array<FloatArray>
            state = (ortOutputs.get(1).value) as Array<Array<FloatArray>>
            for (i in 0 until batchSize) {
                System.arraycopy(xWithContext[i], xWithContext[i].size - contextSize, context[i], 0, contextSize)
            }
            lastSr = sampleRate
            lastBatchSize = batchSize
            return output[0]
        } finally {
            inputTensor?.close()
            stateTensor?.close()
            srTensor?.close()
            ortOutputs?.close()
        }
    }

}