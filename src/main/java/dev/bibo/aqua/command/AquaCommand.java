package dev.bibo.aqua.command;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.form.FormGroup;
import dev.bibo.aqua.form.WaterForm;
import dev.bibo.aqua.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /aqua command: items, granting powers, listing forms, reload. */
public final class AquaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "give", "catalyst", "grant", "revoke", "on", "off", "awaken", "forms", "debug", "reload", "help");

    private final AquaWaterPlugin plugin;

    public AquaCommand(AquaWaterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String pfx = plugin.cfg().prefix;
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label, pfx);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (notAdmin(sender, pfx)) return true;
                Player t = target(sender, args, 1);
                if (t == null) {
                    Msg.send(sender, pfx, "&cУкажи игрока.");
                    return true;
                }
                t.getInventory().addItem(plugin.items().holyWater(1));
                Msg.send(sender, pfx, "&fВыдана &bСвятая Вода&f игроку &b" + t.getName());
            }
            case "catalyst" -> {
                Player t = target(sender, args, 1);
                if (t == null) {
                    Msg.send(sender, pfx, "&cУкажи игрока.");
                    return true;
                }
                boolean self = sender instanceof Player sp && sp.equals(t);
                if (!sender.hasPermission("aquapowers.admin") && !(self && plugin.users().isPowered(t))) {
                    Msg.send(sender, pfx, "&cНет прав.");
                    return true;
                }
                t.getInventory().addItem(plugin.items().catalyst());
                Msg.send(sender, pfx, "&fВыдан &3Водный Тотем&f игроку &b" + t.getName());
            }
            case "grant" -> {
                if (notAdmin(sender, pfx)) return true;
                Player t = target(sender, args, 1);
                if (t == null) {
                    Msg.send(sender, pfx, "&cУкажи игрока.");
                    return true;
                }
                plugin.users().grant(t, true);
                Msg.send(sender, pfx, "&fСила воды дарована &b" + t.getName());
            }
            case "revoke" -> {
                if (notAdmin(sender, pfx)) return true;
                Player t = target(sender, args, 1);
                if (t == null) {
                    Msg.send(sender, pfx, "&cУкажи игрока.");
                    return true;
                }
                plugin.users().revoke(t);
                Msg.send(sender, pfx, "&fСила воды отнята у &b" + t.getName());
            }
            case "on" -> {
                if (notAdmin(sender, pfx)) return true;
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, pfx, "&cТолько для игрока.");
                    return true;
                }
                plugin.users().grant(p, true);
            }
            case "off" -> {
                if (notAdmin(sender, pfx)) return true;
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, pfx, "&cТолько для игрока.");
                    return true;
                }
                plugin.users().revoke(p);
            }
            case "awaken" -> {
                if (notAdmin(sender, pfx)) return true;
                Player t = target(sender, args, 1);
                if (t == null) {
                    Msg.send(sender, pfx, "&cУкажи игрока.");
                    return true;
                }
                plugin.users().debugAwaken(t);
                Msg.send(sender, pfx, "&bАвакенинг + полная стамина выданы &f" + t.getName());
            }
            case "forms" -> {
                Msg.send(sender, pfx, "&bГруппы способностей &7(цифра = группа, затем цифра = способность):");
                int gi = 1;
                for (FormGroup g : plugin.forms().groups()) {
                    sender.sendMessage(Msg.color("&8» " + g.color() + "&l" + gi + ". " + g.name()));
                    int si = 1;
                    for (WaterForm f : g.forms()) {
                        sender.sendMessage(Msg.color("   &e" + si + " " + f.name() + " &7— " + f.description()));
                        si++;
                    }
                    gi++;
                }
            }
            case "debug" -> {
                if (notAdmin(sender, pfx)) return true;
                // The plugin's cost is display entities broadcast to nearby clients, which never shows
                // up in a TPS graph or a profiler — it surfaces as rubber-banding on a "healthy"
                // server. This is the only way to see it.
                var an = plugin.animator();
                Msg.send(sender, pfx, "&bСостояние &7(тик " + an.ticks() + ")");
                sender.sendMessage(Msg.color("&8Эффектов: &f" + an.activeCount()
                        + "&8/&7" + plugin.cfg().maxEffects
                        + " &8| дисплеев: &f" + an.totalDisplays()
                        + "&8/&7" + plugin.cfg().maxDisplays));
                var byType = an.breakdown();
                if (byType.isEmpty()) {
                    sender.sendMessage(Msg.color("&8  (пусто)"));
                } else {
                    byType.entrySet().stream()
                            .sorted((a, b) -> b.getValue() - a.getValue())
                            .forEach(en -> sender.sendMessage(
                                    Msg.color("&8  " + en.getKey() + " &7× " + en.getValue())));
                }
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (!plugin.users().isPowered(pl)) continue;
                    var u = plugin.users().user(pl);
                    sender.sendMessage(Msg.color("&8" + pl.getName()
                            + " &7эфф " + an.countFor(pl.getUniqueId())
                            + " &8| &7стам " + (int) u.getStamina()
                            + " &8| &7стихия " + u.getAttunement().label
                            + " &8| &7шар " + (u.hasOrb() ? u.getOrb().blockCount() : 0)
                            + (u.isAwakening() ? " &c⚡" : "")));
                }
            }
            case "reload" -> {
                if (notAdmin(sender, pfx)) return true;
                plugin.reloadAll();
                if (plugin.cfg().enableRecipes) plugin.items().registerRecipes();
                Msg.send(sender, pfx, "&aКонфиг перезагружен.");
            }
            default -> help(sender, label, pfx);
        }
        return true;
    }

    private void help(CommandSender s, String label, String pfx) {
        Msg.send(s, pfx, "&bAquaPowers &7— управление водой");
        s.sendMessage(Msg.color("&8/" + label + " give [игрок] &7— выдать Святую Воду"));
        s.sendMessage(Msg.color("&8/" + label + " catalyst [игрок] &7— выдать Водный Тотем"));
        s.sendMessage(Msg.color("&8/" + label + " grant|revoke <игрок> &7— дать/забрать силу"));
        s.sendMessage(Msg.color("&8/" + label + " on|off &7— включить/выключить силу себе"));
        s.sendMessage(Msg.color("&8/" + label + " awaken <игрок> &7— выдать Авакенинг (тест)"));
        s.sendMessage(Msg.color("&8/" + label + " forms &7— список всех способностей"));
        s.sendMessage(Msg.color("&8/" + label + " debug &7— живые эффекты, дисплеи, состояние игроков"));
        s.sendMessage(Msg.color("&8/" + label + " reload &7— перезагрузить конфиг"));
        s.sendMessage(Msg.color("&7Управление (рука пуста): &eпервая цифра — группа&7, "
                + "&eвторая — способность&7, &eF &7— сброс."));
    }

    private boolean notAdmin(CommandSender s, String pfx) {
        if (s.hasPermission("aquapowers.admin")) return false;
        Msg.send(s, pfx, "&cНедостаточно прав.");
        return true;
    }

    private Player target(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) return Bukkit.getPlayerExact(args[idx]);
        return sender instanceof Player p ? p : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && Arrays.asList("give", "catalyst", "grant", "revoke", "awaken").contains(args[0].toLowerCase())) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
