package org.junner.pvpclubblocker.screen.widget;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.MaLiLibIcons;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import org.junner.pvpclubblocker.BlockManager;
import org.junner.pvpclubblocker.BlockedPlayer;
import org.junner.pvpclubblocker.screen.BlockedPlayersScreen;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class BlockedPlayerListWidget extends WidgetListBase<BlockedPlayer, BlockedPlayerEntryWidget> {
	private final BlockedPlayersScreen screen;
	private final BlockManager manager;

	public BlockedPlayerListWidget(int x, int y, int width, int height, BlockedPlayersScreen screen, BlockManager manager) {
		super(x, y, width, height, null);
		this.screen = screen;
		this.manager = manager;
		this.browserEntryHeight = 24;
		this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 6, width - 16, 14, 0, MaLiLibIcons.SEARCH, LeftRight.RIGHT);
		this.browserEntriesOffsetY = 18;
		this.shouldSortList = true;
	}

	@Override
	protected Collection<BlockedPlayer> getAllEntries() {
		return screen.entries();
	}

	@Override
	protected Comparator<BlockedPlayer> getComparator() {
		return Comparator.comparing(BlockedPlayer::name, String.CASE_INSENSITIVE_ORDER);
	}

	@Override
	protected List<String> getEntryStringsForFilter(BlockedPlayer entry) {
		return ImmutableList.of(entry.name().toLowerCase(), entry.uuid().toString(), entry.reason().id());
	}

	@Override
	protected BlockedPlayerEntryWidget createHeaderWidget(int x, int y, int listIndexStart, int usableHeight, int usedHeight) {
		return new BlockedPlayerEntryWidget(x, y, browserEntryWidth, browserEntryHeight, false, null, -1, this, manager);
	}

	@Override
	protected BlockedPlayerEntryWidget createListEntryWidget(int x, int y, int listIndex, boolean isOdd, BlockedPlayer entry) {
		return new BlockedPlayerEntryWidget(x, y, browserEntryWidth, browserEntryHeight, isOdd, entry, listIndex, this, manager);
	}
}
