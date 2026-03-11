package com.djnejk.maxdonates;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaxDonates extends JavaPlugin {

    private Economy economy;
    private DatabaseManager databaseManager;
    private Lang lang;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang_cz.yml", false);
        this.lang = new Lang(this, "lang_cz.yml");

        if (!setupEconomy()) {
            getLogger().severe("Vault economy provider nebyl nalezen. Plugin se vypina.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseManager = new DatabaseManager(this);
        try {
            databaseManager.connect();
            databaseManager.createTables();
        } catch (Exception ex) {
            getLogger().severe("Nepodarilo se pripojit do MySQL: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new DonationNotificationListener(this), this);

        PluginCommand command = getCommand("maxdonates");
        if (command != null) {
            MaxDonatesCommand executor = new MaxDonatesCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("MaxDonates plugin byl zapnut.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("MaxDonates plugin byl vypnut.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public Lang getLang() {
        return lang;
    }
}
