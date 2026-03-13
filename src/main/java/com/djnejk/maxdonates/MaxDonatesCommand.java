package com.djnejk.maxdonates;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MaxDonatesCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final MaxDonates plugin;

    public MaxDonatesCommand(MaxDonates plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Jen hrac.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        try {
            switch (args[0].toLowerCase()) {
                case "donate" -> handleDonate(player, args);
                case "cdonate" -> handleCompanyDonate(player, args);
                case "company", "compan" -> handleCompany(player, args);
                case "description" -> handleProfileDescription(player, args);
                case "recived", "received" -> handleReceived(player, args);
                case "crecived", "creceived" -> handleCompanyReceived(player, args);
                case "companies" -> handleCompanies(player);
                default -> sendHelp(player);
            }
        } catch (Exception ex) {
            player.sendMessage(plugin.getLang().get("messages.error", Map.of("error", ex.getMessage())));
            plugin.getLogger().warning("Command error: " + ex.getMessage());
        }
        return true;
    }

    private void handleDonate(Player player, String[] args) throws Exception {
        if (!player.hasPermission("maxdonates.donate.player")) {
            player.sendMessage(plugin.getLang().get("messages.no-permission"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.getLang().get("messages.usage-donate"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().get("messages.cannot-self"));
            return;
        }
        double amount = parseAmount(args[2]);
        boolean confirm = args.length >= 4 && args[3].equalsIgnoreCase("confirm");
        if (!confirm) {
            sendConfirm(player, plugin.getLang().get("messages.confirm-donate", Map.of("target", args[1], "amount", format(amount))),
                    "/maxdonates donate " + args[1] + " " + args[2] + " confirm");
            return;
        }

        Economy eco = plugin.getEconomy();
        if (!eco.has(player, amount)) {
            player.sendMessage(plugin.getLang().get("messages.not-enough"));
            return;
        }

        EconomyResponse withdraw = eco.withdrawPlayer(player, amount);
        if (!withdraw.transactionSuccess()) {
            player.sendMessage(plugin.getLang().get("messages.error", Map.of("error", withdraw.errorMessage)));
            return;
        }
        eco.depositPlayer(target, amount);
        plugin.getDatabaseManager().addPlayerDonation(player.getUniqueId(), target.getUniqueId(), amount, target.isOnline());

        player.sendMessage(plugin.getLang().get("messages.donate-sent", Map.of("target", args[1], "amount", format(amount))));
        if (target.isOnline()) {
            Objects.requireNonNull(target.getPlayer()).sendMessage(plugin.getLang().get("messages.donate-received", Map.of("donor", player.getName(), "amount", format(amount))));
        }
    }

    private void handleCompanyDonate(Player player, String[] args) throws Exception {
        if (!player.hasPermission("maxdonates.donate.company")) {
            player.sendMessage(plugin.getLang().get("messages.no-permission"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.getLang().get("messages.usage-cdonate"));
            return;
        }
        double amount = parseAmount(args[2]);
        boolean confirm = args.length >= 4 && args[3].equalsIgnoreCase("confirm");
        if (!confirm) {
            sendConfirm(player, plugin.getLang().get("messages.confirm-cdonate", Map.of("company", args[1], "amount", format(amount))),
                    "/maxdonates cdonate " + args[1] + " " + args[2] + " confirm");
            return;
        }
        DatabaseManager.Company company = plugin.getDatabaseManager().getCompanyByName(args[1]);
        if (company == null) {
            player.sendMessage(plugin.getLang().get("messages.company-not-found"));
            return;
        }
        if (company.owner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().get("messages.cannot-donate-own-company"));
            return;
        }

        Economy eco = plugin.getEconomy();
        if (!eco.has(player, amount)) {
            player.sendMessage(plugin.getLang().get("messages.not-enough"));
            return;
        }
        EconomyResponse withdraw = eco.withdrawPlayer(player, amount);
        if (!withdraw.transactionSuccess()) {
            player.sendMessage(plugin.getLang().get("messages.error", Map.of("error", withdraw.errorMessage)));
            return;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(company.owner());
        eco.depositPlayer(owner, amount);
        plugin.getDatabaseManager().addCompanyDonation(player.getUniqueId(), company.id(), company.owner(), amount, owner.isOnline());
        player.sendMessage(plugin.getLang().get("messages.cdonate-sent", Map.of("company", company.name(), "amount", format(amount))));
    }

    private void handleCompany(Player player, String[] args) throws Exception {
        if (args.length < 2) {
            player.sendMessage(plugin.getLang().get("messages.usage-company"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (!player.hasPermission("maxdonates.company.create")) {
                    player.sendMessage(plugin.getLang().get("messages.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(plugin.getLang().get("messages.usage-company-create"));
                    return;
                }
                int limit = getCompanyLimit(player);
                int count = plugin.getDatabaseManager().getCompanyCount(player.getUniqueId());
                if (limit >= 0 && count >= limit) {
                    player.sendMessage(plugin.getLang().get("messages.company-limit", Map.of("limit", String.valueOf(limit))));
                    return;
                }
                if (plugin.getDatabaseManager().createCompany(args[2], player.getUniqueId())) {
                    player.sendMessage(plugin.getLang().get("messages.company-created", Map.of("company", args[2])));
                }
            }
            case "remove" -> {
                if (!player.hasPermission("maxdonates.company.remove")) {
                    player.sendMessage(plugin.getLang().get("messages.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(plugin.getLang().get("messages.usage-company-remove"));
                    return;
                }
                boolean confirm = args.length >= 4 && args[3].equalsIgnoreCase("confirm");
                if (!confirm) {
                    sendConfirm(player, plugin.getLang().get("messages.confirm-company-remove", Map.of("company", args[2])),
                            "/maxdonates company remove " + args[2] + " confirm");
                    return;
                }
                boolean removed = plugin.getDatabaseManager().removeCompany(args[2], player.getUniqueId());
                player.sendMessage(removed
                        ? plugin.getLang().get("messages.company-removed", Map.of("company", args[2]))
                        : plugin.getLang().get("messages.company-not-found"));
            }
            case "list" -> {
                UUID owner = player.getUniqueId();
                String ownerName = player.getName();
                if (args.length >= 3) {
                    if (!player.hasPermission("maxdonates.company.list.other")) {
                        player.sendMessage(plugin.getLang().get("messages.no-permission"));
                        return;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                    owner = target.getUniqueId();
                    ownerName = args[2];
                } else if (!player.hasPermission("maxdonates.company.list.own")) {
                    player.sendMessage(plugin.getLang().get("messages.no-permission"));
                    return;
                }

                List<DatabaseManager.Company> companies = plugin.getDatabaseManager().getCompaniesByOwner(owner);
                player.sendMessage(plugin.getLang().get("messages.company-list-header", Map.of("player", ownerName)));
                for (DatabaseManager.Company c : companies) {
                    player.sendMessage(plugin.getLang().get("messages.company-list-line", Map.of("company", c.name(), "description", String.valueOf(c.description()))));
                }
            }
            case "description" -> {
                if (args.length < 5) {
                    player.sendMessage(plugin.getLang().get("messages.usage-company-description"));
                    return;
                }
                String company = args[2];
                if (args[3].equalsIgnoreCase("edit")) {
                    if (!player.hasPermission("maxdonates.company.description.edit")) {
                        player.sendMessage(plugin.getLang().get("messages.no-permission"));
                        return;
                    }
                    String text = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                    boolean ok = plugin.getDatabaseManager().updateCompanyDescription(company, player.getUniqueId(), text);
                    player.sendMessage(ok ? plugin.getLang().get("messages.company-description-edited") : plugin.getLang().get("messages.company-not-found"));
                } else if (args[3].equalsIgnoreCase("remove")) {
                    if (!player.hasPermission("maxdonates.company.description.remove")) {
                        player.sendMessage(plugin.getLang().get("messages.no-permission"));
                        return;
                    }
                    boolean ok = plugin.getDatabaseManager().updateCompanyDescription(company, player.getUniqueId(), null);
                    player.sendMessage(ok ? plugin.getLang().get("messages.company-description-removed") : plugin.getLang().get("messages.company-not-found"));
                }
            }
            case "give" -> {
                if (!player.hasPermission("maxdonates.company.give")) {
                    player.sendMessage(plugin.getLang().get("messages.no-permission"));
                    return;
                }
                if (args.length < 4) {
                    player.sendMessage(plugin.getLang().get("messages.usage-company-give"));
                    return;
                }
                String company = args[2];
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
                boolean confirm = args.length >= 5 && args[4].equalsIgnoreCase("confirm");
                if (!confirm) {
                    sendConfirm(player, plugin.getLang().get("messages.confirm-company-give", Map.of("company", company, "target", args[3])),
                            "/maxdonates company give " + company + " " + args[3] + " confirm");
                    return;
                }
                int limit = getCompanyLimit(target);
                int current = plugin.getDatabaseManager().getCompanyCount(target.getUniqueId());
                if (limit >= 0 && current >= limit) {
                    player.sendMessage(plugin.getLang().get("messages.company-target-limit", Map.of("target", args[3], "limit", String.valueOf(limit))));
                    return;
                }
                boolean ok = plugin.getDatabaseManager().transferCompany(company, player.getUniqueId(), target.getUniqueId());
                player.sendMessage(ok ? plugin.getLang().get("messages.company-given", Map.of("company", company, "target", args[3])) : plugin.getLang().get("messages.company-not-found"));
            }
            default -> player.sendMessage(plugin.getLang().get("messages.usage-company"));
        }
    }

    private void handleProfileDescription(Player player, String[] args) throws Exception {
        if (args.length < 2) {
            player.sendMessage(plugin.getLang().get("messages.usage-description"));
            return;
        }
        if (args[1].equalsIgnoreCase("edit")) {
            if (!player.hasPermission("maxdonates.description.edit")) {
                player.sendMessage(plugin.getLang().get("messages.no-permission"));
                return;
            }
            if (args.length < 3) {
                player.sendMessage(plugin.getLang().get("messages.usage-description"));
                return;
            }
            String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            plugin.getDatabaseManager().setPlayerDescription(player.getUniqueId(), text);
            player.sendMessage(plugin.getLang().get("messages.description-edited"));
        } else if (args[1].equalsIgnoreCase("remove")) {
            if (!player.hasPermission("maxdonates.description.remove")) {
                player.sendMessage(plugin.getLang().get("messages.no-permission"));
                return;
            }
            plugin.getDatabaseManager().setPlayerDescription(player.getUniqueId(), null);
            player.sendMessage(plugin.getLang().get("messages.description-removed"));
        }
    }

    private void handleReceived(Player player, String[] args) throws Exception {
        UUID targetUuid = player.getUniqueId();
        String targetName = player.getName();
        int page = 1;

        if (!player.hasPermission("maxdonates.lookup.player.own")) {
            player.sendMessage(plugin.getLang().get("messages.no-permission"));
            return;
        }

        if (args.length >= 2) {
            if (isNumber(args[1])) page = Integer.parseInt(args[1]);
            else {
                if (!player.hasPermission("maxdonates.lookup.player.other")) {
                    player.sendMessage(plugin.getLang().get("messages.no-permission"));
                    return;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                targetUuid = t.getUniqueId();
                targetName = args[1];
                if (args.length >= 3 && isNumber(args[2])) page = Integer.parseInt(args[2]);
            }
        }

        DatabaseManager.Stats stats = plugin.getDatabaseManager().getPlayerStats(targetUuid);
        List<DatabaseManager.DonationRecord> list = plugin.getDatabaseManager().getPlayerDonations(targetUuid, page, plugin.getConfig().getInt("history.page-size", 10));

        player.sendMessage(plugin.getLang().get("messages.received-header", Map.of("target", targetName, "page", String.valueOf(page))));
        player.sendMessage(plugin.getLang().get("messages.received-stats", Map.of("total", format(stats.total()), "day", format(stats.day()), "week", format(stats.week()), "month", format(stats.month()), "year", format(stats.year()))));
        for (DatabaseManager.DonationRecord record : list) {
            OfflinePlayer donor = Bukkit.getOfflinePlayer(record.donor());
            double donorTotal = plugin.getDatabaseManager().totalSentBy(record.donor());
            TextComponent line = new TextComponent(MessageUtils.colorize(plugin.getLang().get("messages.received-line", Map.of(
                    "donor", donor.getName() == null ? record.donor().toString() : donor.getName(),
                    "amount", format(record.amount()),
                    "date", DATE_FORMATTER.format(record.createdAt())
            ))));
            line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(MessageUtils.colorize(plugin.getLang().get("messages.received-hover", Map.of("sent_total", format(donorTotal))))).create()));
            player.spigot().sendMessage(line);
        }
    }

    private void handleCompanyReceived(Player player, String[] args) throws Exception {
        if (!player.hasPermission("maxdonates.lookup.company.own")) {
            player.sendMessage(plugin.getLang().get("messages.no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getLang().get("messages.usage-crecived"));
            return;
        }
        String companyName = args[1];
        int page = args.length >= 3 && isNumber(args[2]) ? Integer.parseInt(args[2]) : 1;
        DatabaseManager.Company company = plugin.getDatabaseManager().getCompanyByName(companyName);
        if (company == null) {
            player.sendMessage(plugin.getLang().get("messages.company-not-found"));
            return;
        }
        if (!company.owner().equals(player.getUniqueId()) && !player.hasPermission("maxdonates.lookup.company.other")) {
            player.sendMessage(plugin.getLang().get("messages.no-permission"));
            return;
        }

        DatabaseManager.Stats stats = plugin.getDatabaseManager().getCompanyStats(company.id());
        List<DatabaseManager.DonationRecord> list = plugin.getDatabaseManager().getCompanyDonations(company.id(), page, plugin.getConfig().getInt("history.page-size", 10));

        player.sendMessage(plugin.getLang().get("messages.creceived-header", Map.of("company", companyName, "page", String.valueOf(page))));
        player.sendMessage(plugin.getLang().get("messages.received-stats", Map.of("total", format(stats.total()), "day", format(stats.day()), "week", format(stats.week()), "month", format(stats.month()), "year", format(stats.year()))));
        for (DatabaseManager.DonationRecord record : list) {
            OfflinePlayer donor = Bukkit.getOfflinePlayer(record.donor());
            player.sendMessage(plugin.getLang().get("messages.received-line", Map.of("donor", donor.getName() == null ? record.donor().toString() : donor.getName(), "amount", format(record.amount()), "date", DATE_FORMATTER.format(record.createdAt()))));
        }
    }


    private void handleCompanies(Player player) throws Exception {
        if (!player.hasPermission("maxdonates.companies")) {
            player.sendMessage(plugin.getLang().get("messages.no-permission"));
            return;
        }

        List<String> companies = plugin.getDatabaseManager().getAllCompanyNames();
        if (companies.isEmpty()) {
            player.sendMessage(plugin.getLang().get("messages.companies-empty"));
            return;
        }

        player.sendMessage(plugin.getLang().get("messages.companies-header"));
        for (String company : companies) {
            String suggest = "/maxdonates cdonate " + company + " ";
            TextComponent line = new TextComponent(MessageUtils.colorize(
                    plugin.getLang().get("messages.companies-line", Map.of("company", company))
            ));
            line.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggest));
            line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(MessageUtils.colorize(plugin.getLang().get("messages.companies-hover", Map.of("company", company)))).create()));
            player.spigot().sendMessage(line);
        }
    }

    private void sendConfirm(Player player, String text, String command) {
        player.sendMessage(text);
        TextComponent component = new TextComponent(MessageUtils.colorize(plugin.getLang().get("messages.click-confirm")));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(command).create()));
        player.spigot().sendMessage(component);
    }

    private double parseAmount(String arg) {
        double amount = Double.parseDouble(arg);
        if (amount <= 0) throw new IllegalArgumentException("Castka musi byt > 0");
        return amount;
    }

    private boolean isNumber(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private int getCompanyLimit(OfflinePlayer player) {
        if (player.isOp() || player.getPlayer() != null && player.getPlayer().hasPermission("maxdonates.company.create.*")) {
            return -1;
        }
        int best = -1;
        if (player.getPlayer() == null) return 0;
        for (org.bukkit.permissions.PermissionAttachmentInfo info : player.getPlayer().getEffectivePermissions()) {
            String perm = info.getPermission().toLowerCase();
            if (!perm.startsWith("maxdonates.company.create.")) continue;
            String suffix = perm.substring("maxdonates.company.create.".length());
            if (suffix.equals("*")) return -1;
            if (suffix.matches("\\d+")) {
                best = Math.max(best, Integer.parseInt(suffix));
            }
        }
        return best < 0 ? plugin.getConfig().getInt("company.default-limit", 1) : best;
    }

    private String format(double amount) {
        return plugin.getEconomy().format(amount);
    }

    private void sendHelp(Player player) {
        for (String line : plugin.getLang().getList("help")) {
            player.sendMessage(line);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterByStart(List.of("donate", "cdonate", "company", "companies", "description", "recived", "crecived"), args[0]);
        }

        String root = args[0].toLowerCase();
        if (root.equals("donate")) {
            if (args.length == 2) return filterByStart(onlinePlayerNames(player, true), args[1]);
            if (args.length == 4) return filterByStart(List.of("confirm"), args[3]);
        }

        if (root.equals("cdonate")) {
            if (args.length == 2) return filterByStart(companyNames(), args[1]);
            if (args.length == 4) return filterByStart(List.of("confirm"), args[3]);
        }

        if (root.equals("recived") || root.equals("received")) {
            if (args.length == 2) return filterByStart(onlinePlayerNames(player, false), args[1]);
        }

        if (root.equals("crecived") || root.equals("creceived")) {
            if (args.length == 2) return filterByStart(companyNames(), args[1]);
        }

        if (root.equals("description")) {
            if (args.length == 2) return filterByStart(List.of("edit", "remove"), args[1]);
        }

        if (root.equals("company") || root.equals("compan")) {
            if (args.length == 2) return filterByStart(List.of("create", "remove", "list", "description", "give"), args[1]);

            String sub = args[1].toLowerCase();
            if (sub.equals("remove") && args.length == 3) return filterByStart(ownedCompanyNames(player.getUniqueId()), args[2]);
            if (sub.equals("remove") && args.length == 4) return filterByStart(List.of("confirm"), args[3]);

            if (sub.equals("list") && args.length == 3) return filterByStart(onlinePlayerNames(player, false), args[2]);

            if (sub.equals("description") && args.length == 3) return filterByStart(ownedCompanyNames(player.getUniqueId()), args[2]);
            if (sub.equals("description") && args.length == 4) return filterByStart(List.of("edit", "remove"), args[3]);

            if (sub.equals("give") && args.length == 3) return filterByStart(ownedCompanyNames(player.getUniqueId()), args[2]);
            if (sub.equals("give") && args.length == 4) return filterByStart(onlinePlayerNames(player, true), args[3]);
            if (sub.equals("give") && args.length == 5) return filterByStart(List.of("confirm"), args[4]);
        }

        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames(Player viewer, boolean excludeSelf) {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (excludeSelf && online.getUniqueId().equals(viewer.getUniqueId())) continue;
            names.add(online.getName());
        }
        return names;
    }

    private List<String> companyNames() {
        try {
            return plugin.getDatabaseManager().getAllCompanyNames();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> ownedCompanyNames(UUID owner) {
        try {
            List<String> names = new ArrayList<>();
            for (DatabaseManager.Company company : plugin.getDatabaseManager().getCompaniesByOwner(owner)) {
                names.add(company.name());
            }
            return names;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> filterByStart(List<String> values, String input) {
        String lower = input == null ? "" : input.toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(lower)) {
                filtered.add(value);
            }
        }
        return filtered;
    }
}
