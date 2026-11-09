package com.andrei1058.vipfeatures.api;

import org.bukkit.entity.Player;

public interface IVipFeatures {

    void registerMiniGame(MiniGame miniGame) throws MiniGameAlreadyRegistered;

    void givePlayerItemStack(Player player);
}
