package org.junner.pvpclubblocker;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.junner.pvpclubblocker.screen.PvpClubConfigScreen;

public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new PvpClubConfigScreen(parent, PvpClubBlockerClient.manager());
	}
}
