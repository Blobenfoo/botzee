package com.botzee.client;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

final class ChatScoreScreen extends GuiScreen {
    private GuiTextField textField;
    private GuiTextField pointsField;
    private boolean regularExpression;
    private int selected = -1;

    @Override
    public void initGui() {
        buttonList.clear();
        textField = new GuiTextField(0, fontRendererObj, 20, height - 70, width - 140, 20);
        pointsField = new GuiTextField(1, fontRendererObj, width - 110, height - 70, 90, 20);
        buttonList.add(new GuiButton(100, 20, height - 44, 105, 20, "Add rule"));
        buttonList.add(new GuiButton(101, 130, height - 44, 105, 20, "Update rule"));
        buttonList.add(new GuiButton(102, 240, height - 44, 105, 20, "Delete rule"));
        buttonList.add(new GuiButton(103, 350, height - 44, 125, 20, "Mode: text"));
        buttonList.add(new GuiButton(104, width - 105, height - 44, 85, 20, "Done"));
        rebuildRuleButtons();
    }

    private void rebuildRuleButtons() {
        for (int index = buttonList.size() - 1; index >= 0; index--) if (buttonList.get(index).id >= 200) buttonList.remove(index);
        List<ChatScoreManager.Rule> rules = BotzeeController.chatRules();
        for (int index = 0; index < rules.size(); index++) {
            ChatScoreManager.Rule rule = rules.get(index);
            String label = (index == selected ? "> " : "") + (rule.regularExpression ? "regex: " : "text: ")
                    + rule.text + " (" + rule.points + ")";
            buttonList.add(new GuiButton(200 + index, 20, 30 + index * 22, width - 40, 20, label));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        try {
            if (button.id == 100 || button.id == 101) {
                float points = Float.parseFloat(pointsField.getText().trim());
                String text = textField.getText();
                if (!ChatScoreManager.validPattern(text, regularExpression) || !ChatScoreManager.validPoints(points)) throw new IllegalArgumentException();
                if (button.id == 100) BotzeeController.addChatRule(text, points, regularExpression);
                else if (selected >= 0) BotzeeController.updateChatRule(selected, text, points, regularExpression);
                rebuildRuleButtons();
            } else if (button.id == 102 && selected >= 0) {
                BotzeeController.deleteChatRule(selected);
                selected = -1;
                rebuildRuleButtons();
            } else if (button.id == 103) {
                regularExpression = !regularExpression;
                button.displayString = regularExpression ? "Mode: regex" : "Mode: text";
            } else if (button.id == 104) {
                mc.displayGuiScreen(null);
            } else if (button.id >= 200) {
                selected = button.id - 200;
                ChatScoreManager.Rule rule = BotzeeController.chatRules().get(selected);
                textField.setText(rule.text);
                pointsField.setText(Float.toString(rule.points));
                regularExpression = rule.regularExpression;
                ((GuiButton) buttonList.get(3)).displayString = regularExpression ? "Mode: regex" : "Mode: text";
                rebuildRuleButtons();
            }
        } catch (IOException exception) {
            BotzeeController.modelMessage("Could not save chat scoring rules.");
        } catch (NumberFormatException exception) {
            BotzeeController.modelMessage("Points must be a number.");
        } catch (IllegalArgumentException exception) {
            BotzeeController.modelMessage("Enter text and a valid regular expression.");
        }
    }

    @Override
    protected void keyTyped(char character, int keyCode) throws IOException {
        textField.textboxKeyTyped(character, keyCode);
        pointsField.textboxKeyTyped(character, keyCode);
        super.keyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        textField.mouseClicked(mouseX, mouseY, mouseButton);
        pointsField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Chat scoring rules", width / 2, 10, 0x55FFFF);
        drawString(fontRendererObj, "Message or expression", 20, height - 82, 0xFFFFFF);
        drawString(fontRendererObj, "Points", width - 110, height - 82, 0xFFFFFF);
        textField.drawTextBox();
        pointsField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}