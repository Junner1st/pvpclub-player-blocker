package org.junner.pvpclubblocker;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProfileLookupService {
	private static final URI LOOKUP_URI = URI.create("https://api.minecraftservices.com/minecraft/profile/lookup/name/");
	private static final URI SESSION_URI = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/");
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	private ProfileLookupService() {
	}

	public static CompletableFuture<Optional<GameProfile>> lookup(String name) {
		if (!name.matches("[A-Za-z0-9_]{3,16}")) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Minecraft player name: " + name));
		}

		HttpRequest request = HttpRequest.newBuilder(LOOKUP_URI.resolve(name))
			.timeout(Duration.ofSeconds(8))
			.header("Accept", "application/json")
			.header("User-Agent", "PvPClub-Blocker/1.0")
			.GET()
			.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenApply(response -> parseResponse(name, response));
	}

	public static CompletableFuture<Optional<GameProfile>> lookupWithTextures(UUID uuid, String fallbackName) {
		HttpRequest request = HttpRequest.newBuilder(SESSION_URI.resolve(uuid.toString() + "?unsigned=false"))
			.timeout(Duration.ofSeconds(8))
			.header("Accept", "application/json")
			.header("User-Agent", "PvPClub-Blocker/1.0")
			.GET()
			.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenApply(response -> parseSessionResponse(uuid, fallbackName, response));
	}

	private static Optional<GameProfile> parseResponse(String requestedName, HttpResponse<String> response) {
		if (response.statusCode() == 404) return Optional.empty();
		if (response.statusCode() != 200) {
			throw new ProfileLookupException("Profile service returned HTTP " + response.statusCode());
		}

		try {
			ProfileResponse profile = GSON.fromJson(response.body(), ProfileResponse.class);
			if (profile == null || profile.id == null || profile.name == null) {
				throw new ProfileLookupException("Profile service returned an incomplete response");
			}
			return Optional.of(new GameProfile(parseUuid(profile.id), profile.name));
		} catch (JsonParseException | IllegalArgumentException exception) {
			throw new ProfileLookupException("Invalid profile response for " + requestedName, exception);
		}
	}

	private static Optional<GameProfile> parseSessionResponse(UUID requestedUuid, String fallbackName,
			HttpResponse<String> response) {
		if (response.statusCode() == 204 || response.statusCode() == 404) return Optional.empty();
		if (response.statusCode() != 200) {
			throw new ProfileLookupException("Session service returned HTTP " + response.statusCode());
		}

		try {
			SessionProfileResponse profile = GSON.fromJson(response.body(), SessionProfileResponse.class);
			if (profile == null || profile.id == null) {
				throw new ProfileLookupException("Session service returned an incomplete response");
			}
			UUID resolvedUuid = parseUuid(profile.id);
			if (!resolvedUuid.equals(requestedUuid)) {
				throw new ProfileLookupException("Session service returned a different UUID");
			}

			ArrayListMultimap<String, Property> properties = ArrayListMultimap.create();
			if (profile.properties != null) {
				for (PropertyResponse property : profile.properties) {
					if (property.name == null || property.value == null) continue;
					Property value = property.signature == null
						? new Property(property.name, property.value)
						: new Property(property.name, property.value, property.signature);
					properties.put(property.name, value);
				}
			}
			if (!properties.containsKey("textures")) return Optional.empty();
			String name = profile.name == null ? fallbackName : profile.name;
			return Optional.of(new GameProfile(resolvedUuid, name, new PropertyMap(properties)));
		} catch (JsonParseException | IllegalArgumentException exception) {
			throw new ProfileLookupException("Invalid session profile response for " + requestedUuid, exception);
		}
	}

	private static UUID parseUuid(String compactUuid) {
		if (!compactUuid.matches("[0-9a-fA-F]{32}")) {
			throw new IllegalArgumentException("Invalid UUID: " + compactUuid);
		}
		String uuid = compactUuid.substring(0, 8) + "-"
			+ compactUuid.substring(8, 12) + "-"
			+ compactUuid.substring(12, 16) + "-"
			+ compactUuid.substring(16, 20) + "-"
			+ compactUuid.substring(20);
		return UUID.fromString(uuid);
	}

	private record ProfileResponse(String id, String name) {
	}

	private record SessionProfileResponse(String id, String name, PropertyResponse[] properties) {
	}

	private record PropertyResponse(String name, String value, String signature) {
	}

	public static final class ProfileLookupException extends RuntimeException {
		private ProfileLookupException(String message) {
			super(message);
		}

		private ProfileLookupException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
