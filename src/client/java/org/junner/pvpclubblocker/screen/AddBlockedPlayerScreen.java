package org.junner.pvpclubblocker.screen;

import com.mojang.authlib.GameProfile;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.screens.Screen;
import org.junner.pvpclubblocker.BlockManager;
import org.junner.pvpclubblocker.BlockReason;
import org.junner.pvpclubblocker.BlockedPlayer;
import org.junner.pvpclubblocker.ProfileLookupService;

import java.util.Optional;

public final class AddBlockedPlayerScreen extends GuiBase {
	private final BlockManager manager;
	private GuiTextFieldGeneric nameField;
	private BlockReason reason = BlockReason.OTHER;
	private boolean lookupInProgress;

	public AddBlockedPlayerScreen(Screen parent, BlockManager manager) {
		this.manager = manager;
		this.title = "Add Blocked Player";
		this.useTitleHierarchy = false;
		setParent(parent);
	}

	@Override
	public void initGui() {
		super.initGui();
		int left = getScreenWidth() / 2 - 150;
		addLabel(left, 45, 100, 12, 0xFFFFFFFF, "Player name");
		nameField = new GuiTextFieldGeneric(left + 105, 40, 195, 20, font);
		nameField.setMaxLength(16);
		nameField.setFilter(value -> value.matches("[A-Za-z0-9_]*"));
		addTextField(nameField, null);

		addButton(new ButtonGeneric(left, 75, 300, 20, "Blocked type: " + reason.id()), (button, mouseButton) -> {
			reason = BlockReason.values()[(reason.ordinal() + 1) % BlockReason.values().length];
			String currentName = nameField.getValue();
			initGui();
			nameField.setValue(currentName);
		});
		addButton(new ButtonGeneric(left, 110, 145, 20, "Add"), (button, mouseButton) -> addPlayer());
		addButton(new ButtonGeneric(left + 155, 110, 145, 20, "Cancel"), (button, mouseButton) -> onClose());
	}

	private void addPlayer() {
		if (lookupInProgress) return;
		String name = nameField.getValue().trim();
		if (!name.matches("[A-Za-z0-9_]{3,16}")) {
			addGuiMessage(MessageType.ERROR, 4000, "Invalid Minecraft player name");
			return;
		}

		Optional<GameProfile> online = manager.findTabProfile(mc, name);
		if (online.isPresent()) {
			finish(online.get());
			return;
		}
		Optional<BlockedPlayer> existing = manager.getByName(name);
		if (existing.isPresent()) {
			BlockedPlayer player = existing.get();
			finish(new GameProfile(player.uuid(), player.name()));
			return;
		}

		lookupInProgress = true;
		addGuiMessage(MessageType.INFO, 8000, "Looking up UUID for " + name + "...");
		ProfileLookupService.lookup(name).whenComplete((profile, throwable) -> mc.execute(() -> {
			lookupInProgress = false;
			if (throwable != null) {
				Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
				addGuiMessage(MessageType.ERROR, 5000, "UUID lookup failed: " + cause.getMessage());
			} else if (profile.isEmpty()) {
				addGuiMessage(MessageType.ERROR, 5000, "Minecraft player not found: " + name);
			} else {
				finish(profile.get());
			}
		}));
	}

	private void finish(GameProfile profile) {
		manager.block(profile, reason);
		mc.setScreen(getParent());
	}

	@Override
	public void onClose() {
		mc.setScreen(getParent());
	}
}
