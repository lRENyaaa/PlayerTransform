package com.renyuansurvival.playertransform;

import com.lishid.openinv.util.InternalAccessor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


public final class PlayerTransform extends JavaPlugin implements Listener {

    private static Economy econ = null;
    private InternalAccessor accessor;

    @Override
    public void onEnable() {
        if (!setupEconomy() ) {
            getLogger().severe(String.format("[%s] - 没有找到经济插件,插件将自动关闭!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.accessor = new InternalAccessor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void PlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FloodgateApi floodgate = FloodgateApi.getInstance();
        if (floodgate.isFloodgatePlayer(player.getUniqueId())) {
            char[] arr = player.getName().toCharArray();
            char[] ret = new char[arr.length - 3];
            System.arraycopy(arr, 3, ret, 0, ret.length);
            String playerName = String.copyValueOf(ret);
            OfflinePlayer offlineOriginalPlayer = Bukkit.getOfflinePlayer(playerName);
            Player originalPlayer = null;
            if (Bukkit.isPrimaryThread()) {
                originalPlayer = this.accessor.getPlayerDataManager().loadPlayer(offlineOriginalPlayer);
            }else {
                Future<Player> future = Bukkit.getScheduler().callSyncMethod(this,
                        () -> this.accessor.getPlayerDataManager().loadPlayer(offlineOriginalPlayer));
                try {
                    originalPlayer = future.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            if (originalPlayer != null && !originalPlayer.isBanned()){
                Bukkit.getBanList(BanList.Type.NAME).addBan(playerName,"此账户数据已被转移,并冻结原有账户",null,null);
                player.getInventory().setContents(originalPlayer.getInventory().getContents());
                player.sendMessage("已经恢复你的背包数据");
                player.getEnderChest().setContents(originalPlayer.getEnderChest().getContents());
                player.sendMessage("已经恢复你的末影箱数据");
                player.setExp(originalPlayer.getExp());
                player.sendMessage("已经恢复你的经验数据");
                EconomyResponse r = econ.depositPlayer(player, econ.getBalance(offlineOriginalPlayer));
                if(r.transactionSuccess()) {
                    player.sendMessage(String.format("已经恢复你的经济数据 %s 金币", econ.format(r.balance)));
                } else {
                    player.sendMessage(String.format("在转移经济数据时发生了一个错误: %s", r.errorMessage));
                }
                

            }
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

}
