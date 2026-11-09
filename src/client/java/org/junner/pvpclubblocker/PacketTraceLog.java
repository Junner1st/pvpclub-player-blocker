package org.junner.pvpclubblocker;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

final class PacketTraceLog {
	// Developer override. Packet debug is enabled when this OR BlockConfig.packetDebug is true.
	static final boolean FORCE_ENABLED = false;
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
		.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
	private static BufferedWriter writer;
	private static boolean failed;
	private static boolean dirty;

	private PacketTraceLog() {
	}

	static boolean isEnabled() {
		BlockManager manager = PvpClubBlockerClient.manager();
		return FORCE_ENABLED || manager != null && manager.config().packetDebug;
	}

	static synchronized void write(String packetType, String details) {
		if (!isEnabled() || failed) return;
		try {
			open();
			String line = Instant.now() + " " + packetType + " " +
				details.replace("\r", "\\r").replace("\n", "\\n") + "\n";
			writer.write(line);
			dirty = true;
		} catch (IOException exception) {
			fail(exception);
		}
	}

	static void writeRaw(String phase, String direction, byte[] bytes) {
		int packetId = 0;
		int shift = 0;
		for (int index = 0; index < Math.min(bytes.length, 5); index++) {
			packetId |= (bytes[index] & 0x7f) << shift;
			if ((bytes[index] & 0x80) == 0) break;
			shift += 7;
		}
		write("raw", "phase=" + phase + " direction=" + direction + " id=" + packetId +
			" length=" + bytes.length + " base64=" + Base64.getEncoder().encodeToString(bytes));
	}

	static synchronized void flush() {
		if (failed || !dirty) return;
		try {
			writer.flush();
			dirty = false;
		} catch (IOException exception) {
			fail(exception);
		}
	}

	static synchronized void close() {
		if (writer == null) return;
		try {
			writer.close();
		} catch (IOException exception) {
			fail(exception);
		} finally {
			writer = null;
			dirty = false;
		}
	}

	private static void open() throws IOException {
		if (writer != null) return;
		Path directory = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("pvpclub-blocker");
		Files.createDirectories(directory);
		Path file = directory.resolve("packets-" + FILE_TIME.format(Instant.now()) + "-" +
			ProcessHandle.current().pid() + ".log");
		writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		writer.write("# PvPClub packet trace; timestamps are UTC; raw base64 includes packet ID and payload\n");
		dirty = true;
	}

	private static void fail(IOException exception) {
		failed = true;
		PvpClubBlockerClient.LOGGER.warn("{} Could not write packet trace", PvpClubBlockerClient.LOG_PREFIX,
			exception);
	}
}
