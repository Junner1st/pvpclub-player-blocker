package org.junner.pvpclubblocker;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import net.minecraft.client.Minecraft;
import org.junner.pvpclubblocker.screen.BlockedPlayersScreen;
import org.junner.pvpclubblocker.screen.PvpClubConfigScreen;

import java.util.List;

public final class PvpClubConfigOptions implements IKeybindProvider {
	private final BlockManager manager;
	private final ConfigBoolean enabled = new ConfigBoolean(
		"enabled", true, "Enable automatic blocked-player detection.", "Enable", "Enable");
	private final ConfigInteger joinedGameDelay = new ConfigInteger(
		"disconnectDelayAfterJoiningGameTicks", 20, 0, 1200, "Delay before checking a newly joined game.",
		"Disconnect Delay After Joining a Game [ticks]", "Disconnect Delay After Joining a Game [ticks]");
	private final ConfigBoolean disconnectAfterPblock = new ConfigBoolean(
		"disconnectAfterPblockDuringGame", false, "Disconnect after /pblock succeeds during an active game.",
		"Disconnect After Running Pblock During a Game", "Disconnect After Running Pblock During a Game");
	private final ConfigInteger pblockDelay = new ConfigInteger(
		"disconnectDelayAfterPblockDuringGameTicks", 20, 0, 1200,
		"Delay before disconnecting after /pblock succeeds during an active game.",
		"Disconnect Delay After Running Pblock During a Game [ticks]",
		"Disconnect Delay After Running Pblock During a Game [ticks]");
	private final ConfigHotkey configOpen = new ConfigHotkey(
		"configOpen", "", "Open the PvPClub Blocker configuration.",
		"Open Config [keybind]", "Open Config [keybind]");
	private final ConfigHotkey blockListOpen = new ConfigHotkey(
		"blockListOpen", "", "Open the blocked players list.",
		"Open Blocklist [keybind]", "Open Blocklist [keybind]");
	private final List<IConfigBase> options = List.of(
		enabled, joinedGameDelay, disconnectAfterPblock, pblockDelay, configOpen, blockListOpen);

	public PvpClubConfigOptions(BlockManager manager) {
		this.manager = manager;
		BlockConfig config = manager.config();
		enabled.setBooleanValue(config.enabled);
		joinedGameDelay.setIntegerValue(config.disconnectDelayAfterJoiningGameTicks);
		disconnectAfterPblock.setBooleanValue(config.disconnectAfterPblockDuringGame);
		pblockDelay.setIntegerValue(config.disconnectDelayAfterPblockDuringGameTicks);
		configOpen.setValueFromString(config.configHotkey);
		blockListOpen.setValueFromString(config.blockListHotkey);

		enabled.setValueChangeCallback(ignored -> save());
		joinedGameDelay.setValueChangeCallback(ignored -> save());
		disconnectAfterPblock.setValueChangeCallback(ignored -> save());
		pblockDelay.setValueChangeCallback(ignored -> save());
		configOpen.setValueChangeCallback(ignored -> save());
		blockListOpen.setValueChangeCallback(ignored -> save());
		configOpen.getKeybind().setCallback((action, keybind) -> {
			Minecraft client = Minecraft.getInstance();
			client.setScreen(new PvpClubConfigScreen(client.screen, manager));
			return true;
		});
		blockListOpen.getKeybind().setCallback((action, keybind) -> {
			Minecraft client = Minecraft.getInstance();
			client.setScreen(new BlockedPlayersScreen(client.screen, manager));
			return true;
		});
	}

	public List<IConfigBase> options() {
		return options;
	}

	public void save() {
		BlockConfig config = manager.config();
		config.enabled = enabled.getBooleanValue();
		config.disconnectDelayAfterJoiningGameTicks = joinedGameDelay.getIntegerValue();
		config.disconnectAfterPblockDuringGame = disconnectAfterPblock.getBooleanValue();
		config.disconnectDelayAfterPblockDuringGameTicks = pblockDelay.getIntegerValue();
		config.configHotkey = configOpen.getStringValue();
		config.blockListHotkey = blockListOpen.getStringValue();
		config.save();
		PvpClubBlockerClient.LOGGER.info(
			"{} Settings updated: enabled={}; joined-game delay={} ticks; command disconnect={}; " +
				"command delay={} ticks; config hotkey={}; blocklist hotkey={}",
			PvpClubBlockerClient.LOG_PREFIX, config.enabled, config.disconnectDelayAfterJoiningGameTicks,
			config.disconnectAfterPblockDuringGame, config.disconnectDelayAfterPblockDuringGameTicks,
			config.configHotkey.isEmpty() ? "none" : config.configHotkey,
			config.blockListHotkey.isEmpty() ? "none" : config.blockListHotkey);
	}

	public void refreshKeybinds() {
		InputEventHandler.getKeybindManager().updateUsedKeys();
	}

	@Override
	public void addKeysToMap(IKeybindManager keybindManager) {
		keybindManager.addKeybindToMap(configOpen.getKeybind());
		keybindManager.addKeybindToMap(blockListOpen.getKeybind());
	}

	@Override
	public void addHotkeys(IKeybindManager keybindManager) {
		keybindManager.addHotkeysForCategory("PvPClub Blocker", "PvPClub Blocker", List.of(configOpen, blockListOpen));
	}
}
