package com.botzee.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.lang.reflect.Method;
import java.io.File;
import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

public final class BotzeeController {
    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();
    private static final int ACTIONS = 19;
    private static final int INPUTS = 32;
    private static final int RECORD_LIMIT = 20000;
    private static final Random RANDOM = new Random();
    private static final List<Experience> REPLAY = new ArrayList<Experience>();
    private static final ModelManager MODELS = new ModelManager(new File(new File(MINECRAFT.mcDataDir, "botzee"), "models"), INPUTS, 24, ACTIONS);
    private static final BedwarsDetector BEDWARS = new BedwarsDetector();
    private static final KeyBinding RECORD_KEY = new KeyBinding("Botzee: Toggle recording", Keyboard.KEY_R, "Botzee");
    private static final KeyBinding TRAIN_KEY = new KeyBinding("Botzee: Train policy", Keyboard.KEY_T, "Botzee");
    private static final KeyBinding MODELS_KEY = new KeyBinding("Botzee: Open models", Keyboard.KEY_M, "Botzee");
    private static boolean recording;
    private static boolean playing;
    private static boolean reinforcement;
    private static long ticks;
    private static int actionsThisSecond;
    private static float previousHealth = 20.0F;
    private static float previousPlayHealth = 20.0F;
    private static int autoQueueDelay;
    private static GuiChat autoQueueChat;
    private static final String BEDWARS_QUEUE_COMMAND = "/play bedwars_eight_one";
    private static final File POLICY_FILE = new File(new File(MINECRAFT.mcDataDir, "botzee"), "policy.bin");
    private static final ChatScoreManager CHAT_SCORES = new ChatScoreManager(new File(new File(MINECRAFT.mcDataDir, "botzee"), "chat-rules.txt"));
    private static float pendingChatReward;

    private BotzeeController() { }

    public static void initialize() {
        ClientRegistry.registerKeyBinding(RECORD_KEY);
        ClientRegistry.registerKeyBinding(TRAIN_KEY);
        ClientRegistry.registerKeyBinding(MODELS_KEY);
        try {
            MODELS.active().load(POLICY_FILE);
        } catch (IOException ignored) {
            // A missing or unreadable policy starts with the deterministic initial weights.
        }
    }

