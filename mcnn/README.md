# Botzee

Botzee is a Minecraft Forge 1.8.9 client mod that learns a gameplay policy while recording is enabled and can later play by emitting vanilla keypresses only.

## Controls

The commands are client-side chat commands:

- `/botzee record` toggles recording gameplay examples.
- `/botzee learn` trains on recorded examples and enables reinforcement updates during play.
- `/botzee play` starts policy playback.
- `/botzee stop` stops playback and releases every key Botzee can control.
- `/botzee status` reports the current mode and training count.
- `/botzee clear` deletes all recorded gameplay samples from memory.

The trained neural policy is saved after each `/botzee learn` cycle at `.minecraft/botzee/policy.bin` and loaded automatically on startup. `/botzee clear` removes replay samples only; it does not delete the trained policy file.

The default client keybind `R` toggles recording and `T` trains the policy. Both can be changed in Minecraft's Controls menu under the `Botzee` category.

Botzee controls the vanilla inventory, drop, hotbar, movement, jump, sneak, attack, and use-item bindings. This gives the policy the key-level primitives needed to open inventories and shops, select purchases, and interact with post-game menus. It does not type arbitrary chat text or issue server commands; joining a new game is learned only through available keybind-driven menu interactions.

When Botzee is playing, a locally attributed Bedwars victory or loss automatically opens chat with `/play bedwars_eight_one` and submits it once after a short delay. This prevents the policy from getting stuck on a result screen. If the mapped chat key method is unavailable at runtime, the same exact command is sent through Minecraft's normal chat API as a compatibility fallback.

Botzee never writes player position, rotation, velocity, inventory, or world state. It only calls `KeyBinding.setKeyBindState` for the vanilla movement/action bindings.

## Bedwars learning

While a Bedwars scoreboard is visible, Botzee adds heuristic rewards from client-visible scoreboard and chat signals: entering a match, bed destruction, kills, final kills, resource indicators, victories, and defeats. These signals are deduplicated within each match and the detector resets when the Bedwars scoreboard disappears. Recording therefore captures the entire game as one continuous stream of state/action/reward samples.

Detection is adaptive: Botzee reads the formatted sidebar title as well as rows, recognizes changing `Kills`, `Final Kills`, `Beds Broken`, and `Winstreak` counters by numeric deltas, and accepts common first-person Hypixel messages such as `You killed`, `You won`, and `You were FINAL KILLED`. Notices use the colored `§8[§bBotzee§8]` prefix and only announce meaningful reward points.

The detector is intentionally client-only and heuristic. Hypixel can change scoreboard or chat wording, and Botzee does not inspect private server packets or automate clicks outside its vanilla keypress interface.

Chat rewards are accepted only when the local player's IGN appears in the message and the wording identifies the player as the positive actor, such as `IGN killed ...`, `IGN destroyed ...`, or `... by IGN`. This avoids training from another player's achievement or a message describing the local player's loss.

## Build

Use Java 8 and run `./gradlew setupDecompWorkspace`, then `./gradlew build`. The built jar is in `build/libs`.
