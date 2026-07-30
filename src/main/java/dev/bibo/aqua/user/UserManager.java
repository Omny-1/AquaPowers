package dev.bibo.aqua.user;

import dev.bibo.aqua.AquaConfig;
import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.Keys;
import dev.bibo.aqua.effect.BarrierEffect;
import dev.bibo.aqua.effect.NukeEffect;
import dev.bibo.aqua.effect.PartWaterEffect;
import dev.bibo.aqua.env.Attunement;
import dev.bibo.aqua.form.FormContext;
import dev.bibo.aqua.form.FormGroup;
import dev.bibo.aqua.form.Selection;
import dev.bibo.aqua.form.WaterForm;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.orb.WaterCollector;
import dev.bibo.aqua.orb.WaterOrb;
import dev.bibo.aqua.util.Msg;
import dev.bibo.aqua.util.Targeting;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Owns powers, ability selection, stamina, awakening, environment and the HUD. */
public final class UserManager {

    private final AquaWaterPlugin plugin;
    private final Keys keys;
    private final Map<UUID, HydroUser> online = new HashMap<>();

    public UserManager(AquaWaterPlugin plugin) {
        this.plugin = plugin;
        this.keys = plugin.keys();
    }

    private AquaConfig cfg() { return plugin.cfg(); }

    /** The plugin's monotonic clock. See {@link HydroUser} for why it isn't the wall clock. */
    private long now() { return plugin.animator().ticks(); }

    /**
     * The single gate every ability, HUD update and input path runs through.
     *
     * <p>{@code aquapowers.use} is checked here rather than nowhere: it was advertised in plugin.yml
     * and never consulted, so a server owner who denied it saw no effect at all — a published
     * permission that quietly does nothing is worse than not offering one.
     */
    public boolean isPowered(Player p) {
        return p.hasPermission("aquapowers.use")
                && p.getPersistentDataContainer().has(keys.powered, PersistentDataType.BYTE);
    }

    public HydroUser user(Player p) {
        HydroUser existing = online.get(p.getUniqueId());
        if (existing != null) return existing;
        HydroUser u = new HydroUser(p.getUniqueId());
        u.setStamina(cfg().staminaMax);
        online.put(p.getUniqueId(), u);
        load(p, u);
        return u;
    }

    // ---- persistence --------------------------------------------------------

    /**
     * Restore what the player had when they left.
     *
     * <p>Nothing but the "powered" flag used to survive a disconnect, so quitting and rejoining
     * refilled stamina, cleared every ability cooldown and wiped the five-minute cooldown on the
     * ultimate — a free reset available on demand. Cooldowns are stored as <b>ticks remaining</b>
     * rather than a timestamp, deliberately: a timestamp keeps counting down while the server is off
     * and would hand the ultimate back after any overnight restart.
     */
    private void load(Player p, HydroUser u) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Double stam = pdc.get(keys.savedStamina, PersistentDataType.DOUBLE);
        if (stam != null) u.setStamina(Math.max(0, Math.min(cfg().staminaMax, stam)));
        Double aw = pdc.get(keys.savedAwakening, PersistentDataType.DOUBLE);
        if (aw != null) u.setAwakeningCharge(Math.max(0, Math.min(cfg().awakeningMax, aw)));

        Integer nukeLeft = pdc.get(keys.savedNukeCd, PersistentDataType.INTEGER);
        if (nukeLeft != null && nukeLeft > 0) u.setNukeReadyTick(now() + nukeLeft);

