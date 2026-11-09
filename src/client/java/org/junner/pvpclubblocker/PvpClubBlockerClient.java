package org.junner.pvpclubblocker;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import fi.dy.masa.malilib.event.InputEventHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.world.level.GameType;
import org.junner.pvpclubblocker.screen.BlockedPlayersScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

public final class PvpClubBlockerClient implements ClientModInitializer {
	public static final String MOD_ID = "pvpclub_blocker";
	public static final String LOG_PREFIX = "[pvpclub blocker]";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final SuggestionProvider<FabricClientCommandSource> REASONS = (context, builder) -> {
		for (BlockReason reason : BlockReason.values()) builder.suggest(reason.id());
		return builder.buildFuture();
	};

	private static BlockManager manager;
	private static PvpClubConfigOptions configOptions;
	private static PvpClubBlockerClient instance;
	private ClientPacketListener observedConnection;
	private boolean pvpClubSession;
	private String connectedAddress;
	private int ticksUntilCheck = -1;
	private int ticksUntilPlayerLog = -1;
	private int ticksUntilPblockDisconnect = -1;
	private BlockedPlayer pendingPblockDisconnect;
	private final LinkedHashMap<UUID, GameProfile> currentGamePlayers = new LinkedHashMap<>();
	private List<GameProfile> previousGamePlayers = List.of();
	private boolean inGame;
	private boolean returningToLobby;

	public static BlockManager manager() {
		return manager;
	}

