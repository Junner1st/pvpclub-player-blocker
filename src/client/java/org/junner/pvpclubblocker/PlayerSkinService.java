package org.junner.pvpclubblocker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSkinService {
	private static final ConcurrentHashMap<UUID, CompletableFuture<PlayerSkin>> CACHE = new ConcurrentHashMap<>();

	private PlayerSkinService() {
	}

	public static CompletableFuture<PlayerSkin> get(UUID uuid, String name) {
		return CACHE.computeIfAbsent(uuid, key -> load(key, name));
	}

	private static CompletableFuture<PlayerSkin> load(UUID uuid, String name) {
		return ProfileLookupService.lookupWithTextures(uuid, name)
			.thenCompose(profile -> profile
				.map(value -> Minecraft.getInstance().getSkinManager().get(value)
					.thenApply(skin -> skin.orElseThrow(() -> new IllegalStateException("SkinManager returned no skin"))))
				.orElseGet(() -> CompletableFuture.failedFuture(
					new IllegalStateException("Session profile has no textures property"))))
			.whenComplete((skin, throwable) -> {
				if (throwable == null) {
					PvpClubBlockerClient.LOGGER.debug("{} Loaded player head for {} ({})",
						PvpClubBlockerClient.LOG_PREFIX, name, uuid);
				} else {
					Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
						? throwable.getCause() : throwable;
					PvpClubBlockerClient.LOGGER.warn("{} Failed to load player head for {} ({}): {}",
						PvpClubBlockerClient.LOG_PREFIX, name, uuid, cause.getMessage());
				}
			})
			.exceptionally(throwable -> DefaultPlayerSkin.get(uuid));
	}
}
