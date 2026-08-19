package com.botzee.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

final class NeuralPolicy {
    private static final int FILE_MAGIC = 0x42545A31;
    private final int hiddenCount;
    private final int outputCount;
    private final float[][] inputWeights;
    private final float[][] outputWeights;
    private final Random random = new Random(0xB07EE);

    NeuralPolicy(int inputCount, int hiddenCount, int outputCount) {
        this.hiddenCount = hiddenCount;
        this.outputCount = outputCount;
        inputWeights = new float[hiddenCount][inputCount];
        outputWeights = new float[outputCount][hiddenCount];
        for (int hidden = 0; hidden < hiddenCount; hidden++) {
            for (int input = 0; input < inputCount; input++) inputWeights[hidden][input] = randomWeight();
        }
        for (int output = 0; output < outputCount; output++) {
            for (int hidden = 0; hidden < hiddenCount; hidden++) outputWeights[output][hidden] = randomWeight();
        }
    }

    int chooseAction(float[] state, boolean exploratory) {
        float[] hidden = hidden(state);
        int mask = 0;
        for (int output = 0; output < outputCount; output++) {
            boolean explore = exploratory && random.nextFloat() < 0.05F;
            if (explore || sigmoid(dot(outputWeights[output], hidden)) > (exploratory ? 0.58F : 0.5F)) mask |= 1 << output;
        }
        return mask;
    }

    void reinforce(float[] state, int action, float reward) {
        float[] hidden = hidden(state);
        float rate = Math.max(-0.03F, Math.min(0.03F, reward * 0.01F + 0.002F));
        for (int output = 0; output < outputCount; output++) {
            float target = (action & (1 << output)) == 0 ? 0.0F : 1.0F;
            float prediction = sigmoid(dot(outputWeights[output], hidden));
            float delta = (target - prediction) * rate;
            for (int hiddenIndex = 0; hiddenIndex < hiddenCount; hiddenIndex++) {
                outputWeights[output][hiddenIndex] += delta * hidden[hiddenIndex];
            }
        }
    }

    void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)));
        try {
            output.writeInt(FILE_MAGIC);
            output.writeInt(inputWeights[0].length);
            output.writeInt(hiddenCount);
            output.writeInt(outputCount);
            for (int hidden = 0; hidden < hiddenCount; hidden++) {
                for (int input = 0; input < inputWeights[hidden].length; input++) output.writeFloat(inputWeights[hidden][input]);
            }
            for (int outputIndex = 0; outputIndex < outputCount; outputIndex++) {
                for (int hidden = 0; hidden < hiddenCount; hidden++) output.writeFloat(outputWeights[outputIndex][hidden]);
            }
        } finally {
            output.close();
        }
        if (file.exists() && !file.delete()) throw new IOException("Could not replace " + file);
        if (!temporary.renameTo(file)) throw new IOException("Could not move " + temporary + " to " + file);
    }

    boolean load(File file) throws IOException {
        if (!file.isFile()) return false;
        DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        try {
            if (input.readInt() != FILE_MAGIC || input.readInt() != inputWeights[0].length
                    || input.readInt() != hiddenCount || input.readInt() != outputCount) return false;
            for (int hidden = 0; hidden < hiddenCount; hidden++) {
                for (int inputIndex = 0; inputIndex < inputWeights[hidden].length; inputIndex++) inputWeights[hidden][inputIndex] = input.readFloat();
            }
            for (int outputIndex = 0; outputIndex < outputCount; outputIndex++) {
                for (int hidden = 0; hidden < hiddenCount; hidden++) outputWeights[outputIndex][hidden] = input.readFloat();
            }
            return true;
        } finally {
            input.close();
        }
    }

    private float[] hidden(float[] state) {
        float[] values = new float[hiddenCount];
        for (int hidden = 0; hidden < hiddenCount; hidden++) {
            values[hidden] = (float) Math.tanh(dot(inputWeights[hidden], state));
        }
        return values;
    }

    private static float dot(float[] left, float[] right) {
        float result = 0.0F;
        for (int index = 0; index < left.length; index++) result += left[index] * right[index];
        return result;
    }

    private float randomWeight() { return (random.nextFloat() - 0.5F) * 0.2F; }
    private static float sigmoid(float value) { return 1.0F / (1.0F + (float) Math.exp(-value)); }
}
