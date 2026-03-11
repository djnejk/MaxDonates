package com.djnejk.maxdonates;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DonationNotificationListener implements Listener {

    private final MaxDonates plugin;

    public DonationNotificationListener(MaxDonates plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<DatabaseManager.PendingDonation> playerPending = plugin.getDatabaseManager().getPendingPlayerDonations(player.getUniqueId());
                List<DatabaseManager.PendingDonation> companyPending = plugin.getDatabaseManager().getPendingCompanyDonations(player.getUniqueId());

                if (playerPending.isEmpty() && companyPending.isEmpty()) {
                    return;
                }

                double playerTotal = playerPending.stream().mapToDouble(DatabaseManager.PendingDonation::amount).sum();
                double companyTotal = companyPending.stream().mapToDouble(DatabaseManager.PendingDonation::amount).sum();

                Set<String> playerDonors = new LinkedHashSet<>();
                Set<String> companyDonors = new LinkedHashSet<>();

                for (DatabaseManager.PendingDonation donation : playerPending) {
                    playerDonors.add(resolveName(donation.donor()));
                }
                for (DatabaseManager.PendingDonation donation : companyPending) {
                    companyDonors.add(resolveName(donation.donor()));
                }

                List<Long> playerIds = new ArrayList<>();
                for (DatabaseManager.PendingDonation pending : playerPending) {
                    playerIds.add(pending.id());
                }

                List<Long> companyIds = new ArrayList<>();
                for (DatabaseManager.PendingDonation pending : companyPending) {
                    companyIds.add(pending.id());
                }

                plugin.getDatabaseManager().markPlayerDonationsNotified(playerIds);
                plugin.getDatabaseManager().markCompanyDonationsNotified(companyIds);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.getLang().get("messages.offline-report-header"));
                    if (!playerPending.isEmpty()) {
                        player.sendMessage(plugin.getLang().get("messages.offline-report-player", java.util.Map.of(
                                "total", plugin.getEconomy().format(playerTotal),
                                "donors", String.join(", ", playerDonors)
                        )));
                    }
                    if (!companyPending.isEmpty()) {
                        player.sendMessage(plugin.getLang().get("messages.offline-report-company", java.util.Map.of(
                                "total", plugin.getEconomy().format(companyTotal),
                                "donors", String.join(", ", companyDonors)
                        )));
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Nepodarilo se nacist offline notifikace: " + ex.getMessage());
            }
        });
    }

    private String resolveName(java.util.UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        return p.getName() != null ? p.getName() : uuid.toString();
    }
}
