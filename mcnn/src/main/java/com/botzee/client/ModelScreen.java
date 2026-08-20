package com.botzee.client;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

final class ModelScreen extends GuiScreen {
    private GuiTextField nameField;
    private int modelStart;

    @Override
    public void initGui() {
        buttonList.clear();
        nameField = new GuiTextField(0, fontRendererObj, width / 2 - 100, height - 48, 130, 20);
        nameField.setFocused(true);
        buttonList.add(new GuiButton(100, width / 2 + 35, height - 48, 65, 20, "Create"));
        buttonList.add(new GuiButton(102, width / 2 - 100, height - 72, 200, 20, "Chat scoring rules"));
        buttonList.add(new GuiButton(101, width / 2 - 100, height - 24, 200, 20, "Delete selected"));
        rebuildModelButtons();
    }

    private void rebuildModelButtons() {
        modelStart = 0;
        for (int index = buttonList.size() - 1; index >= 0; index--) if (buttonList.get(index).id >= 200) buttonList.remove(index);
        List<String> names = BotzeeController.modelNames();
        for (int index = 0; index < names.size(); index++) {
            buttonList.add(new GuiButton(200 + index, width / 2 - 100, 35 + index * 22, 200, 20,
                    (names.get(index).equals(BotzeeController.activeModel()) ? "* " : "") + names.get(index)));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        try {
            if (button.id == 100) {
                if (BotzeeController.createModel(nameField.getText())) { nameField.setText(""); rebuildModelButtons(); }
            } else if (button.id == 101) {
                BotzeeController.deleteActiveModel(); rebuildModelButtons();
            } else if (button.id == 102) {
                BotzeeController.openChatScores();
            } else if (button.id >= 200) {
                BotzeeController.selectModel(button.id - 200); rebuildModelButtons();
            }
        } catch (IOException exception) {
            BotzeeController.modelMessage("Model operation failed.");
        }
    }

    @Override
    protected void keyTyped(char character, int keyCode) throws IOException {
        nameField.textboxKeyTyped(character, keyCode);
        super.keyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Botzee Models", width / 2, 12, 0x55FFFF);
        nameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}