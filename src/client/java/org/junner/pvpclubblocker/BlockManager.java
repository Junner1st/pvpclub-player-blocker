package org.junner.pvpclubblocker;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class BlockManager {
	private static final Pattern PVPCLUB_PLAYER_ROW = Pattern.compile("(?i)(?:^|\\s)\\d+ms(?:\\s|$)");
	private final BlockConfig config;

	public BlockManager(BlockConfig config) {
		this.config = config;
	}

	public BlockConfig config() {
		return config;
	}

	public List<BlockedPlayer> entries() {
		return config.blockedPlayers.stream()
			.sorted(Comparator.comparing(BlockedPlayer::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	public Optional<BlockedPlayer> get(UUID uuid) {
		return config.blockedPlayers.stream().filter(entry -> entry.uuid().equals(uuid)).findFirst();
	}

	public Optional<BlockedPlayer> getByName(String name) {
		return config.blockedPlayers.stream().filter(entry -> entry.name().equalsIgnoreCase(name)).findFirst();
	}

	public void block(GameProfile profile, BlockReason reason) {
		config.blockedPlayers.removeIf(entry -> entry.uuid().equals(profile.id()));
		config.blockedPlayers.add(new BlockedPlayer(profile.id(), profile.name(), reason, System.currentTimeMillis()));
		config.save();
		PvpClubBlockerClient.LOGGER.info("{} Blocked {} {} ({})", PvpClubBlockerClient.LOG_PREFIX,
			profile.name(), reason.id(), profile.id());
	}

	public boolean unblock(UUID uuid) {
		boolean removed = config.blockedPlayers.removeIf(entry -> entry.uuid().equals(uuid));
		if (removed) {
			config.save();
			PvpClubBlockerClient.LOGGER.info("{} Unblocked UUID {}", PvpClubBlockerClient.LOG_PREFIX, uuid);
		}
		return removed;
	}

	public Optional<GameProfile> findTabProfile(Minecraft client, String name) {
		return tabProfiles(client).stream()
			.filter(profile -> profile.name().equalsIgnoreCase(name))
			.findFirst();
	}

	public List<GameProfile> tabProfiles(Minecraft client) {
		if (client.getConnection() == null) return List.of();

		List<GameProfile> knownProfiles = client.getConnection().getOnlinePlayers().stream()
			.map(PlayerInfo::getProfile)
			.filter(profile -> !profile.name().isBlank())
			.toList();
		LinkedHashMap<UUID, GameProfile> profiles = new LinkedHashMap<>();
		for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
			GameProfile listedProfile = info.getProfile();
			if (!listedProfile.name().isBlank()) {
				if (!isOwnProfile(client, listedProfile)) profiles.put(listedProfile.id(), listedProfile);
				continue;
			}

			Component displayName = info.getTabListDisplayName();
			if (displayName == null) continue;
			String displayedText = displayName.getString();
			if (!PVPCLUB_PLAYER_ROW.matcher(displayedText).find()) continue;
			for (GameProfile profile : knownProfiles) {
				if (!isOwnProfile(client, profile) && containsPlayerName(displayedText, profile.name())) {
					profiles.put(profile.id(), profile);
				}
			}
		}
		return profiles.values().stream()
			.sorted(Comparator.comparing(GameProfile::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static boolean containsPlayerName(String text, String name) {
		int from = 0;
		while ((from = text.indexOf(name, from)) >= 0) {
			int end = from + name.length();
			boolean validStart = from == 0 || !isPlayerNameCharacter(text.charAt(from - 1));
			boolean validEnd = end == text.length() || !isPlayerNameCharacter(text.charAt(end));
			if (validStart && validEnd) return true;
			from = end;
		}
		return false;
	}

	private static boolean isPlayerNameCharacter(char character) {
		return character == '_' || character >= '0' && character <= '9' ||
			character >= 'A' && character <= 'Z' || character >= 'a' && character <= 'z';
	}

	public Optional<BlockedPlayer> findBlocked(Iterable<GameProfile> profiles) {
		for (GameProfile profile : profiles) {
			Optional<BlockedPlayer> blocked = matchBlockedProfile(profile);
			if (blocked.isPresent()) return blocked;
		}
		return Optional.empty();
	}

	private Optional<BlockedPlayer> matchBlockedProfile(GameProfile profile) {
		Optional<BlockedPlayer> blocked = get(profile.id());
		if (blocked.isEmpty()) {
			blocked = getByName(profile.name());
			blocked.ifPresent(entry -> PvpClubBlockerClient.LOGGER.warn(
				"{} Matched blocked Tab player {} by name because server UUID differs (stored={}, server={})",
				PvpClubBlockerClient.LOG_PREFIX, profile.name(), entry.uuid(), profile.id()));
		}
		if (blocked.isEmpty()) return Optional.empty();

		BlockedPlayer entry = blocked.get();
		if (entry.uuid().equals(profile.id()) && !entry.name().equals(profile.name())) {
			config.blockedPlayers.remove(entry);
			entry = new BlockedPlayer(entry.uuid(), profile.name(), entry.reason(), entry.blockedAt());
			config.blockedPlayers.add(entry);
			config.save();
		}
		return Optional.of(entry);
	}

	private static boolean isOwnProfile(Minecraft client, GameProfile profile) {
		GameProfile ownProfile = client.getGameProfile();
		return profile.id().equals(ownProfile.id()) || profile.name().equalsIgnoreCase(ownProfile.name());
	}

	public static boolean isPvpClub(Minecraft client) {
		if (client.getCurrentServer() == null) return false;
		String host = client.getCurrentServer().ip;
		int portSeparator = host.indexOf(':');
		if (portSeparator >= 0) host = host.substring(0, portSeparator);
		host = host.toLowerCase(Locale.ROOT);
		return host.equals("mcpvp.club") || host.endsWith(".mcpvp.club");
	}

	public static String serverAddress(Minecraft client) {
		return client.getCurrentServer() == null ? "unknown" : client.getCurrentServer().ip;
	}

	public void disconnect(Minecraft client, BlockedPlayer player) {
		if (client.getConnection() != null) {
			PvpClubBlockerClient.LOGGER.info("{} Disconnected: detected blocked player {} {} ({})",
				PvpClubBlockerClient.LOG_PREFIX, player.name(), player.reason().id(), player.uuid());
			client.getConnection().getConnection().disconnect(
				Component.literal("Blocked opponent: " + player.name() + " (" + player.reason().id() + ")"));
		}
	}
}
