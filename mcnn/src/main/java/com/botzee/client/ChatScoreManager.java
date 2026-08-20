package com.botzee.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class ChatScoreManager {
    private final File file;
    private final List<Rule> rules = new ArrayList<Rule>();

    ChatScoreManager(File file) {
        this.file = file;
        load();
    }

    List<Rule> rules() { return new ArrayList<Rule>(rules); }

    void add(String text, float points, boolean regularExpression) {
        rules.add(new Rule(text, points, regularExpression));
    }

    void update(int index, String text, float points, boolean regularExpression) {
        rules.set(index, new Rule(text, points, regularExpression));
    }

    void remove(int index) { rules.remove(index); }

    float score(String message) {
        float total = 0.0F;
        for (Rule rule : rules) {
            if (rule.matches(message)) total += rule.points;
        }
        return total;
    }

    void save() throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
        BufferedWriter output = new BufferedWriter(new FileWriter(file));
        try {
            output.write("BOTZEE_CHAT_RULES_1");
            output.newLine();
            for (Rule rule : rules) {
                output.write(encode(rule.text));
                output.write('\t');
                output.write(Float.toString(rule.points));
                output.write('\t');
                output.write(rule.regularExpression ? "regex" : "text");
                output.newLine();
            }
        } finally {
            output.close();
        }
    }

    static boolean validPattern(String text, boolean regularExpression) {
        if (text == null || text.trim().length() == 0) return false;
        if (!regularExpression) return true;
        try {
            Pattern.compile(text, Pattern.CASE_INSENSITIVE);
            return true;
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }

    private void load() {
        if (!file.isFile()) return;
        try {
            BufferedReader input = new BufferedReader(new FileReader(file));
            try {
                if (!"BOTZEE_CHAT_RULES_1".equals(input.readLine())) return;
                String line;
                while ((line = input.readLine()) != null) {
                    String[] fields = line.split("\\t");
                    if (fields.length != 3) continue;
                    try {
                        String text = decode(fields[0]);
                        float points = Float.parseFloat(fields[1]);
                        boolean regex = "regex".equals(fields[2]);
                        if (validPattern(text, regex) && validPoints(points)) rules.add(new Rule(text, points, regex));
                    } catch (IllegalArgumentException ignored) { }
                }
            } finally {
                input.close();
            }
        } catch (IOException ignored) { }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static boolean validPoints(float points) {
        return !Float.isNaN(points) && !Float.isInfinite(points);
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    static final class Rule {
        final String text;
        final float points;
        final boolean regularExpression;
        private final Pattern pattern;

        Rule(String text, float points, boolean regularExpression) {
            this.text = text;
            this.points = points;
            this.regularExpression = regularExpression;
            pattern = regularExpression ? Pattern.compile(text, Pattern.CASE_INSENSITIVE) : null;
        }

        boolean matches(String message) {
            if (regularExpression) return pattern.matcher(message).find();
            return message.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
        }
    }
}