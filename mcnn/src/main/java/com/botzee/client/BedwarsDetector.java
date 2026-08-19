package com.botzee.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Detects public Bedwars signals without reading or changing server state. */
final class BedwarsDetector {
    private static final int FEATURE_COUNT = 8;
    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();
    private static final Pattern NUMBER = Pattern.compile("(-?\\d+)");
    private final Set<String> observedSignals = new HashSet<String>();
    private final Set<String> knownLines = new HashSet<String>();
    private final float[] features = new float[FEATURE_COUNT];
    private boolean inBedwars;
    private boolean episodeStarted;
    private float pendingReward;
    private int bedBreaks;
    private int kills;
    private int finalKills;
    private int wins;
    private int losses;
    private int previousKills = -1;
    private int previousFinalKills = -1;
    private int previousBeds = -1;
    private int previousWinstreak = -1;
    private String scoreboardTitle = "";

    void update() {
        if (MINECRAFT.theWorld == null) {
            return;
        }
        Set<String> currentLines = scoreboardLines();
        boolean bedwarsScoreboard = containsBedwars(scoreboardTitle, currentLines);
        if (!bedwarsScoreboard && inBedwars) {
            resetEpisode();
        }
        inBedwars = bedwarsScoreboard;
        if (!inBedwars) {
            clearFeatures();
            return;
        }
        if (!episodeStarted) {
            episodeStarted = true;
            observedSignals.clear();
            knownLines.clear();
            pendingReward += 1.0F;
            BotzeeController.detectorMessage("§aBed Wars game detected §7(training episode started)");
        }
        for (String line : currentLines) {
            if (knownLines.add(line)) {
                detect(line, false);
            }
        }
        detectLeaderboardDelta(currentLines);
        updateFeatures(currentLines);
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String text = event.message == null ? "" : event.message.getUnformattedText();
        detect(text, true);
    }

    float[] getFeatures() {
        return features;
    }

    float getReward() {
        float reward = pendingReward;
        pendingReward = 0.0F;
        return reward;
    }

    String status() {
        return "bedwars=" + inBedwars + ", beds=" + bedBreaks + ", kills=" + kills + ", finalKills=" + finalKills + ", wins=" + wins + ", losses=" + losses;
    }

