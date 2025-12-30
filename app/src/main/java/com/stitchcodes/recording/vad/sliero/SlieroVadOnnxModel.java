package com.stitchcodes.recording.vad.sliero;

import android.content.Context;
import android.util.Log;

import androidx.activity.contextaware.ContextAware;

import com.stitchcodes.recording.ContextHolder;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Silero VAD ONNX Model Wrapper
 *
 * @author VvvvvGH
 */
public class SlieroVadOnnxModel {
    private static final String TAG = "SlieroVadOnnxModel";
    // ONNX runtime session
    private final OrtSession session;
    // Model state - dimensions: [2, batch_size, 128]
    private float[][][] state;
    // Context - stores the tail of the previous audio chunk
    private float[][] context;
    // Last sample rate
    private int lastSr = 0;
    // Last batch size
    private int lastBatchSize = 0;
    // Supported sample rates
    private static final List<Integer> SAMPLE_RATES = Arrays.asList(8000, 16000);

    // Constructor
    public SlieroVadOnnxModel() throws OrtException, IOException {
        // Get the ONNX runtime environment
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        // Create ONNX session options
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        // Set InterOp thread count to 1 (for parallel processing of different graph operations)
        opts.setInterOpNumThreads(1);
        // Set IntraOp thread count to 1 (for parallel processing within a single operation)
        opts.setIntraOpNumThreads(1);
        // Enable CPU execution optimization
        opts.addCPU(true);
        // Create ONNX session with the environment, model path, and options
        byte[] bytes = readAssetToBytes(ContextHolder.INSTANCE.appContext(), "silero_vad.onnx");
        session = env.createSession(bytes, opts);
        // Reset states
        resetStates();
    }

    public static byte[] readAssetToBytes(Context context, String assetPath) throws IOException {
        InputStream is = context.getAssets().open(assetPath);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        is.close();
        return buffer.toByteArray();
    }

    /**
     * Reset states with default batch size
     */
    void resetStates() {
        resetStates(1);
    }

    /**
     * Reset states with specific batch size
     *
     * @param batchSize Batch size for state initialization
     */
    void resetStates(int batchSize) {
        state = new float[2][batchSize][128];
        context = new float[0][]; // Empty context
        lastSr = 0;
        lastBatchSize = 0;
    }

    public void close() throws OrtException {
        session.close();
    }

    /**
     * Inner class for validation result
     */
    public static class ValidationResult {
        public final float[][] x;
        public final int sr;

        public ValidationResult(float[][] x, int sr) {
            this.x = x;
            this.sr = sr;
        }
    }

    /**
     * Validate input data
     *
     * @param x  Audio data array
     * @param sr Sample rate
     * @return Validated input data and sample rate
     */
    private ValidationResult validateInput(float[][] x, int sr) {
        // Ensure input is at least 2D
        if (x.length == 1) {
            x = new float[][]{x[0]};
        }
        // Check if input dimension is valid
        if (x.length > 2) {
            throw new IllegalArgumentException("Incorrect audio data dimension: " + x[0].length);
        }

        // Downsample if sample rate is a multiple of 16000
        if (sr != 16000 && (sr % 16000 == 0)) {
            int step = sr / 16000;
            float[][] reducedX = new float[x.length][];

            for (int i = 0; i < x.length; i++) {
                float[] current = x[i];
                float[] newArr = new float[(current.length + step - 1) / step];

                for (int j = 0, index = 0; j < current.length; j += step, index++) {
                    newArr[index] = current[j];
                }

                reducedX[i] = newArr;
            }

            x = reducedX;
            sr = 16000;
        }

        // Validate sample rate
        if (!SAMPLE_RATES.contains(sr)) {
            throw new IllegalArgumentException("Only supports sample rates " + SAMPLE_RATES + " (or multiples of 16000)");
        }

        // Check if audio chunk is too short
        if (((float) sr) / x[0].length > 31.25) {
            throw new IllegalArgumentException("Input audio is too short");
        }

        return new ValidationResult(x, sr);
    }

    /**
     * Call the ONNX model for inference
     *
     * @param x  Audio data array
     * @param sr Sample rate
     * @return Speech probability output
     * @throws OrtException If ONNX runtime error occurs
     */
    public float[] call(float[][] x, int sr) throws OrtException {
        ValidationResult result = validateInput(x, sr);
        x = result.x;
        sr = result.sr;

        int batchSize = x.length;
        int numSamples = sr == 16000 ? 512 : 256;
        int contextSize = sr == 16000 ? 64 : 32;

        // Reset states only when sample rate or batch size changes
        if (lastSr != 0 && lastSr != sr) {
            resetStates(batchSize);
        } else if (lastBatchSize != 0 && lastBatchSize != batchSize) {
            resetStates(batchSize);
        } else if (lastBatchSize == 0) {
            // First call - state is already initialized, just set batch size
            lastBatchSize = batchSize;
        }

        // Initialize context if needed
        if (context.length == 0) {
            context = new float[batchSize][contextSize];
        }

        // Concatenate context and input
        float[][] xWithContext = new float[batchSize][contextSize + numSamples];
        for (int i = 0; i < batchSize; i++) {
            // Copy context
            System.arraycopy(context[i], 0, xWithContext[i], 0, contextSize);
            // Copy input
            System.arraycopy(x[i], 0, xWithContext[i], contextSize, numSamples);
        }

        OrtEnvironment env = OrtEnvironment.getEnvironment();

        OnnxTensor inputTensor = null;
        OnnxTensor stateTensor = null;
        OnnxTensor srTensor = null;
        OrtSession.Result ortOutputs = null;

        try {
            // Create input tensors
            inputTensor = OnnxTensor.createTensor(env, xWithContext);
            stateTensor = OnnxTensor.createTensor(env, state);
            srTensor = OnnxTensor.createTensor(env, new long[]{sr});

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input", inputTensor);
            inputs.put("sr", srTensor);
            inputs.put("state", stateTensor);

            // Run ONNX model inference
            ortOutputs = session.run(inputs);
            // Get output results
            float[][] output = (float[][]) ortOutputs.get(0).getValue();
            state = (float[][][]) ortOutputs.get(1).getValue();

            // Update context - save the last contextSize samples from input
            for (int i = 0; i < batchSize; i++) {
                System.arraycopy(xWithContext[i], xWithContext[i].length - contextSize, context[i], 0, contextSize);
            }

            lastSr = sr;
            lastBatchSize = batchSize;
            return output[0];
        } finally {
            if (inputTensor != null) {
                inputTensor.close();
            }
            if (stateTensor != null) {
                stateTensor.close();
            }
            if (srTensor != null) {
                srTensor.close();
            }
            if (ortOutputs != null) {
                ortOutputs.close();
            }
        }
    }
}
