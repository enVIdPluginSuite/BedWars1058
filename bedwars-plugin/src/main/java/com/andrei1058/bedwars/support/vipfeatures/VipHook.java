package com.andrei1058.bedwars.support.vipfeatures;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.vipfeatures.api.IVipFeatures;
import org.bukkit.Bukkit;

/**
 * Only ever loaded when the VipFeatures plugin is present: referencing its API classes from
 * {@link BedWars} directly would make the main class fail to load on servers without it.
 */
public final class VipHook {

    private VipHook() {
    }

    public static void register(BedWars plugin) throws Exception {
        IVipFeatures vf = Bukkit.getServicesManager().getRegistration(IVipFeatures.class).getProvider();
        vf.registerMiniGame(new VipFeatures(plugin));
        BedWars.registerEvents(new VipListeners(vf));
    }
}
