package com.djnejk.maxdonates;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

import java.io.File;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaxDonates extends JavaPlugin {

    private Economy economy;
    private DatabaseManager databaseManager;
    private Lang lang;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File langFile = new File(getDataFolder(), "lang_cz.yml");
        if (!langFile.exists()) {
            saveResource("lang_cz.yml", false);
        }
        this.lang = new Lang(this, "lang_cz.yml");

        String version = getDescription().getVersion();
        Bukkit.getLogger().info(" _____________________________________________________________");
        Bukkit.getLogger().info("|    *                 (                                      |");
        Bukkit.getLogger().info("|  (  `                )\\ )                        )          |");
        Bukkit.getLogger().info("|  )\\))(      )     ) (()/(                  )  ( /(   (      |");
        Bukkit.getLogger().info("| ((_)()\\  ( /(  ( /(  /(_))   (    (     ( /(  )\\()) ))\\ (   |");
        Bukkit.getLogger().info("| (_()((_) )(_)) )\\())(_))_    )\\   )\\ )  )(_))(_))/ /((_))\\  |");
        Bukkit.getLogger().info("| |  \\/  |((_)_ ((_)\\  |   \\  ((_) _(_/( ((_)_ | |_ (_)) ((_) |");
        Bukkit.getLogger().info("| | |\\/| |/ _` |\\ \\ /  | |) |/ _ \\| ' \\))/ _` ||  _|/ -_)(_-< |");
        Bukkit.getLogger().info("| |_|  |_|\\__,_|/_\\_\\  |___/ \\___/|_||_| \\__,_| \\__|\\___|/__/ |");
        Bukkit.getLogger().info(String.format("%-36s", "| v" + version) + "by DjDevs.eu (@djnejk) ❤️ |");
        Bukkit.getLogger().info("|_____________________________________________________________|");

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
