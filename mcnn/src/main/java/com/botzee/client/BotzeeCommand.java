package com.botzee.client;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public final class BotzeeCommand extends CommandBase {
    @Override
    public String getCommandName() { return "botzee"; }

    @Override
    public String getCommandUsage(ICommandSender sender) { return "/botzee <record|learn|play|stop|status|clear|models|scores>"; }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("record")) {
            BotzeeController.toggleRecording();
        } else if (args.length == 1 && args[0].equalsIgnoreCase("learn")) {
            BotzeeController.learn();
        } else if (args.length == 1 && args[0].equalsIgnoreCase("play")) {
            BotzeeController.play();
        } else if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
            BotzeeController.stop();
        } else if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.addChatMessage(new ChatComponentText("[Botzee] " + BotzeeController.status()));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            BotzeeController.clearRecording();
        } else if (args.length == 1 && args[0].equalsIgnoreCase("models")) {
            BotzeeController.openModels();
        } else if (args.length == 1 && (args[0].equalsIgnoreCase("scores") || args[0].equalsIgnoreCase("score"))) {
            BotzeeController.openChatScores();
        } else {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
        }
    }

    @Override
    public int getRequiredPermissionLevel() { return 0; }
}
