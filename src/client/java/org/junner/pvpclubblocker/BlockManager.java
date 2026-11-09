package org.junner.pvpclubblocker;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class BlockManager {
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

	public Optional<GameProfile> findOnlineProfile(Minecraft client, String name) {
		if (client.getConnection() == null) return Optional.empty();
		return client.getConnection().getOnlinePlayers().stream()
			.map(PlayerInfo::getProfile)
			.filter(profile -> profile.name().equalsIgnoreCase(name))
			.findFirst();
	}

	public List<GameProfile> onlineProfiles(Minecraft client) {
		if (client.getConnection() == null) return List.of();
		List<GameProfile> profiles = new ArrayList<>();
		for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
			GameProfile profile = info.getProfile();
			if (!isOwnProfile(client, profile)) profiles.add(profile);
		}
		profiles.sort(Comparator.comparing(GameProfile::name, String.CASE_INSENSITIVE_ORDER));
		return profiles;
	}

	public Optional<BlockedPlayer> findBlockedOnline(Minecraft client) {
		if (client.getConnection() == null) return Optional.empty();
		for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
			GameProfile profile = info.getProfile();
			if (isOwnProfile(client, profile)) continue;
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