    public static void register() {
        ClientCommandHandler.instance.registerCommand(new BotzeeCommand());
        MinecraftForge.EVENT_BUS.register(new BotzeeController());
        MinecraftForge.EVENT_BUS.register(BEDWARS);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || MINECRAFT.thePlayer == null) {
            return;
        }
        BEDWARS.update();
        processAutoQueue();
        if (recording) {
            recordStep();
        }
        if (playing && ++ticks % 2 == 0) {
            playStep();
        }
        if (playing && ticks % 20 == 0) {
            message("§fmodel=§b" + MODELS.activeName() + " §factions/s=§b" + actionsThisSecond + " §fsamples=§b" + REPLAY.size());
            actionsThisSecond = 0;
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String text = event.message == null ? "" : event.message.getUnformattedText();
        float reward = CHAT_SCORES.score(text);
        if (reward != 0.0F) {
            pendingChatReward += reward;
            message("§dCustom chat score §7(" + (reward >= 0.0F ? "+" : "") + reward + ")");
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (RECORD_KEY.isPressed()) {
            toggleRecording();
        }
        if (TRAIN_KEY.isPressed()) {
            learn();
        }
        if (MODELS_KEY.isPressed()) {
            openModels();
        }
    }

    static void detectorMessage(String text) {
        message(text);
    }

    static void queueBedwarsRematch() {
        if (!playing || autoQueueDelay > 0) {
            return;
        }
        autoQueueDelay = 10;
        message("Bedwars result detected; typing " + BEDWARS_QUEUE_COMMAND);
    }

    private static void processAutoQueue() {
        if (autoQueueDelay <= 0 || --autoQueueDelay > 0) {
            return;
        }
        autoQueueChat = new GuiChat(BEDWARS_QUEUE_COMMAND);
        MINECRAFT.displayGuiScreen(autoQueueChat);
        try {
            Method keyTyped = GuiChat.class.getDeclaredMethod("keyTyped", Character.TYPE, Integer.TYPE);
            keyTyped.setAccessible(true);
            keyTyped.invoke(autoQueueChat, '\n', Keyboard.KEY_RETURN);
            message("Queued for the next Bedwars game.");
        } catch (Exception exception) {
            MINECRAFT.thePlayer.sendChatMessage(BEDWARS_QUEUE_COMMAND);
            message("Queued for the next Bedwars game.");
        }
        autoQueueChat = null;
    }

    private void recordStep() {
        float[] state = observe();
        int action = actionMask();
        float reward = MINECRAFT.thePlayer.getHealth() - previousHealth + BEDWARS.getReward() + getChatReward();
        previousHealth = MINECRAFT.thePlayer.getHealth();
        if (REPLAY.size() == RECORD_LIMIT) {
            REPLAY.remove(0);
        }
        REPLAY.add(new Experience(state, action, reward, observe()));
    }

    private void playStep() {
        int action = MODELS.active().chooseAction(observe(), reinforcement);
        actionsThisSecond++;
        applyAction(action);
        if (reinforcement) {
            float healthDelta = MINECRAFT.thePlayer.getHealth() - previousPlayHealth + BEDWARS.getReward() + getChatReward();
            previousPlayHealth = MINECRAFT.thePlayer.getHealth();
            MODELS.active().reinforce(observe(), action, healthDelta);
            if (RANDOM.nextInt(20) == 0 && !REPLAY.isEmpty()) {
                Experience sample = REPLAY.get(RANDOM.nextInt(REPLAY.size()));
                MODELS.active().reinforce(sample.state, sample.action, sample.reward);
            }
        }
    }

    public static void toggleRecording() {
        recording = !recording;
        if (recording) {
            previousHealth = MINECRAFT.thePlayer == null ? 20.0F : MINECRAFT.thePlayer.getHealth();
        }
        message("Recording " + (recording ? "enabled" : "disabled") + " (" + REPLAY.size() + " samples)");
    }

    public static void learn() {
        if (REPLAY.isEmpty()) {
            message("Record gameplay first; Botzee has no examples yet.");
            return;
        }
        for (int pass = 0; pass < 8; pass++) {
            for (Experience sample : REPLAY) {
                MODELS.active().reinforce(sample.state, sample.action, sample.reward + 0.05F);
            }
        }
        reinforcement = true;
        try {
            MODELS.active().save(POLICY_FILE);
            MODELS.saveActive();
            message("Learned from " + REPLAY.size() + " samples; model " + MODELS.activeName() + " saved.");
        } catch (IOException exception) {
            message("Learned from " + REPLAY.size() + " samples, but policy save failed.");
        }
    }

    public static void clearRecording() {
        REPLAY.clear();
        message("Cleared all recorded gameplay data.");
    }

    static void openModels() { MINECRAFT.displayGuiScreen(new ModelScreen()); }
    static void openChatScores() { MINECRAFT.displayGuiScreen(new ChatScoreScreen()); }
    static String activeModel() { return MODELS.activeName(); }
    static List<String> modelNames() { return MODELS.names(); }
    static void modelMessage(String text) { message(text); }
    static List<ChatScoreManager.Rule> chatRules() { return CHAT_SCORES.rules(); }
    static void addChatRule(String text, float points, boolean regex) throws IOException { CHAT_SCORES.add(text, points, regex); CHAT_SCORES.save(); }
    static void updateChatRule(int index, String text, float points, boolean regex) throws IOException { CHAT_SCORES.update(index, text, points, regex); CHAT_SCORES.save(); }
    static void deleteChatRule(int index) throws IOException { CHAT_SCORES.remove(index); CHAT_SCORES.save(); }
    static boolean createModel(String name) throws IOException { return MODELS.create(name); }
    static boolean deleteActiveModel() throws IOException { return MODELS.deleteActive(); }
    static void selectModel(int index) {
        List<String> names = MODELS.names();
        if (index >= 0 && index < names.size()) {
            MODELS.select(names.get(index));
            message("Selected model " + MODELS.activeName() + ".");
        }
    }

    public static void play() {
        playing = true;
        ticks = 0;
        previousPlayHealth = MINECRAFT.thePlayer == null ? 20.0F : MINECRAFT.thePlayer.getHealth();
        message("Botzee is playing. Use /botzee stop to take control.");
    }

    public static void stop() {
        playing = false;
        autoQueueDelay = 0;
        autoQueueChat = null;
        releaseKeys();
        message("Botzee stopped and released its keypresses.");
    }

    public static String status() {
        return "recording=" + recording + ", playing=" + playing + ", reinforcement=" + reinforcement + ", samples=" + REPLAY.size() + ", " + BEDWARS.status();
    }

    private static float getChatReward() {
        float reward = pendingChatReward;
        pendingChatReward = 0.0F;
        return reward;
    }

    private static float[] observe() {
        float[] state = new float[INPUTS];
        state[0] = MINECRAFT.thePlayer.getHealth() / 20.0F;
        state[1] = MINECRAFT.thePlayer.onGround ? 1.0F : 0.0F;
        state[2] = MINECRAFT.thePlayer.isInWater() ? 1.0F : 0.0F;
        state[3] = MINECRAFT.thePlayer.isSneaking() ? 1.0F : 0.0F;
        state[4] = MINECRAFT.thePlayer.isSprinting() ? 1.0F : 0.0F;
        int offset = 5;
        KeyBinding[] keys = controlledKeys();
        for (int index = 0; index < keys.length; index++) {
            state[offset + index] = keys[index].isKeyDown() ? 1.0F : 0.0F;
        }
        float[] bedwarsState = BEDWARS.getFeatures();
        for (int index = 0; index < bedwarsState.length; index++) {
            state[offset + keys.length + index] = bedwarsState[index];
        }
        return state;
    }

    private static int actionMask() {
        int mask = 0;
        KeyBinding[] keys = controlledKeys();
        for (int index = 0; index < keys.length; index++) {
            if (keys[index].isKeyDown()) {
                mask |= 1 << index;
            }
        }
        return mask;
    }

    private static void applyAction(int mask) {
        KeyBinding[] keys = controlledKeys();
        for (int index = 0; index < keys.length; index++) {
            KeyBinding.setKeyBindState(keys[index].getKeyCode(), (mask & (1 << index)) != 0);
        }
    }

    private static void releaseKeys() { applyAction(0); }

    private static KeyBinding[] controlledKeys() {
        return new KeyBinding[] {
            MINECRAFT.gameSettings.keyBindForward, MINECRAFT.gameSettings.keyBindBack,
            MINECRAFT.gameSettings.keyBindLeft, MINECRAFT.gameSettings.keyBindRight,
            MINECRAFT.gameSettings.keyBindJump, MINECRAFT.gameSettings.keyBindSneak,
            MINECRAFT.gameSettings.keyBindAttack, MINECRAFT.gameSettings.keyBindUseItem,
            MINECRAFT.gameSettings.keyBindInventory, MINECRAFT.gameSettings.keyBindDrop,
            MINECRAFT.gameSettings.keyBindsHotbar[0], MINECRAFT.gameSettings.keyBindsHotbar[1],
            MINECRAFT.gameSettings.keyBindsHotbar[2], MINECRAFT.gameSettings.keyBindsHotbar[3],
            MINECRAFT.gameSettings.keyBindsHotbar[4], MINECRAFT.gameSettings.keyBindsHotbar[5],
            MINECRAFT.gameSettings.keyBindsHotbar[6], MINECRAFT.gameSettings.keyBindsHotbar[7],
            MINECRAFT.gameSettings.keyBindsHotbar[8]
        };
    }

    private static void message(String text) {
        if (MINECRAFT.thePlayer != null) {
            MINECRAFT.thePlayer.addChatMessage(new ChatComponentText("§8[§bBotzee§8] §7" + text));
        }
    }

    private static final class Experience {
        private final float[] state;
        private final int action;
        private final float reward;
        @SuppressWarnings("unused")
        private final float[] nextState;

        private Experience(float[] state, int action, float reward, float[] nextState) {
            this.state = state;
            this.action = action;
            this.reward = reward;
            this.nextState = nextState;
        }
    }
}
