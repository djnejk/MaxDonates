package com.djnejk.maxdonates;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaxDonates extends JavaPlugin {

    private Economy economy;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault economy provider nebyl nalezen. Plugin se vypina.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PluginCommand command = getCommand("test");
        if (command != null) {
            command.setExecutor(new TestCommand(this));
        }

        getLogger().info("MaxDonates plugin byl zapnut.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MaxDonates plugin byl vypnut.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }
}