	public static PvpClubConfigOptions configOptions() {
		return configOptions;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		manager = new BlockManager(BlockConfig.load());
		configOptions = new PvpClubConfigOptions(manager);
		InputEventHandler.getKeybindManager().registerKeybindProvider(configOptions);
		registerCommands();
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> PacketTraceLog.flush());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PacketTraceLog.close());
		ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof SocialInteractionsScreen || screen instanceof PauseScreen) {
				Screens.getButtons(screen).add(Button.builder(Component.literal("Blocked Players"), button ->
					client.setScreen(new BlockedPlayersScreen(screen, manager)))
					.bounds(width - 126, 6, 120, 20).build());
			}
		});
		LOGGER.info("{} Loaded with {} blocked player(s); enabled={}; joined-game delay={} ticks; " +
				"command disconnect={}; command delay={} ticks; packet debug={}",
			LOG_PREFIX, manager.entries().size(), manager.config().enabled,
			manager.config().disconnectDelayAfterJoiningGameTicks,
			manager.config().disconnectAfterPblockDuringGame,
			manager.config().disconnectDelayAfterPblockDuringGameTicks, PacketTraceLog.isEnabled());
	}

	public static void onLoginPacket(ClientPacketListener connection, ClientboundLoginPacket packet) {
		Minecraft client = Minecraft.getInstance();
		if (!BlockManager.isPvpClub(client)) return;
		logSpawnPacket("login", packet.commonPlayerSpawnInfo(), client);
		if (instance != null) instance.onSpawnInfo(connection, packet.commonPlayerSpawnInfo());
	}

	public static void onRawPacket(ProtocolInfo<?> protocol, ByteBuf buffer) {
		if (!PacketTraceLog.isEnabled() || !BlockManager.isPvpClub(Minecraft.getInstance())) return;
		byte[] bytes = new byte[buffer.readableBytes()];
		buffer.getBytes(buffer.readerIndex(), bytes);
		PacketTraceLog.writeRaw(protocol.id().toString(), protocol.flow().id(), bytes);
	}

	public static void onRespawnPacket(ClientPacketListener connection, ClientboundRespawnPacket packet) {
		Minecraft client = Minecraft.getInstance();
		if (!BlockManager.isPvpClub(client)) return;
		logSpawnPacket("respawn", packet.commonPlayerSpawnInfo(), client);
		if (instance != null) instance.onSpawnInfo(connection, packet.commonPlayerSpawnInfo());
	}

	private void onSpawnInfo(ClientPacketListener connection, CommonPlayerSpawnInfo spawn) {
		observedConnection = connection;
		pvpClubSession = true;
		connectedAddress = BlockManager.serverAddress(Minecraft.getInstance());

		if (spawn.gameType() == GameType.SURVIVAL) {
			enterGame();
		} else if (spawn.gameType() == GameType.ADVENTURE) {
			enterLobby();
		} else {
			enterLobby();
			LOGGER.warn("{} Treating unsupported PvPClub game mode {} as lobby", LOG_PREFIX, spawn.gameType());
		}
	}

	private void enterGame() {
		boolean newlyDetected = !inGame || returningToLobby;
		returningToLobby = false;
		inGame = true;
		if (!newlyDetected) return;

		rememberCurrentGamePlayers();
		currentGamePlayers.clear();
		cancelPendingPblockDisconnect("entered a new game");
		ticksUntilCheck = manager.config().enabled
			? manager.config().disconnectDelayAfterJoiningGameTicks : -1;
		ticksUntilPlayerLog = 0;
		LOGGER.info("{} Joined game: {}", LOG_PREFIX, connectedAddress);
	}

	private void enterLobby() {
		if (inGame) rememberCurrentGamePlayers();
		currentGamePlayers.clear();
		cancelPendingPblockDisconnect("entered lobby");
		inGame = false;
		returningToLobby = false;
		ticksUntilCheck = -1;
		ticksUntilPlayerLog = -1;
	}

	private static void logSpawnPacket(String type, CommonPlayerSpawnInfo spawn, Minecraft client) {
		PacketTraceLog.write(type, "dimension=" + spawn.dimension().identifier() +
			" gameMode=" + spawn.gameType() + " previousGameMode=" + spawn.previousGameType() +
			" seed=" + spawn.seed() + " seaLevel=" + spawn.seaLevel() + " flat=" + spawn.isFlat() +
			" clientLevel=" + (client.level == null ? "none" : client.level.dimension().identifier()) +
			" playerPresent=" + (client.player != null));
	}

	public static void onObjectivePacket(ClientboundSetObjectivePacket packet) {
		if (!BlockManager.isPvpClub(Minecraft.getInstance())) return;
		PacketTraceLog.write("objective", "name=" + packet.getObjectiveName() +
			" method=" + packet.getMethod() + " display=" +
			(packet.getDisplayName() == null ? "none" : packet.getDisplayName().getString()));
	}

	public static void onDisplayObjectivePacket(ClientboundSetDisplayObjectivePacket packet) {
		if (!BlockManager.isPvpClub(Minecraft.getInstance())) return;
		PacketTraceLog.write("displayObjective", "slot=" + packet.getSlot() +
			" name=" + packet.getObjectiveName());
	}

	public static void onGameEventPacket(ClientboundGameEventPacket packet) {
		if (!BlockManager.isPvpClub(Minecraft.getInstance())) return;
		PacketTraceLog.write("gameEvent", "type=" + packet.getEvent() +
			" value=" + packet.getParam());
	}

	private void onGameMessage(Component message, boolean overlay) {
		Minecraft client = Minecraft.getInstance();
		String text = message.getString();
		boolean gameComplete = text.contains("Match Complete");
		boolean failedToStart = text.contains("You were kicked from ") && text.contains("Not all players joined");
		boolean resigned = text.strip().matches("\\S+ resigned");
		if (BlockManager.isPvpClub(client) && (gameComplete || failedToStart || resigned)) {
			rememberCurrentGamePlayers();
			returningToLobby = true;
			ticksUntilCheck = -1;
			ticksUntilPlayerLog = -1;
			cancelPendingPblockDisconnect("game ended");
		}
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var pblock = dispatcher.register(ClientCommandManager.literal("pblock")
				.then(ClientCommandManager.argument("player", StringArgumentType.word())
					.suggests((context, builder) -> {
						Minecraft client = Minecraft.getInstance();
						for (GameProfile profile : blockCommandSuggestions(client)) builder.suggest(profile.name());
						return builder.buildFuture();
					})
					.executes(context -> block(context.getSource(), StringArgumentType.getString(context, "player"), BlockReason.OTHER))
					.then(ClientCommandManager.argument("reason", StringArgumentType.word()).suggests(REASONS)
						.executes(context -> {
							String reason = StringArgumentType.getString(context, "reason");
							try {
								return block(context.getSource(), StringArgumentType.getString(context, "player"), BlockReason.fromId(reason));
							} catch (IllegalArgumentException exception) {
								context.getSource().sendError(Component.literal(exception.getMessage()));
								return 0;
							}
						}))));
			dispatcher.register(ClientCommandManager.literal("pb").redirect(pblock));

			dispatcher.register(ClientCommandManager.literal("punblock")
				.then(ClientCommandManager.argument("player", StringArgumentType.word())
					.suggests((context, builder) -> {
						for (BlockedPlayer entry : manager.entries()) builder.suggest(entry.name());
						return builder.buildFuture();
					})
					.executes(context -> {
						String name = StringArgumentType.getString(context, "player");
						Optional<BlockedPlayer> entry = manager.getByName(name);
						if (entry.isEmpty() || !manager.unblock(entry.get().uuid())) {
							context.getSource().sendError(Component.literal("Player is not blocked: " + name));
							return 0;
						}
						context.getSource().sendFeedback(Component.literal("Unblocked " + entry.get().name()));
						return 1;
					})));

			dispatcher.register(ClientCommandManager.literal("pblocked")
				.executes(context -> {
					Minecraft.getInstance().setScreen(new BlockedPlayersScreen(null, manager));
					return 1;
				}));
		});
	}

	private int block(FabricClientCommandSource source, String name, BlockReason reason) {
		Minecraft client = Minecraft.getInstance();
		boolean commandStartedInGame = isActiveGame();
		Optional<GameProfile> onlineProfile = manager.findOnlineProfile(client, name);
		if (onlineProfile.isPresent()) {
			return finishBlock(source, client, onlineProfile.get(), reason, commandStartedInGame);
		}

		Optional<BlockedPlayer> existing = manager.getByName(name);
		if (existing.isPresent()) {
			BlockedPlayer player = existing.get();
			return finishBlock(source, client, new GameProfile(player.uuid(), player.name()), reason, commandStartedInGame);
		}

		source.sendFeedback(Component.literal("Looking up UUID for " + name + "..."));
		LOGGER.info("{} Looking up UUID for offline player {}", LOG_PREFIX, name);
		ProfileLookupService.lookup(name).whenComplete((profile, throwable) -> client.execute(() -> {
			if (throwable != null) {
				Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
				LOGGER.warn("{} UUID lookup failed for {}: {}", LOG_PREFIX, name, cause.getMessage());
				source.sendError(Component.literal("UUID lookup failed for " + name + ": " + cause.getMessage()));
			} else if (profile.isEmpty()) {
				LOGGER.info("{} No Minecraft profile found for {}", LOG_PREFIX, name);
				source.sendError(Component.literal("Minecraft player not found: " + name));
			} else {
				GameProfile resolved = profile.get();
				LOGGER.info("{} Resolved offline player {} as {}", LOG_PREFIX, resolved.name(), resolved.id());
				finishBlock(source, client, resolved, reason, commandStartedInGame);
			}
		}));
		return 1;
	}

	private int finishBlock(FabricClientCommandSource source, Minecraft client, GameProfile profile, BlockReason reason,
			boolean commandStartedInGame) {
		manager.block(profile, reason);
		source.sendFeedback(Component.literal("Blocked " + profile.name() + " [" + reason.id() + "]"));
		if (commandStartedInGame && isActiveGame()) {
			// Once a player is blocked during this game, the command-specific setting owns the disconnect behavior.
			ticksUntilCheck = -1;
		}
		if (!manager.config().disconnectAfterPblockDuringGame || !commandStartedInGame) return 1;

		if (isActiveGame() && client.getConnection() != null) {
			BlockedPlayer blockedPlayer = manager.get(profile.id())
				.orElse(new BlockedPlayer(profile.id(), profile.name(), reason, System.currentTimeMillis()));
			schedulePblockDisconnect(client, blockedPlayer);
		} else {
			LOGGER.info("{} Cancelled /pblock disconnect for {} {}: game ended before blocking completed",
				LOG_PREFIX, profile.name(), reason.id());
		}
		return 1;
	}

	private boolean isActiveGame() {
		return inGame && !returningToLobby;
	}

	private List<GameProfile> blockCommandSuggestions(Minecraft client) {
		if (isActiveGame() && !currentGamePlayers.isEmpty()) {
			return new ArrayList<>(currentGamePlayers.values());
		}
		if (!previousGamePlayers.isEmpty()) return previousGamePlayers;
		return manager.onlineProfiles(client);
	}

	private void collectCurrentGamePlayers(Minecraft client) {
		if (!isActiveGame()) return;
		for (GameProfile profile : manager.onlineProfiles(client)) {
			currentGamePlayers.put(profile.id(), profile);
		}
	}

	private void tickGamePlayerLog() {
		if (ticksUntilPlayerLog < 0) return;
		if (ticksUntilPlayerLog > 0) {
			ticksUntilPlayerLog--;
			return;
		}
		if (currentGamePlayers.isEmpty()) return;

		String players = currentGamePlayers.values().stream()
			.map(profile -> profile.name() + " (" + profile.id() + ")")
			.reduce((left, right) -> left + ", " + right)
			.orElse("none");
		LOGGER.info("{} Detected game player(s): {}", LOG_PREFIX, players);
		ticksUntilPlayerLog = -1;
	}

	private void rememberCurrentGamePlayers() {
		if (!currentGamePlayers.isEmpty()) {
			previousGamePlayers = List.copyOf(currentGamePlayers.values());
		}
	}

	private void schedulePblockDisconnect(Minecraft client, BlockedPlayer player) {
		int delay = manager.config().disconnectDelayAfterPblockDuringGameTicks;
		if (delay == 0) {
			disconnectAfterPblock(client, player, 0);
			return;
		}
		pendingPblockDisconnect = player;
		ticksUntilPblockDisconnect = delay;
		LOGGER.info("{} Scheduled disconnect after /pblock {} {} in {} ticks",
			LOG_PREFIX, player.name(), player.reason().id(), delay);
	}

	private void tickPendingPblockDisconnect(Minecraft client) {
		if (pendingPblockDisconnect == null) return;
		if (!manager.config().disconnectAfterPblockDuringGame || !isActiveGame() || client.getConnection() == null) {
			cancelPendingPblockDisconnect("no active game");
			return;
		}
		if (ticksUntilPblockDisconnect > 0 && --ticksUntilPblockDisconnect > 0) return;

		BlockedPlayer player = pendingPblockDisconnect;
		int configuredDelay = manager.config().disconnectDelayAfterPblockDuringGameTicks;
		pendingPblockDisconnect = null;
		ticksUntilPblockDisconnect = -1;
		disconnectAfterPblock(client, player, configuredDelay);
	}

	private void disconnectAfterPblock(Minecraft client, BlockedPlayer player, int delay) {
		if (client.getConnection() == null) return;
		LOGGER.info("{} Disconnected after /pblock {} {} (delay={} ticks)",
			LOG_PREFIX, player.name(), player.reason().id(), delay);
		client.getConnection().getConnection().disconnect(
			Component.literal("Blocked " + player.name() + " (" + player.reason().id() + ")"));
	}

	private void cancelPendingPblockDisconnect(String reason) {
		if (pendingPblockDisconnect != null) {
			LOGGER.info("{} Cancelled scheduled /pblock disconnect for {}: {}",
				LOG_PREFIX, pendingPblockDisconnect.name(), reason);
		}
		pendingPblockDisconnect = null;
		ticksUntilPblockDisconnect = -1;
	}

	private void onEndTick(Minecraft client) {
		boolean onPvpClub = client.getConnection() != null && BlockManager.isPvpClub(client);

		if (!onPvpClub) {
			if (!pvpClubSession) return;
			pvpClubSession = false;
			observedConnection = null;
			connectedAddress = null;
			ticksUntilCheck = -1;
			ticksUntilPlayerLog = -1;
			rememberCurrentGamePlayers();
			currentGamePlayers.clear();
			cancelPendingPblockDisconnect("left game server");
			inGame = false;
			returningToLobby = false;
			LOGGER.info("{} Left PvPClub PLAY connection", LOG_PREFIX);
			return;
		}

		if (client.getConnection() != observedConnection) {
			// The login packet normally identifies this connection before the next tick.
			// Keep it inactive if a future protocol skips the expected packet callback.
			observedConnection = client.getConnection();
			pvpClubSession = true;
			ticksUntilCheck = -1;
			ticksUntilPlayerLog = -1;
			inGame = false;
			returningToLobby = false;
			connectedAddress = BlockManager.serverAddress(client);
			LOGGER.info("{} Joined server: {}", LOG_PREFIX, connectedAddress);
		}

		if (client.level == null) return;

		collectCurrentGamePlayers(client);
		tickGamePlayerLog();
		tickPendingPblockDisconnect(client);
		if (!manager.config().enabled) {
			ticksUntilCheck = -1;
			return;
		}
		if (ticksUntilCheck < 0) return;
		if (ticksUntilCheck > 0) {
			ticksUntilCheck--;
			return;
		}
		Optional<BlockedPlayer> blockedPlayer = manager.findBlockedOnline(client);
		if (blockedPlayer.isPresent()) {
			ticksUntilCheck = -1;
			BlockedPlayer player = blockedPlayer.get();
			LOGGER.info("{} Game check detected blocked player {} {}", LOG_PREFIX, player.name(), player.reason().id());
			manager.disconnect(client, player);
		}
	}
}
