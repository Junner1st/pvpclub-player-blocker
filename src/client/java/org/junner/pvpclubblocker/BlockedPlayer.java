package org.junner.pvpclubblocker;

import java.util.UUID;

public record BlockedPlayer(UUID uuid, String name, BlockReason reason, long blockedAt) {
}