        String cds = pdc.get(keys.savedFormCds, PersistentDataType.STRING);
        if (cds != null && !cds.isEmpty()) {
            for (String part : cds.split(";")) {
                int sep = part.lastIndexOf(':');
                if (sep <= 0) continue;
                try {
                    long left = Long.parseLong(part.substring(sep + 1));
                    // Stored as "how much is left"; rebased onto the current tick.
                    if (left > 0) u.setFormUse(part.substring(0, sep), now() + left - cooldownTicks(part.substring(0, sep)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private long cooldownTicks(String formId) {
        for (WaterForm f : plugin.forms().all()) {
            if (f.id().equals(formId)) return f.cooldownTicks();
        }
        return 0;
    }

    /** Write the parts of a bender's state that must not be resettable by reconnecting. */
    public void save(Player p) {
        HydroUser u = online.get(p.getUniqueId());
        if (u == null) return;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(keys.savedStamina, PersistentDataType.DOUBLE, u.getStamina());
        pdc.set(keys.savedAwakening, PersistentDataType.DOUBLE, u.getAwakeningCharge());
        pdc.set(keys.savedNukeCd, PersistentDataType.INTEGER,
                (int) Math.max(0, u.getNukeReadyTick() - now()));

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : u.formUses().entrySet()) {
            long cd = cooldownTicks(e.getKey());
            long left = cd - (now() - e.getValue());
            if (left <= 0) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append(':').append(left);
        }
        pdc.set(keys.savedFormCds, PersistentDataType.STRING, sb.toString());
    }

    public void ensureOnline(Player p) {
        if (isPowered(p)) {
            user(p);
            updateBar(user(p), p);
        }
    }

    public void grant(Player p, boolean giveCatalyst) {
        p.getPersistentDataContainer().set(keys.powered, PersistentDataType.BYTE, (byte) 1);
        HydroUser u = user(p);
        u.setStamina(cfg().staminaMax);
        u.setCurrentGroup(Selection.NO_GROUP);
        u.setSelected(null);
        // Repeated /aqua grant used to stack undroppable totems that the player then could not
        // get rid of by any normal means.
        if (giveCatalyst && !plugin.items().hasCatalystInInventory(p)) plugin.items().giveCatalyst(p);
        updateBar(u, p);
        updateHud(u, p);
        Msg.send(p, cfg().prefix, "&fТы пробудил &bсилу воды&f! &3Тотем &fдержи &eв левой руке&f.");
        Msg.send(p, cfg().prefix, "&7Пока рука пуста: &eпервая цифра — группа&7, &eвторая — способность&7. "
                + "&eF &7— сброс, снова выбираешь группу.");
        Msg.send(p, cfg().prefix, "&7&oЦифра, под которой лежит предмет, просто берёт предмет — "
                + "держи нижние слоты пустыми.");
        WorldFx.sound(plugin, p.getLocation(), "minecraft:block.beacon.activate", 1.0f, 1.4f);
    }

    public void revoke(Player p) {
        disperse(p);
        clearArmedNuke(p);
        plugin.animator().dropOwnedBy(p.getUniqueId());
        HydroUser u = online.get(p.getUniqueId());
        if (u != null) releaseHud(u, p);
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(keys.powered);
        pdc.remove(keys.savedStamina);
        pdc.remove(keys.savedAwakening);
        pdc.remove(keys.savedNukeCd);
        pdc.remove(keys.savedFormCds);
        online.remove(p.getUniqueId());
        Msg.send(p, cfg().prefix, "&7Сила воды покинула тебя.");
    }

    /**
     * Cancel and forget any charged ultimate.
     *
     * <p>Dying used to leave the reference in place while the effect itself was reaped by the
     * animator. The stale object still reported {@code READY}, so the next click "fired" it: the
     * title printed, stamina went to zero, the five-minute cooldown started, and nothing exploded.
     */
    public void clearArmedNuke(Player p) {
        HydroUser u = online.get(p.getUniqueId());
        if (u == null || u.getArmedNuke() == null) return;
        u.getArmedNuke().cancel();
        u.setArmedNuke(null);
    }

    public void onQuit(Player p) {
        save(p);
        HydroUser u = online.get(p.getUniqueId());
        if (u != null) {
            if (u.hasOrb()) u.getOrb().disperse();
            if (u.getArmedNuke() != null) u.getArmedNuke().cancel();
            releaseHud(u, p);
        }
        plugin.animator().dropOwnedBy(p.getUniqueId());
        online.remove(p.getUniqueId());
    }

    public void disperseAll() {
        for (HydroUser u : online.values()) {
            if (u.hasOrb()) u.getOrb().disperse();
            if (u.getArmedNuke() != null) u.getArmedNuke().cancel();
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            save(p);
            HydroUser u = online.get(p.getUniqueId());
            if (u != null) releaseHud(u, p);
        }
    }

    // ---- selection ----------------------------------------------------------

    /**
     * A number key was pressed: first press chooses a group, the next chooses an ability inside it.
     * {@link #resetSelection} (F) goes back to choosing a group.
     */
    public void pressNumber(Player p, int number) {
        if (!isPowered(p)) return;
        HydroUser u = user(p);
        int groupCount = plugin.forms().groupCount();
        FormGroup current = plugin.forms().group(u.getCurrentGroup());
        int abilityCount = current == null ? 0 : current.size();

        Selection.Press r = Selection.press(u.getCurrentGroup(), number, groupCount, abilityCount);
        switch (r.kind()) {
            case GROUP -> {
                FormGroup g = plugin.forms().group(r.group());
                u.setCurrentGroup(r.group());
                u.setSelected(null);
                Msg.actionBar(p, g.color() + "▸ " + g.name() + " &8» " + groupList(g));
                WorldFx.sound(plugin, p.getLocation(), "minecraft:block.note_block.hat", 0.7f, 1.2f);
            }
            case ABILITY -> {
                WaterForm f = current.get(r.ability());
                u.setSelected(f);
                Msg.actionBar(p, "&aВыбрано: " + f.name()
                        + " &8(" + (int) costOf(u, f) + " ст.) " + strip(current, f));
                WorldFx.sound(plugin, p.getLocation(), "minecraft:block.note_block.bell", 0.7f, 1.5f);
            }
            case REJECTED -> {
                Msg.actionBar(p, u.getCurrentGroup() == Selection.NO_GROUP
                        ? "&cНет группы " + number + " &8(есть 1-" + groupCount + ")"
                        : "&cВ группе нет способности " + number + " &8(есть 1-" + abilityCount + ")");
                WorldFx.sound(plugin, p.getLocation(), "minecraft:block.note_block.bass", 0.5f, 0.8f);
                return;
            }
        }
        updateHud(u, p);
        updateBar(u, p);
    }

    /** F: drop back to "choose a group". */
    public void resetSelection(Player p) {
        if (!isPowered(p)) return;
        HydroUser u = user(p);
        u.setCurrentGroup(Selection.NO_GROUP);
        u.setSelected(null);
        Msg.actionBar(p, "&7Сброс — выбери &eгруппу 1-" + plugin.forms().groupCount());
        WorldFx.sound(plugin, p.getLocation(), "minecraft:block.note_block.bass", 0.7f, 0.8f);
        updateHud(u, p);
        updateBar(u, p);
    }

    private String groupList(FormGroup g) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < g.size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append("&e").append(i + 1).append("&7")
                    .append(ChatColor.stripColor(Msg.color(g.get(i).name())));
        }
        return sb.toString();
    }

    private String strip(FormGroup g, WaterForm sel) {
        StringBuilder sb = new StringBuilder("&8");
        for (WaterForm f : g.forms()) sb.append(f == sel ? "&f●" : "&8○");
        return sb.toString();
    }

    // ---- collect / fire / disperse -----------------------------------------

    private List<Location> skyWater(Player p, Attunement att, int count) {
        List<Location> out = new ArrayList<>();
        if (!cfg().skyCollection || !att.freeWater() || count <= 0) return out;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location base = p.getLocation().add(0, p.getHeight() + 2.0, 0);
        for (int i = 0; i < count; i++) {
            out.add(base.clone().add(
                    (r.nextDouble() * 2 - 1) * 5.0,
                    r.nextDouble() * 4.0,
                    (r.nextDouble() * 2 - 1) * 5.0));
        }
        return out;
    }

    /** Shared gate for anything that would add display entities. Refuses out loud, never silently. */
    private boolean budgetOk(Player p, int displays) {
        if (plugin.animator().hasRoom(p.getUniqueId(), displays)) return true;
        Msg.actionBar(p, "&cСлишком много воды в игре — подожди секунду");
        WorldFx.sound(plugin, p.getLocation(), "minecraft:block.fire.extinguish", 0.5f, 1.2f);
        return false;
    }

    public void collect(Player p) {
        if (!isPowered(p)) return;
        HydroUser u = user(p);
        AquaConfig cfg = cfg();
        Attunement att = attunementOf(p, u);

        if (u.hasOrb()) {
            WaterOrb orb = u.getOrb();
            if (orb.isFull()) {
                Msg.actionBar(p, "&bШар воды уже полный");
                return;
            }
            int missing = cfg.maxBlocks - orb.blockCount();
            if (!budgetOk(p, missing)) return;
            Location aimT = Targeting.lookPoint(p, cfg.collectRadius + 4);
            List<WaterCollector.Drained> more = WaterCollector.findSources(p, aimT, cfg.collectRadius, missing);
            if (more.isEmpty()) {
                more = WaterCollector.findSources(p, p.getLocation(), cfg.collectRadius, missing);
            }
            List<Location> sky = skyWater(p, att, missing - more.size());
            if (more.isEmpty() && sky.isEmpty()) {
                Msg.actionBar(p, "&7Рядом больше нет воды для дозабора");
                return;
            }
            if (cfg.drainSource) WaterCollector.drain(plugin, more);
            orb.topUp(more, sky);
            int pctNow = (int) Math.round(Math.min(1.0, orb.blockCount() / (double) cfg.maxBlocks) * 100);
            Msg.actionBar(p, "&bДозабор воды... &7(" + orb.blockCount() + " • " + pctNow + "%) "
                    + att.display());
            return;
        }

        Location aim = Targeting.lookPoint(p, cfg.collectRadius + 4);
        List<WaterCollector.Drained> sources = WaterCollector.findSources(p, aim, cfg.collectRadius, cfg.maxBlocks);
        if (sources.size() < cfg.minBlocks) {
            sources = WaterCollector.findSources(p, p.getLocation(), cfg.collectRadius, cfg.maxBlocks);
        }
        List<Location> sky = skyWater(p, att, cfg.maxBlocks - sources.size());
        int total = sources.size() + sky.size();
        if (total < cfg.minBlocks) {
            Msg.actionBar(p, "&cРядом слишком мало воды &8(" + att.display() + "&8)");
            WorldFx.sound(plugin, p.getLocation(), "minecraft:block.fire.extinguish", 0.6f, 1.6f);
            return;
        }
        if (!budgetOk(p, total)) return;
        if (cfg.drainSource) WaterCollector.drain(plugin, sources);
        int duration = u.isAwakening()
                ? (int) Math.max(4, cfg.collectDuration / Math.max(1.0, cfg.awakeningCollectMult))
                : cfg.collectDuration;
        WaterOrb orb = new WaterOrb(plugin, p, sources, sky, duration);
        plugin.animator().add(orb);
        u.setOrb(orb);
        p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().add(0, 0.2, 0), 24, 0.4, 0.3, 0.4, 0.05);
        p.getWorld().spawnParticle(Particle.FALLING_WATER, p.getLocation().add(0, 1.0, 0), 16, 0.4, 0.6, 0.4, 0.0);
        if (!sky.isEmpty()) {
            WorldFx.sound(plugin, p.getLocation(), "minecraft:weather.rain.above", 1.2f, 1.4f);
        }
        WaterForm f = u.getSelected();
        int pct = (int) Math.round(Math.min(1.0, total / (double) cfg.maxBlocks) * 100);
        Msg.actionBar(p, "&bВода поднимается... &7(" + total + " • " + pct + "%) &8| "
                + (f != null ? f.name() : "") + " &8| " + att.display());
    }

    /** What this ability costs right now, environment and awakening included. */
    public double costOf(HydroUser u, WaterForm form) {
        return costOf(u, form, u.getAttunement());
    }

    private double costOf(HydroUser u, WaterForm form, Attunement att) {
        if (form == null) return 0;
        double cost = form.staminaCost() * (u.isAwakening() ? cfg().awakeningCostMult : 1.0);
        if (cfg().environmentEnabled) cost *= att.costMult;
        return cost;
    }

    public void fire(Player p) {
        if (!isPowered(p)) return;
        HydroUser u = user(p);
        WaterForm form = u.getSelected();
        if (form == null) {
            Msg.actionBar(p, u.getCurrentGroup() == Selection.NO_GROUP
                    ? "&7Выбери &eгруппу 1-" + plugin.forms().groupCount() + "&7, затем способность"
                    : "&7Группа выбрана — теперь жми &eцифру способности");
            return;
        }
        // Bending from inside a tank, a helicopter or a train is undefined at best — Прибой would
        // start driving a passenger's velocity — and this server has a garage full of them.
        if (p.getVehicle() != null) {
            Msg.actionBar(p, "&7Из техники бендить нельзя");
            return;
        }

        // Resolve the environment ONCE, so the price charged and the power delivered agree. They
        // could disagree by up to two seconds when the cost read a cached value and the cast
        // refreshed it afterwards.
        Attunement att = attunementOf(p, u);

        if (form.id().equals("nuke")) {
            handleNuke(p, u);
            return;
        }
        if (form.awakeningOnly() && !u.isAwakening()) {
            Msg.actionBar(p, "&cСпособность только в Авакенинге");
            return;
        }
        if (form.needsEntityTarget() && Targeting.targetEntity(p, 30) == null) {
            Msg.actionBar(p, "&7Нужна цель — наведись на моба или игрока");
            return;
        }
        if (form.id().equals("part") && PartWaterEffect.activeFor(p.getUniqueId()) >= PartWaterEffect.MAX_ACTIVE) {
            Msg.actionBar(p, "&7Уже активно " + PartWaterEffect.MAX_ACTIVE + " «Расступись»");
            return;
        }
        if (form.id().equals("barrier") && BarrierEffect.isActive(p.getUniqueId())) {
            Msg.actionBar(p, "&7Барьер уже держится");
            return;
        }

        long now = now();
        if (now - u.getLastUseTick() < cfg().cooldownTicks) return;

        long cdRemain = form.cooldownTicks() - (now - u.getFormUse(form.id()));
        if (cdRemain > 0) {
            Msg.actionBar(p, form.name() + " &cна перезарядке: "
                    + String.format("%.1f", cdRemain / 20.0) + "с");
            WorldFx.sound(plugin, p.getLocation(), "minecraft:block.note_block.bass", 0.5f, 0.8f);
            return;
        }

        double cost = costOf(u, form, att);
        if (u.getStamina() < cost) {
            Msg.actionBar(p, "&cМало стамины &8(" + (int) u.getStamina() + "/" + (int) cost + ")");
            WorldFx.sound(plugin, p.getLocation(), "minecraft:block.fire.extinguish", 0.5f, 1.4f);
            return;
        }
        // Only gate the abilities that CREATE display entities. A water-fed cast hands over the orb's
        // existing drops rather than spawning new ones, so charging it against the budget would refuse
        // casts that cost nothing — the orb was already counted when it was collected.
        if (!form.requiresWater() && !budgetOk(p, 24)) return;

        // Orb payment for the water-less forms. Must come AFTER the cooldown/stamina gates above,
        // or spamming dash while it's on cooldown drains the orb and casts nothing.
        if (form.id().equals("dash")) {
            if (!WaterCollector.anySourceNear(p, 8)) {
                if (!u.hasOrb()) {
                    Msg.actionBar(p, "&cНет воды рядом");
                    return;
                }
                u.getOrb().consumePartial(Math.max(6, cfg().maxBlocks / 3 + 1));
                if (u.getOrb().isConsumed()) u.setOrb(null);
            }
        } else if (form.id().equals("relocate")) {
            boolean ground = WaterCollector.anySourceNear(p, 8);
            boolean orbReady = u.hasOrb() && u.getOrb().isReady();
            if (!ground && !orbReady) {
                Msg.actionBar(p, "&cНет воды рядом");
                return;
            }
            if (!ground && orbReady) {
                for (WaterBlock wb : u.getOrb().takeBlocks()) wb.remove();
                u.setOrb(null);
            }
        }

        if (form.requiresWater()) {
            if (!u.hasOrb()) {
                Msg.actionBar(p, "&7Сначала набери воду — &bПКМ");
                return;
            }
            WaterOrb orb = u.getOrb();
            if (!orb.isReady()) {
                Msg.actionBar(p, "&7Шар ещё формируется...");
                return;
            }
            FormContext ctx = new FormContext(plugin, p, orb.center(), orb.blockCount(), att, u.isAwakening());
            List<WaterBlock> blocks = orb.takeBlocks();
            u.setOrb(null);
            spend(u, cost, now);
            u.setFormUse(form.id(), now);
            try {
                form.cast(ctx, blocks);
            } catch (Exception e) {
                plugin.getLogger().warning("Form '" + form.id() + "' failed: " + e);
                for (WaterBlock b : blocks) b.remove();
            }
            WorldFx.splash(plugin, ctx.orbCenter, 24);
            WorldFx.sound(plugin, p.getLocation(), "minecraft:entity.player.splash", 0.8f, 1.0f);
            Msg.actionBar(p, form.name() + " &7— " + chargeLabel(ctx));
        } else {
            FormContext ctx = new FormContext(plugin, p, p.getEyeLocation(), cfg().maxBlocks / 2, att, u.isAwakening());
            // "Расступись" charges nothing if there was no water to part — including when a claim
            // vetoed the whole corridor, which used to bill the caster for a no-op.
            if (form.id().equals("part")) {
                PartWaterEffect eff = new PartWaterEffect(plugin, p, 60, 2, 5, 10, 300);
                if (!eff.opened()) {
                    eff.cleanup();
                    Msg.actionBar(p, "&7Здесь нечего раздвигать");
                    return;
                }
                plugin.animator().add(eff);
            } else {
                try {
                    form.cast(ctx, new ArrayList<>());
                } catch (Exception e) {
                    plugin.getLogger().warning("Form '" + form.id() + "' failed: " + e);
                }
            }
            spend(u, cost, now);
            u.setFormUse(form.id(), now);
            Msg.actionBar(p, form.name() + " &7— активирована!");
        }
        updateBar(u, p);
    }

    private String chargeLabel(FormContext ctx) {
        return switch (ctx.charge) {
            case LIGHT -> "&fлёгкая форма";
            case MEDIUM -> "выпущена!";
            case HEAVY -> "&b&lПОЛНАЯ МОЩЬ";
        };
    }

    private void spend(HydroUser u, double cost, long now) {
        u.setStamina(Math.max(0, u.getStamina() - cost));
        u.setLastUseTick(now);
    }

    private void handleNuke(Player p, HydroUser u) {
        AquaConfig cfg = cfg();
        if (!cfg.nukeEnabled) {
            Msg.actionBar(p, "&cУльта отключена на сервере");
            return;
        }
        if (!u.isAwakening()) {
            Msg.actionBar(p, "&cАква-Армагеддон — только в Авакенинге");
            return;
        }
        if (u.getArmedNuke() != null) {
            NukeEffect ne = u.getArmedNuke();
            if (ne.isReady()) {
                ne.drop(Targeting.lookPoint(p, cfg.nukeRadius));
                u.setArmedNuke(null);
                u.setStamina(0);
                u.setNukeReadyTick(now() + cfg.nukeCooldownSeconds * 20L);
                Msg.send(p, cfg.prefix, "&4&lЯДЕРНЫЙ УДАР!");
            } else {
                Msg.actionBar(p, "&7Заряд ещё собирается...");
            }
            return;
        }
        long left = u.getNukeReadyTick() - now();
        if (left > 0) {
            Msg.actionBar(p, "&cПерезарядка: " + (left / 20) + "с");
            return;
        }
        if (u.getStamina() < cfg.staminaMax - 0.5) {
            Msg.actionBar(p, "&cНужна ПОЛНАЯ стамина для Армагеддона");
            return;
        }
        if (!budgetOk(p, 300)) return;
        NukeEffect ne = new NukeEffect(plugin, p);
        plugin.animator().add(ne);
        u.setArmedNuke(ne);
        Msg.send(p, cfg.prefix, "&4Аква-Армагеддон заряжается... &7кликни ещё раз, чтобы сбросить.");
    }

    /** Admin/testing: force-enter awakening with full stamina. */
    public void debugAwaken(Player p) {
        if (!isPowered(p)) {
            Msg.send(p, cfg().prefix, "&7Сила выдана автоматически для теста Авакенинга.");
            grant(p, true);
        }
        HydroUser u = user(p);
        u.setStamina(cfg().staminaMax);
        u.setAwakeningCharge(cfg().awakeningMax);
        if (!u.isAwakening()) enterAwakening(u, p);
        updateBar(u, p);
    }

    public void disperse(Player p) {
        HydroUser u = online.get(p.getUniqueId());
        if (u != null && u.hasOrb()) {
            u.getOrb().disperse();
            u.setOrb(null);
            Msg.actionBar(p, "&7Шар воды развеян");
        }
    }

    // ---- per-second tick ----------------------------------------------------

    public void serverTick() {
        AquaConfig cfg = cfg();
        long now = now();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isPowered(p)) continue;
            HydroUser u = user(p);
            boolean stance = isStance(p);

            Attunement att = stance || u.hasOrb() ? attunementOf(p, u) : u.getAttunement();
            u.setStamina(clamp(u.getStamina() + cfg.staminaMax / Math.max(1, att.secondsToFull(cfg)),
                    0, cfg.staminaMax));

            if (cfg.awakeningEnabled) {
                boolean recentlyHit = now - u.getLastChargeTick() <= cfg.awakeningGraceTicks;
                if (!recentlyHit) {
                    u.setAwakeningCharge(Math.max(0, u.getAwakeningCharge() - cfg.awakeningDecayPerSec));
                }
                if (!u.isAwakening() && u.getAwakeningCharge() >= cfg.awakeningThreshold) {
                    enterAwakening(u, p);
                } else if (u.isAwakening() && u.getAwakeningCharge() <= 0) {
                    exitAwakening(u, p);
                }
                if (u.isAwakening()) awakenedAura(p);
            }

            if (stance) {
                updateBar(u, p);
                // One raytrace per tick, shared by the sidebar line and the ground ring.
                LivingEntity aimed = null;
                WaterForm sel = u.getSelected();
                if (sel != null && sel.needsEntityTarget()) aimed = Targeting.targetEntity(p, 30);
                updateHud(u, p, aimed);
                if (aimed != null) markTarget(p, aimed);
            } else {
                releaseHud(u, p);
            }
        }
    }

    private void awakenedAura(Player p) {
        Location at = p.getLocation();
        p.getWorld().spawnParticle(Particle.FALLING_WATER, at.clone().add(0, 1, 0), 6, 0.4, 0.8, 0.4, 0.0);
        WorldFx.shockwave(plugin, at, 0.9, 10);
        p.getWorld().spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.1, 0), 4, 0.5, 0.05, 0.5, 0.01);
    }

    private void markTarget(Player p, LivingEntity t) {
        Location base = t.getLocation();
        for (int i = 0; i < 12; i++) {
            double a = (2 * Math.PI * i) / 12;
            p.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 0.9, 0.1, Math.sin(a) * 0.9), 1,
                    0, 0, 0, 0, new Particle.DustOptions(WorldFx.foam(), 0.9f));
        }
    }

    private Attunement attunementOf(Player p, HydroUser u) {
        if (!cfg().environmentEnabled) return Attunement.NEUTRAL;
        long now = now();
        if (p.isInWater()) {
            u.setAttunement(Attunement.SUBMERGED, now);
            return Attunement.SUBMERGED;
        }
        if (now - u.getLastAttuneTick() < 40) return u.getAttunement();
        Attunement a = Attunement.of(p, cfg());
        Attunement before = u.getAttunement();
        u.setAttunement(a, now);
        if (before != a && isStance(p)) {
            Msg.actionBar(p, "&7Стихия: " + a.display() + " &8(&7стоимость ×"
                    + String.format("%.2f", a.costMult) + "&8, &7мощь ×"
                    + String.format("%.2f", a.powerMult) + "&8)");
        }
        return a;
    }

    public boolean isStance(Player p) {
        return isPowered(p) && plugin.items().isCatalyst(p.getInventory().getItemInOffHand());
    }

    public void onDamageDealt(Player p, double amount) {
        if (!cfg().awakeningEnabled || p == null || !isPowered(p)) return;
        HydroUser u = user(p);
        u.setAwakeningCharge(Math.min(cfg().awakeningMax,
                u.getAwakeningCharge() + amount * cfg().awakeningChargePerDamage));
        u.setLastChargeTick(now());
        if (!u.isAwakening() && u.getAwakeningCharge() >= cfg().awakeningThreshold) {
            enterAwakening(u, p);
        }
    }

    private void enterAwakening(HydroUser u, Player p) {
        u.setAwakening(true);
        p.sendTitle(Msg.color("&b&lА В А К Е Н И Н Г"), Msg.color("&3Сила воды пробудилась"), 8, 40, 12);
        WorldFx.spray(plugin, p.getLocation().add(0, 1, 0), 50);
        WorldFx.skin(plugin, p.getLocation().add(0, 1.2, 0), 2.5, 40);
        for (int r = 1; r <= 4; r++) WorldFx.shockwave(plugin, p.getLocation(), r * 1.6, 30);
        WorldFx.sound(plugin, p.getLocation(), "minecraft:entity.ender_dragon.growl", 1.0f, 1.2f);
        WorldFx.sound(plugin, p.getLocation(), "minecraft:block.beacon.power_select", 1.0f, 1.4f);
    }

    private void exitAwakening(HydroUser u, Player p) {
        u.setAwakening(false);
        clearArmedNuke(p);
        Msg.actionBar(p, "&7Авакенинг угас.");
        WorldFx.steam(plugin, p.getLocation().add(0, 1, 0), 20);
    }

    // ---- boss bar -----------------------------------------------------------

    private void updateBar(HydroUser u, Player p) {
        BossBar bar = u.getBar();
        if (bar == null) {
            bar = Bukkit.createBossBar(" ", BarColor.BLUE, BarStyle.SOLID);
            bar.addPlayer(p);
            u.setBar(bar);
        }
        AquaConfig cfg = cfg();
        double pct = clamp(u.getStamina() / cfg.staminaMax, 0, 1);
        bar.setProgress(pct);
        String sel = u.getSelected() != null ? u.getSelected().name() : "&7—";
        String orb = u.hasOrb()
                ? " &8| &bШар " + (int) Math.round(100.0 * u.getOrb().blockCount() / cfg.maxBlocks) + "%"
                : "";
        if (u.isAwakening()) {
            bar.setColor(BarColor.RED);
            bar.setTitle(Msg.color("&c⚡ АВАКЕНИНГ &8| " + sel + " &8| &fСтам " + (int) (pct * 100)
                    + "% &8| &cЗаряд " + (int) u.getAwakeningCharge() + orb));
        } else {
            bar.setColor(pct < 0.3 ? BarColor.YELLOW : BarColor.BLUE);
            int aw = (int) Math.round(u.getAwakeningCharge() / Math.max(1, cfg.awakeningThreshold) * 100);
            bar.setTitle(Msg.color(sel + " &8| &fСтамина " + (int) (pct * 100) + "% &8| &7Ав " + aw + "%" + orb));
        }
    }

    // ---- corner HUD ---------------------------------------------------------

    /**
     * Give the player their scoreboard back.
     *
     * <p>Taking one over means taking over <b>teams</b> too, which is where other plugins keep name
     * colours, prefixes and nametag visibility. This used to hand back the server's <i>main</i>
     * scoreboard rather than whatever the player actually had, so a rank or clan plugin's settings
     * for that player were simply gone until they reconnected.
     */
    private void releaseHud(HydroUser u, Player p) {
        if (u.getBar() != null) {
            u.getBar().removeAll();
            u.setBar(null);
        }
        if (u.getBoard() != null) {
            try {
                Scoreboard back = u.getPreviousBoard();
                if (back != null) p.setScoreboard(back);
                else p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            } catch (Exception ignored) {
            }
        }
        u.setBoard(null);
        u.setHud(null);
        u.setPreviousBoard(null);
        u.setHudSignature("");
    }

    private void updateHud(HydroUser u, Player p) {
        updateHud(u, p, null);
    }

    private void updateHud(HydroUser u, Player p, LivingEntity aimed) {
        AquaConfig cfg = cfg();
        double pct = clamp(u.getStamina() / cfg.staminaMax, 0, 1);
        FormGroup g = plugin.forms().group(u.getCurrentGroup());
        WaterForm sel = u.getSelected();
        long now = now();

        List<String> lines = new ArrayList<>(16);
        lines.add(g == null
                ? "&fГруппа: &7— &8(жми 1-" + plugin.forms().groupCount() + ")"
                : "&fГруппа &8[" + (u.getCurrentGroup() + 1) + "] " + g.color() + g.name());
        if (g != null) {
            for (int i = 0; i < g.size(); i++) {
                WaterForm f = g.get(i);
                boolean active = f == sel;
                long rem = f.cooldownTicks() - (now - u.getFormUse(f.id()));
                String tail = rem > 0 ? " &c" + (long) Math.ceil(rem / 20.0) + "с" : "";
                lines.add((active ? "&f▸&e" : "&8 ") + (i + 1) + " "
                        + (active ? f.name() : "&7" + ChatColor.stripColor(Msg.color(f.name()))) + tail);
            }
        }
        lines.add("&8&m-------------");
        lines.add("&fСтамина: &b" + (int) (pct * 100) + "%"
                + (sel != null ? " &8(-" + (int) costOf(u, sel) + ")" : ""));
        lines.add("&fШар: " + (u.hasOrb()
                ? "&b" + (int) Math.round(100.0 * u.getOrb().blockCount() / cfg.maxBlocks) + "%"
                        + (u.getOrb().isReady() ? " &a✓" : " &7…")
                : "&7пусто"));
        if (cfg.environmentEnabled) lines.add("&fСтихия: " + u.getAttunement().display());
        if (u.isAwakening()) {
            lines.add("&c⚡ Авакенинг " + (int) u.getAwakeningCharge());
        } else {
            int aw = (int) Math.round(u.getAwakeningCharge() / Math.max(1, cfg.awakeningThreshold) * 100);
            lines.add("&7Заряд: " + aw + "%");
        }
        if (sel != null && sel.needsEntityTarget()) {
            lines.add("&fЦель: " + (aimed == null ? "&c— нет" : "&a" + describe(aimed)));
        }

        // Rebuilding the sidebar means a remove packet and an add packet per line, every second, for
        // every bender. Skip it entirely when nothing would look different.
        String signature = String.join("|", lines);
        boolean fresh = u.getBoard() == null;
        if (!fresh && signature.equals(u.getHudSignature())) return;
        u.setHudSignature(signature);

        Scoreboard board = u.getBoard();
        if (board == null) {
            Scoreboard current = p.getScoreboard();
            Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            u.setPreviousBoard(current == main ? null : current);
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            u.setBoard(board);
        }
        Objective obj = u.getHud();
        if (obj == null) {
            obj = board.getObjective("aquahud");
            if (obj == null) {
                obj = board.registerNewObjective("aquahud", Criteria.DUMMY, Msg.color("&b&l⊙ Вода"));
            }
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            u.setHud(obj);
        }
        for (String e : board.getEntries()) board.resetScores(e);
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            obj.getScore(uniqueEntry(Msg.color(lines.get(i)), i)).setScore(score - i);
        }
        if (p.getScoreboard() != board) p.setScoreboard(board);
    }

    private String describe(LivingEntity t) {
        String name = t instanceof Player pl ? pl.getName()
                : t.getType().name().toLowerCase().replace('_', ' ');
        double max = 20;
        try {
            if (t.getAttribute(Attribute.MAX_HEALTH) != null) max = t.getAttribute(Attribute.MAX_HEALTH).getValue();
        } catch (Exception ignored) {
        }
        if (name.length() > 12) name = name.substring(0, 12);
        return name + " &7" + (int) Math.ceil(t.getHealth()) + "/" + (int) max + "❤";
    }

    /**
     * Make each sidebar line a distinct scoreboard entry.
     *
     * <p>Uniqueness comes from the per-line invisible token at the front. The tail is the delicate
     * part: cutting a coloured string at the 40-character entry limit can land in the middle of a
     * two-character colour code and leave a dangling section sign, which the client renders as
     * garbage. So the token is reserved first, the body is trimmed to what is left, and a trailing
     * lone {@code §} is dropped.
     */
    public static String uniqueEntry(String coloured, int index) {
        String token = "§" + Integer.toHexString(index & 0xF) + "§r";
        int room = 40 - token.length();
        String body = coloured.length() <= room ? coloured : coloured.substring(0, room);
        // Never end on a lone section sign; that renders as garbage.
        if (body.endsWith("§")) body = body.substring(0, body.length() - 1);
        return token + body;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
