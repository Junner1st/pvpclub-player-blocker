package org.junner.pvpclubblocker.screen;

import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.screens.Screen;
import org.junner.pvpclubblocker.BlockManager;
import org.junner.pvpclubblocker.BlockReason;
import org.junner.pvpclubblocker.BlockedPlayer;
import org.junner.pvpclubblocker.screen.widget.BlockedPlayerEntryWidget;
import org.junner.pvpclubblocker.screen.widget.BlockedPlayerListWidget;

import java.util.List;

public final class BlockedPlayersScreen extends GuiListBase<BlockedPlayer, BlockedPlayerEntryWidget, BlockedPlayerListWidget> {
	private final BlockManager manager;
	private BlockReason filter;

	public BlockedPlayersScreen(Screen parent, BlockManager manager) {
		super(10, 70);
		this.manager = manager;
		this.title = "PvPClub Blocker";
		this.useTitleHierarchy = false;
		this.setParent(parent);
	}

	@Override
	protected BlockedPlayerListWidget createListWidget(int listX, int listY) {
		return new BlockedPlayerListWidget(listX, listY, getBrowserWidth(), getBrowserHeight(), this, manager);
	}

	@Override
	protected int getBrowserWidth() {
		return getScreenWidth() - 20;
	}

	@Override
	protected int getBrowserHeight() {
		return getScreenHeight() - 106;
	}

	@Override
	public void initGui() {
		super.initGui();
		int tabWidth = (getScreenWidth() - 20) / 2;
		addButton(new ButtonGeneric(10, 22, tabWidth, 20, "Configuration"),
			(button, mouseButton) -> mc.setScreen(new PvpClubConfigScreen(getParent(), manager)));
		ButtonGeneric blockedPlayers = addButton(new ButtonGeneric(10 + tabWidth, 22, tabWidth, 20, "Blocked Players"),
			(button, mouseButton) -> {});
		blockedPlayers.setEnabled(false);

		int y = 46;
		addButton(new ButtonGeneric(10, y, 118, 20, "Add player"), (button, mouseButton) ->
			mc.setScreen(new AddBlockedPlayerScreen(this, manager)));
		addButton(new ButtonGeneric(132, y, 180, 20, filterLabel()), (button, mouseButton) -> {
			filter = nextFilter(filter);
			initGui();
		});
		addButton(new ButtonGeneric(getScreenWidth() - 80, getScreenHeight() - 28, 70, 20, "Done"),
			(button, mouseButton) -> onClose());
	}

	public List<BlockedPlayer> entries() {
		return manager.entries().stream()
			.filter(entry -> filter == null || entry.reason() == filter)
			.toList();
	}

	private String filterLabel() {
		return filter == null ? "Blocked type: all" : "Blocked type: " + filter.id();
	}

	private static BlockReason nextFilter(BlockReason current) {
		if (current == null) return BlockReason.values()[0];
		int next = current.ordinal() + 1;
		return next == BlockReason.values().length ? null : BlockReason.values()[next];
	}

	@Override
	public void onClose() {
		mc.setScreen(getParent());
	}
}
