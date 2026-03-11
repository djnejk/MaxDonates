package com.djnejk.maxdonates;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TestCommand implements CommandExecutor {

  private final MaxDonates plugin;

  public TestCommand(MaxDonates plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Tento prikaz muze pouzit jen hrac.");
      return true;
    }

    Economy economy = plugin.getEconomy();
    double balance = economy.getBalance(player);

    player.sendMessage("Mas " + economy.format(economy.getBalance(player)));
    return true;
  }
}