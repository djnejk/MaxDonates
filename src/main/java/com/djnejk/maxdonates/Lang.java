package com.djnejk.maxdonates;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lang {

    private final FileConfiguration config;

    public Lang(MaxDonates plugin, String fileName) {
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

    public List<String> getList(String path) {
        List<String> output = new ArrayList<>();
        for (String raw : config.getStringList(path)) {
            output.add(MessageUtils.colorize(raw.replace("{prefix}", config.getString("prefix", "")).replace("\\n", "\n")));
        }
        return output;
    }
}
