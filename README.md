# Botzee

Botzee is a client-only Minecraft Forge 1.8.9 mod that records gameplay state and keypresses, trains a small neural policy, and can play by holding vanilla keybindings. It does not read or modify server state, player coordinates, inventory contents, rotations, or packets.

## Installation

Copy the built `botzee-<version>.jar` into the instance's `mods` directory. Botzee requires Minecraft 1.8.9 and Forge 11.15.1.2318. Java 8 is recommended for this legacy Forge/LWJGL stack.

## Keybinds

The defaults are registered under Minecraft's **Controls** menu in the **Botzee** category:

- `R`: Toggle recording.
- `]`: Train the active policy from the recorded samples.
- `M`: Open the model list and model management screen.

All three keybinds can be changed in Minecraft's Controls menu. The `]` default avoids conflicting with Minecraft's `T` chat key.

## Commands

All commands are client-side commands and use the `/botzee` prefix:

- `/botzee record`: Toggle recording. While enabled, Botzee stores state, current key actions, reward, and next state samples, up to 20,000 samples.
- `/botzee learn`: Train the active model for several passes over the recorded samples, save it, and enable reinforcement updates during playback. If there are no samples, nothing is trained.
- `/botzee play`: Start policy playback. Botzee applies the model's selected movement, action, inventory, drop, and hotbar keybindings.
- `/botzee stop`: Stop playback, cancel automatic rematch queuing, and release all keys controlled by Botzee.
- `/botzee status`: Show recording/playback state, reinforcement state, sample count, and Bedwars statistics.
- `/botzee clear`: Delete recorded samples from memory. This does not delete saved models or policy files.
- `/botzee models`: Open the model management GUI.
- `/botzee scores` or `/botzee score`: Open the custom chat scoring GUI.

## Model management

The model screen can:

- Select the active named model.
- Create a model by entering a name and pressing **Create**.
- Delete the selected model, except for the built-in `default` model.
- Open the chat scoring rules screen.

The active policy is saved at `.minecraft/botzee/policy.bin`. Named models are stored at `.minecraft/botzee/models/*.bin`. The default model is created automatically when no model exists.

## Custom chat scoring

Open the screen with `/botzee scores` or through **M -> Chat scoring rules**. Each rule contains:

- A message fragment or regular expression.
- A point value, which may be positive or negative.
- A matching mode: literal text or regex.

Literal text matching is case-insensitive and matches any message containing the text. Regex matching is case-insensitive and uses Java regular expressions. Invalid expressions and non-finite point values are rejected. Select an existing rule to load it into the fields, then use **Update rule** or **Delete rule**.

Rules are saved at `.minecraft/botzee/chat-rules.txt`. When a received chat message matches one or more rules, the points are added to the pending reward used by recording and reinforcement playback. Multiple matching rules stack. Botzee displays the resulting custom score in local chat.

## Bedwars rewards

When a Bedwars scoreboard is visible, Botzee adds heuristic rewards for entering a game, beds destroyed, kills, final kills, resources, victories, defeats, and changing scoreboard counters such as `Kills`, `Final Kills`, `Beds Broken`, and `Winstreak`. Signals are deduplicated during each match and reset when the Bedwars scoreboard disappears.

The detector reads client-visible scoreboard and chat text only. Chat wording can change, so these rewards are heuristic rather than authoritative. A locally attributed victory or loss while playing schedules one `/play bedwars_eight_one` rematch command after a short delay. The fallback sends the same command through Minecraft's normal chat API if the chat-screen method is unavailable.

## Controlled actions and safety

The policy can control forward, back, left, right, jump, sneak, attack, use item, inventory, drop, and all nine hotbar slots through vanilla `KeyBinding` state. It does not type arbitrary chat text, issue arbitrary server commands, click GUI controls directly, or write player/world state. Use `/botzee stop` to immediately release its controlled keys.

## Build

Use Java 8 for this legacy ForgeGradle project:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" ./gradlew build
```

The release jar is written to `build/libs/`. A deobfuscated development workspace can be prepared with `./gradlew setupDecompWorkspace` before building.
