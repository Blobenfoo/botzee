package com.botzee.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ModelManager {
    private static final String DEFAULT_MODEL = "default";
    private final File directory;
    private final int inputCount;
    private final int hiddenCount;
    private final int outputCount;
    private final Map<String, NeuralPolicy> models = new LinkedHashMap<String, NeuralPolicy>();
    private String activeName = DEFAULT_MODEL;

    ModelManager(File directory, int inputCount, int hiddenCount, int outputCount) {
        this.directory = directory;
        this.inputCount = inputCount;
        this.hiddenCount = hiddenCount;
        this.outputCount = outputCount;
        loadModels();
    }

    NeuralPolicy active() { return models.get(activeName); }
    String activeName() { return activeName; }
    List<String> names() { return new ArrayList<String>(models.keySet()); }

    boolean select(String name) {
        if (!models.containsKey(name)) return false;
        activeName = name;
        return true;
    }

    boolean create(String name) throws IOException {
        String normalized = normalize(name);
        if (normalized.length() == 0 || models.containsKey(normalized)) return false;
        models.put(normalized, new NeuralPolicy(inputCount, hiddenCount, outputCount));
        activeName = normalized;
        saveActive();
        return true;
    }

    boolean deleteActive() throws IOException {
        if (DEFAULT_MODEL.equals(activeName)) return false;
        File file = fileFor(activeName);
        if (file.exists() && !file.delete()) throw new IOException("Could not delete " + file);
        models.remove(activeName);
        activeName = DEFAULT_MODEL;
        return true;
    }

    void saveActive() throws IOException { active().save(fileFor(activeName)); }

    private void loadModels() {
        if (!directory.exists()) directory.mkdirs();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.getName().endsWith(".bin")) continue;
                String name = file.getName().substring(0, file.getName().length() - 4);
                NeuralPolicy policy = new NeuralPolicy(inputCount, hiddenCount, outputCount);
                try { if (policy.load(file)) models.put(name, policy); } catch (IOException ignored) { }
            }
        }
        if (!models.containsKey(DEFAULT_MODEL)) models.put(DEFAULT_MODEL, new NeuralPolicy(inputCount, hiddenCount, outputCount));
    }

    private File fileFor(String name) { return new File(directory, normalize(name) + ".bin"); }

    private static String normalize(String name) {
        String value = name == null ? "" : name.trim().toLowerCase();
        return value.replaceAll("[^a-z0-9_-]", "_").substring(0, Math.min(24, value.replaceAll("[^a-z0-9_-]", "_").length()));
    }
}