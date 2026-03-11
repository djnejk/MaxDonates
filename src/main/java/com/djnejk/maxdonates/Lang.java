package com.djnejk.maxdonates;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

public class Lang {

    private final MaxDonates plugin;
    private FileConfiguration config;

    public Lang(MaxDonates plugin, String fileName) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), fileName);
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path) {
        String raw = config.getString(path, path);
        String prefix = config.getString("prefix", "");
        return MessageUtils.colorize(raw.replace("{prefix}", prefix).replace("\\n", "\n"));
    }

    public String get(String path, Map<String, String> placeholders) {
        String msg = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return msg;
    }
}
