package org.junner.pvpclubblocker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BlockConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pvpclub-blocker.json");

	public boolean enabled = true;
	public int disconnectDelayAfterJoiningGameTicks = 20;
	public boolean disconnectAfterPblockDuringGame = false;
	public int disconnectDelayAfterPblockDuringGameTicks = 20;
	public boolean packetDebug = false;
	public String configHotkey = "";
	public String blockListHotkey = "";
	public List<BlockedPlayer> blockedPlayers = new ArrayList<>();

	public static BlockConfig load() {
		if (!Files.exists(PATH)) return new BlockConfig();
		try (Reader reader = Files.newBufferedReader(PATH)) {
			BlockConfig config = GSON.fromJson(reader, BlockConfig.class);
			if (config == null) return new BlockConfig();
			if (config.blockedPlayers == null) config.blockedPlayers = new ArrayList<>();
			if (config.configHotkey == null) config.configHotkey = "";
			if (config.blockListHotkey == null) config.blockListHotkey = "";
			config.disconnectDelayAfterJoiningGameTicks = Math.max(0,
				Math.min(1200, config.disconnectDelayAfterJoiningGameTicks));
			config.disconnectDelayAfterPblockDuringGameTicks = Math.max(0,
				Math.min(1200, config.disconnectDelayAfterPblockDuringGameTicks));
			return config;
		} catch (IOException | JsonParseException exception) {
			PvpClubBlockerClient.LOGGER.error("{} Could not load {}", PvpClubBlockerClient.LOG_PREFIX, PATH, exception);
			return new BlockConfig();
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			PvpClubBlockerClient.LOGGER.error("{} Could not save {}", PvpClubBlockerClient.LOG_PREFIX, PATH, exception);
		}
	}
}