    private Set<String> scoreboardLines() {
        Set<String> lines = new HashSet<String>();
        scoreboardTitle = "";
        Scoreboard scoreboard = MINECRAFT.theWorld.getScoreboard();
        if (scoreboard == null) {
            return lines;
        }
        net.minecraft.scoreboard.ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return lines;
        }
        scoreboardTitle = clean(objective.getDisplayName());
        Collection<Score> scores = scoreboard.getSortedScores(objective);
        for (Score score : scores) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            lines.add(clean(line));
        }
        return lines;
    }

    private boolean containsBedwars(String title, Set<String> lines) {
        if (title.contains("bed wars") || title.contains("bedwars")) {
            return true;
        }
        for (String line : lines) {
            if (line.contains("bed wars") || line.contains("bedwars")
                    || line.contains("kills") || line.contains("beds broken")
                    || line.contains("diamond ii")) {
                return true;
            }
        }
        return false;
    }

    private void detect(String rawText, boolean chatSignal) {
        String text = clean(rawText);
        if (text.length() == 0) {
            return;
        }
        if (chatSignal && !isPositivePlayerMessage(text)) {
            if (isLocalLoss(text)) {
                detectOutcome(text);
            }
            return;
        }
        detectOutcome(text);
        if (text.contains("bed destroyed") || text.contains("bed gone") || text.contains("bed was destroyed")) {
            scoreOnce("bed:" + text, 20.0F);
            bedBreaks++;
        }
        if (text.contains("final kill")) {
            scoreOnce("final:" + text, 15.0F);
            finalKills++;
        } else if (text.contains("killed") || text.contains("was slain") || text.contains("eliminated")) {
            scoreOnce("kill:" + text, 8.0F);
            kills++;
        }
        if (text.contains("diamond") || text.contains("emerald") || text.contains("gold") || text.contains("iron")) {
            scoreOnce("resource:" + text, 1.0F);
        }
    }

    private void scoreOnce(String signal, float reward) {
        if (observedSignals.add(signal)) {
            pendingReward += reward;
            String kind = signal.substring(0, signal.indexOf(':'));
            BotzeeController.detectorMessage("§dRL point generator §7" + kind + " §f(" + formatReward(reward) + ")");
            if (signal.startsWith("win:") || signal.startsWith("loss:")) {
                BotzeeController.queueBedwarsRematch();
            }
        }
    }

    private void detectOutcome(String text) {
        if (text.contains("you win") || text.contains("you won") || text.contains("victory") || text.contains("winner")
            || text.contains("game over - you won")) {
            scoreOnce("win:" + text, 100.0F);
            wins++;
        }
        if (isLocalLoss(text)) {
            scoreOnce("loss:" + text, -50.0F);
            losses++;
        }
    }

    private boolean isLocalLoss(String text) {
        if (MINECRAFT.thePlayer == null) {
            return false;
        }
        String ign = clean(MINECRAFT.thePlayer.getName());
        return (text.contains("you lose") || text.contains("game over") || text.contains("defeat")
                || text.contains("you died") || text.contains("you were killed")
                || text.contains("you were final killed") || text.contains("you were eliminated"))
                || (ign.length() > 0 && text.contains(ign)
                && (text.contains(ign + " was killed") || text.contains(ign + " was slain")
                || text.contains(ign + " was eliminated") || text.contains(ign + " died")
                || text.contains(ign + " lost") || text.contains(ign + "'s bed was destroyed")));
    }

    private boolean isPositivePlayerMessage(String text) {
        if (MINECRAFT.thePlayer == null) {
            return false;
        }
        String ign = clean(MINECRAFT.thePlayer.getName());
        if (text.contains("you killed") || text.contains("you final killed")
                || text.contains("you destroyed") || text.contains("you won")
                || text.contains("you win") || text.contains("victory")) return true;
        if (ign.length() == 0 || !text.contains(ign)) return false;
        if (text.contains(ign + " was killed") || text.contains(ign + " was slain")
            || text.contains(ign + " was eliminated") || text.contains(ign + " died")
            || text.contains(ign + " lost") || text.contains(ign + "'s bed was destroyed")) {
            return false;
        }
        return text.contains(ign + " killed") || text.contains(ign + " eliminated")
                || text.contains(ign + " destroyed") || text.contains("by " + ign)
                || text.contains(ign + " won") || text.contains(ign + " victory")
                || text.contains("you win") || text.contains("victory");
    }

    private void detectLeaderboardDelta(Set<String> lines) {
        int currentKills = metric(lines, "kills");
        int currentFinalKills = metric(lines, "final kills");
        int currentBeds = metric(lines, "beds broken");
        int currentWinstreak = metric(lines, "winstreak");
        if (previousKills >= 0 && currentKills > previousKills) {
            scoreOnce("leaderboard-kill:" + currentKills, 8.0F);
            kills += currentKills - previousKills;
        }
        if (previousFinalKills >= 0 && currentFinalKills > previousFinalKills) {
            scoreOnce("leaderboard-final-kill:" + currentFinalKills, 15.0F);
            finalKills += currentFinalKills - previousFinalKills;
        }
        if (previousBeds >= 0 && currentBeds > previousBeds) {
            scoreOnce("leaderboard-bed:" + currentBeds, 20.0F);
            bedBreaks += currentBeds - previousBeds;
        }
        if (previousWinstreak >= 0 && currentWinstreak > previousWinstreak) {
            scoreOnce("leaderboard-winstreak:" + currentWinstreak, 10.0F);
        }
        previousKills = currentKills;
        previousFinalKills = currentFinalKills;
        previousBeds = currentBeds;
        previousWinstreak = currentWinstreak;
    }

    private int metric(Set<String> lines, String label) {
        for (String line : lines) {
            if (line.contains(label)) {
                Matcher matcher = NUMBER.matcher(line);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        }
        return -1;
    }

    private static String formatReward(float reward) {
        return reward >= 0.0F ? "+" + reward : Float.toString(reward);
    }

    private void updateFeatures(Set<String> lines) {
        clearFeatures();
        features[0] = 1.0F;
        features[1] = Math.min(1.0F, bedBreaks / 4.0F);
        features[2] = Math.min(1.0F, kills / 10.0F);
        features[3] = Math.min(1.0F, finalKills / 10.0F);
        features[4] = wins > 0 ? 1.0F : 0.0F;
        features[5] = losses > 0 ? 1.0F : 0.0F;
        for (String line : lines) {
            if (line.contains("diamond")) features[6] = 1.0F;
            if (line.contains("emerald")) features[7] = 1.0F;
        }
    }

    private void clearFeatures() {
        for (int index = 0; index < features.length; index++) {
            features[index] = 0.0F;
        }
    }

    private void resetEpisode() {
        inBedwars = false;
        episodeStarted = false;
        observedSignals.clear();
        knownLines.clear();
        bedBreaks = 0;
        kills = 0;
        finalKills = 0;
        wins = 0;
        losses = 0;
        previousKills = -1;
        previousFinalKills = -1;
        previousBeds = -1;
        previousWinstreak = -1;
        scoreboardTitle = "";
        clearFeatures();
    }

    private static String clean(String text) {
        return text.replaceAll("\\u00a7[0-9a-fk-or]", "").toLowerCase(Locale.ENGLISH).trim();
    }
}