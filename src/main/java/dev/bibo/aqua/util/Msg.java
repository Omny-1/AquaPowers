package dev.bibo.aqua.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Small text/colour helpers. */
public final class Msg {

    private Msg() {}

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static void send(CommandSender to, String prefix, String s) {
        to.sendMessage(color(prefix + s));
    }

    /**
     * Almost every piece of feedback this plugin gives goes through here.
     *
     * <p>{@code new TextComponent(legacyString)} drops the string into the component's plain-text
     * field without parsing it, so whether the colours appear at all depends on the client still
     * honouring section signs there for backwards compatibility. {@code fromLegacyText} builds the
     * proper component chain instead, which is the documented contract rather than a leftover.
     */
    public static void actionBar(Player p, String s) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(s)));
    }
}
