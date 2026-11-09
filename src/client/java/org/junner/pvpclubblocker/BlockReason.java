package org.junner.pvpclubblocker;

import java.util.Locale;

public enum BlockReason {
	WEIRD_BEHAVIOR,
	LONG_TIME_NO_ACTIVE,
	BAD_LANGUAGE,
	REFUSING_TO_FIGHT,
	ELY_MACING_AT_CRYSTAL,
	SWAP_MACING_AT_CRYSTAL,
	CRIT_SPAM,
	OTHER;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static BlockReason fromId(String id) {
		for (BlockReason reason : values()) {
			if (reason.id().equalsIgnoreCase(id)) return reason;
		}
		throw new IllegalArgumentException("Unknown block reason: " + id);
	}
}
