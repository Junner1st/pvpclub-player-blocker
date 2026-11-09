package org.junner.pvpclubblocker.screen.widget;

import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.junner.pvpclubblocker.BlockManager;
import org.junner.pvpclubblocker.BlockedPlayer;
import org.junner.pvpclubblocker.PlayerSkinService;

public final class BlockedPlayerEntryWidget extends WidgetListEntryBase<BlockedPlayer> {
	private final boolean odd;
	private final BlockedPlayerListWidget listWidget;
	private volatile PlayerSkin skin;

	public BlockedPlayerEntryWidget(int x, int y, int width, int height, boolean odd, BlockedPlayer entry,
			int listIndex, BlockedPlayerListWidget listWidget, BlockManager manager) {
		super(x, y, width, height, entry, listIndex);
		this.odd = odd;
		this.listWidget = listWidget;
		if (entry != null) {
			this.skin = DefaultPlayerSkin.get(entry.uuid());
			PlayerSkinService.get(entry.uuid(), entry.name()).thenAccept(resolved -> this.skin = resolved);
			addButton(new ButtonGeneric(x + width, y + 2, -1, true, "Remove"), (button, mouseButton) -> {
				manager.unblock(entry.uuid());
				listWidget.refreshEntries();
			});
		}
	}

	@Override
	public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
		int color = isMouseOver(mouseX, mouseY) ? 0xA0707070 : (odd ? 0xA0101010 : 0xA0303030);
		RenderUtils.drawRect(ctx, x, y, width, height, color);
		int reasonX = x + Math.min(180, Math.max(130, width / 3));
		int uuidX = reasonX + 170;
		boolean showUuid = uuidX + 280 < x + width - 65;
		if (entry == null) {
			drawString(ctx, x + 26, y + 8, 0xFFFFFFFF, "Player");
			drawString(ctx, reasonX, y + 8, 0xFFFFFFFF, "Blocked type");
			if (showUuid) drawString(ctx, uuidX, y + 8, 0xFFFFFFFF, "UUID");
		} else {
			PlayerFaceRenderer.draw(ctx, skin, x + 4, y + 4, 16);
			drawString(ctx, x + 26, y + 8, 0xFFFFFFFF, entry.name());
			drawString(ctx, reasonX, y + 8, 0xFFCCCCCC, entry.reason().id());
			if (showUuid) drawString(ctx, uuidX, y + 8, 0xFF999999, entry.uuid().toString());
		}
		super.render(ctx, mouseX, mouseY, selected);
	}
}
