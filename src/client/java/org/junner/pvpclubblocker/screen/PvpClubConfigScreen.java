package org.junner.pvpclubblocker.screen;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.screens.Screen;
import org.junner.pvpclubblocker.BlockManager;
import org.junner.pvpclubblocker.PvpClubBlockerClient;

import java.util.List;

public final class PvpClubConfigScreen extends GuiConfigsBase {
	private final BlockManager manager;

	public PvpClubConfigScreen(Screen parent, BlockManager manager) {
		super(10, 50, PvpClubBlockerClient.MOD_ID, parent, "PvPClub Blocker");
		this.manager = manager;
		this.useTitleHierarchy = false;
		this.setParent(parent);
	}

	@Override
	public void initGui() {
		super.initGui();
		this.clearOptions();

		int tabWidth = (getScreenWidth() - 20) / 2;
		ButtonGeneric configuration = addButton(new ButtonGeneric(10, 22, tabWidth, 20, "Configuration"),
			(button, mouseButton) -> {});
		configuration.setEnabled(false);
		addButton(new ButtonGeneric(10 + tabWidth, 22, tabWidth, 20, "Blocked Players"),
			(button, mouseButton) -> mc.setScreen(new BlockedPlayersScreen(getParent(), manager)));
		addButton(new ButtonGeneric(getScreenWidth() - 80, getScreenHeight() - 28, 70, 20, "Done"),
			(button, mouseButton) -> onClose());
	}

	@Override
	protected int getBrowserHeight() {
		return getScreenHeight() - 86;
	}

	@Override
	protected int getConfigWidth() {
		return 220;
	}

	@Override
	public List<ConfigOptionWrapper> getConfigs() {
		return ConfigOptionWrapper.createFor(PvpClubBlockerClient.configOptions().options());
	}

	@Override
	protected void onSettingsChanged() {
		PvpClubBlockerClient.configOptions().save();
		PvpClubBlockerClient.configOptions().refreshKeybinds();
	}

	@Override
	public void onClose() {
		mc.setScreen(getParent());
	}
}
