package dev.bibo.aqua.form;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.effect.BarrierEffect;
import dev.bibo.aqua.effect.CloneEffect;
import dev.bibo.aqua.effect.HealEffect;
import dev.bibo.aqua.effect.MineEffect;
import dev.bibo.aqua.effect.NeedleRainEffect;
import dev.bibo.aqua.effect.ProjectileEffect;
import dev.bibo.aqua.effect.RelocateEffect;
import dev.bibo.aqua.effect.SlamEffect;
import dev.bibo.aqua.effect.SpringEffect;
import dev.bibo.aqua.effect.SurfEffect;
import dev.bibo.aqua.effect.TornadoEffect;
import dev.bibo.aqua.effect.VortexEffect;
import dev.bibo.aqua.effect.WallEffect;
import dev.bibo.aqua.effect.WaterDashEffect;
import dev.bibo.aqua.effect.WaterWalkEffect;
import dev.bibo.aqua.effect.WaveEffect;
import dev.bibo.aqua.effect.WhipEffect;
import dev.bibo.aqua.fx.ImpactFx;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.Targeting;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * All abilities, organised into groups.
 *
 * <p>Several abilities branch on {@link FormContext.Charge}: a half-full orb doesn't just do less,
 * it does something <i>else</i>. A light spear is a fast piercing needle; a full one is a slow lance
 * that detonates. That is what makes the orb a decision instead of a loading bar.
 */
public final class Forms {

    private final List<FormGroup> groups = new ArrayList<>();
    private final List<WaterForm> flat = new ArrayList<>();

    public Forms(AquaWaterPlugin plugin) {
        // ---- Group 1: focused / projectile ---------------------------------

        WaterForm bullet = new WaterForm("bullet", "&b&lВодяная Пика",
                "Сжатая пика воды. &fМало воды&7 — быстрая пробивающая игла, &fполный шар&7 — тяжёлое копьё со взрывом.",
                16, true, false,
                (c, b) -> {
                    boolean light = c.charge.isLight();
                    // trail length / girth: a light bolt is a long thin dart, a full one a fat lance.
                    c.add(new ProjectileEffect(c.plugin, c.player, b, c.player.getEyeLocation(), dir(c.player),
                            light ? 2.9 : 1.9,                       // light bolts fly much flatter/faster
                            light ? lp(c, 4.5, 6.5) : lp(c, 3.5, 8.0),
                            light ? 0.35 : lp(c, 0.45, 0.95),
                            light ? 7.5 : lp(c, 9, 24),
                            light ? 1.2 : lp(c, 1.8, 3.2),
                            light,                                   // light pierces, heavy detonates
                            70, light ? 1 : li(c, 2, 5), c.power, 0,
                            nearestNear(c, Targeting.lookPoint(c.player, 30), 5), 0.05, 2.6)
                            .signature(light ? ImpactFx.NEEDLE : ImpactFx.SPEAR));
                }).cooldown(1.5);

        WaterForm dragon = new WaterForm("dragon", "&b&lВодяной Дракон",
                "Поток воды взмывает дугой как из катапульты и обрушивается на цель (с самонаведением).",
                22, true, false,
                (c, b) -> {
                    Location aim = Targeting.lookPoint(c.player, 30);
                    LivingEntity tgt = nearestNear(c, aim, 8);
                    Vector look = dir(c.player);
                    Vector fh = new Vector(look.getX(), 0, look.getZ());
                    if (fh.lengthSquared() < 1e-6) fh = new Vector(1, 0, 0);
                    Vector launch = fh.normalize().multiply(0.8).add(new Vector(0, 0.95, 0)).normalize();
                    c.add(new ProjectileEffect(c.plugin, c.player, b, c.player.getEyeLocation().add(0, 0.3, 0), launch,
                            1.75, lp(c, 6, 13), lp(c, 0.8, 1.5), lp(c, 7, 16), lp(c, 2.5, 4.0),
                            true, 120, li(c, 1, 3), c.power, 0.06, tgt, 0.2, 2.8)
                            .signature(ImpactFx.METEOR));
                }).cooldown(4);

        WaterForm shotgun = new WaterForm("shotgun", "&b&lВодяной Дробовик",
                "Залп водяных болтов. &fМало воды&7 — 3 кучных и злых, &fполный шар&7 — широкий веер.",
                20, true, false,
                (c, b) -> {
                    boolean light = c.charge.isLight();
                    int k = Math.min(light ? 3 : li(c, 5, 9), b.size());
                    double spreadDeg = light ? 4.5 : 9.0;
                    double dmgMult = light ? 1.9 : 1.0;   // fewer bolts hit harder — same total, tighter
                    List<List<WaterBlock>> parts = split(b, k);
                    Vector base = dir(c.player);
                    Location start = c.player.getEyeLocation();
                    for (int i = 0; i < parts.size(); i++) {
                        double ang = (i - (parts.size() - 1) / 2.0) * Math.toRadians(spreadDeg);
                        Vector d = Geometry.rotateY(base, ang).add(new Vector(0, rnd(0.06), 0)).normalize();
                        c.add(new ProjectileEffect(c.plugin, c.player, parts.get(i), start, d, 1.6,
                                lp(c, 2.0, 3.4), lp(c, 0.3, 0.55), lp(c, 4, 9) * dmgMult, lp(c, 1.6, 2.4),
                                false, 35, 1, c.power, 0, null, 0, 3.2)
                                .signature(ImpactFx.SHOTGUN));
                    }
                }).cooldown(3);

        WaterForm needles = new WaterForm("needles", "&b&lДождь Игл",
                "Ливень водяных игл держит зону ~10 секунд. Каждая игла почти не жалит, "
                        + "но их много и они не кончаются.", 24, true, false,
                (c, b) -> {
                    Location ground = aimGround(c.player, 30);
                    c.add(new NeedleRainEffect(c.plugin, c.player, b, ground,
                            lp(c, 4.0, 7.5),     // area held
                            lp(c, 26, 34),       // starts high above it
                            200,                 // ~10 seconds
                            lp(c, 0.4, 1.0),     // per needle: a nick, nothing more
                            1.1,
                            5));                 // at most one nick per victim per quarter-second
                }).cooldown(9);

        WaterForm whip = new WaterForm("whip", "&b&lВодяной Хлыст",
                "Плеть воды. &fМало воды&7 — длинный и стремительный росчерк, &fполный шар&7 — тяжёлый удар с отбросом.",
                14, true, false,
                (c, b) -> {
                    boolean light = c.charge.isLight();
                    c.add(new WhipEffect(c.plugin, c.player, b,
                            light ? lp(c, 9, 13) : lp(c, 6, 11),
                            light ? lp(c, 5, 9) : lp(c, 7, 16),
                            light ? 0.5 : lp(c, 0.8, 1.8),
                            light ? 7 : 14,
                            Math.toRadians(light ? 185 : 150), false));
                }).cooldown(2);

        groups.add(new FormGroup("Точечные", "&b", bullet, dragon, shotgun, needles, whip));

        // ---- Group 2: area / ground ----------------------------------------

        WaterForm geyser = new WaterForm("geyser", "&3&lГейзер",
                "Столб воды бьёт из-под цели. &fМало воды&7 — узкая струя-катапульта, &fполный шар&7 — широкий разлом.",
                26, true, false,
                (c, b) -> {
                    Location g = aimGround(c.player, 26);
                    boolean light = c.charge.isLight();
                    c.add(new SlamEffect(c.plugin, c.player, b, g, 0,
                            light ? 2.0 : lp(c, 2.2, 4),
                            light ? 2.0 : lp(c, 3, 7),
                            light ? 2.6 : lp(c, 1.3, 2.2),     // a thin jet throws you much higher
                            0, true, 10,
                            light ? 11 : lp(c, 5, 9), c.power));
                }).cooldown(4);

        WaterForm whirlpool = new WaterForm("whirlpool", "&3&lВодоворот",
                "Вихрь на земле затягивает врагов и перемалывает их.", 30, true, false,
                (c, b) -> {
                    Location g = aimGround(c.player, 26);
                    c.add(new VortexEffect(c.plugin, c.player, b, VortexEffect.Mode.WHIRLPOOL,
                            g, null, lp(c, 3.5, 6), 130, lp(c, 0.25, 0.5), lp(c, 2, 5), lp(c, 2.2, 3.5), c.power));
                }).cooldown(8);

        WaterForm tornado = new WaterForm("tornado", "&3&lВодяной Смерч",
                "Воронка воды едет вперёд, затягивает и подбрасывает врагов.", 34, true, false,
                (c, b) -> c.add(new TornadoEffect(c.plugin, c.player, b, lp(c, 2.5, 4.5), lp(c, 5, 9),
                        lp(c, 14, 24), 0.7, lp(c, 4, 9), lp(c, 0.8, 1.4), li(c, 2, 4), c.power))).cooldown(9);

        WaterForm meteor = new WaterForm("meteor", "&3&lВодяной Метеор",
                "Шар взмывает и обрушивается в точку чудовищным взрывом.", 45, true, false,
                (c, b) -> {
                    Location g = aimGround(c.player, 34);
                    c.add(new SlamEffect(c.plugin, c.player, b, g, lp(c, 12, 20), lp(c, 5, 9),
                            lp(c, 12, 30), lp(c, 0.6, 1.0), li(c, 3, 6), false, 18, 0, c.power));
                }).cooldown(10);

        WaterForm deluge = new WaterForm("deluge", "&3&lВеликий Потоп",
                "Колоссальный поток сносит и ломает огромную зону.", 55, true, false,
                (c, b) -> c.add(new WaveEffect(c.plugin, c.player, b, dir(c.player), lp(c, 10, 18),
                        lp(c, 5, 9), lp(c, 22, 32), 1.0, lp(c, 9, 18), lp(c, 1.2, 2.2), true, li(c, 3, 6), c.power)))
                .cooldown(12);

        groups.add(new FormGroup("Зональные", "&3", geyser, whirlpool, tornado, meteor, deluge));

        // ---- Group 3: control / defence ------------------------------------

        WaterForm wall = new WaterForm("wall", "&9&lВодяная Стена",
                "Стена воды. &fМало воды&7 — мгновенный низкий отбойник, &fполный шар&7 — высокая стена, что держится и сносит.",
                22, true, false,
                (c, b) -> {
                    boolean light = c.charge.isLight();
                    c.add(new WallEffect(c.plugin, c.player, b,
                            light ? 5.0 : lp(c, 5, 9),
                            light ? 2.6 : lp(c, 3.5, 6),
                            lp(c, 5, 11), light ? 0.85 : lp(c, 0.3, 0.6),
                            li(c, 2, 4), light ? 8 : 46, c.power));
                }).cooldown(5);

        WaterForm clones = new WaterForm("clones", "&9&lВодяные Клоны",
                "Шар распадается на водяных двойников, что атакуют врагов.", 30, true, false,
                (c, b) -> c.add(new CloneEffect(c.plugin, c.player, b, Math.min(li(c, 2, 4), b.size()),
                        130, lp(c, 1, 2), 0.5))).cooldown(12);

        WaterForm prison = new WaterForm("prison", "&9&lВодяная Тюрьма",
                "Сфера воды запирает цель и топит её. (Нужна цель — моб или игрок.)", 24, true, false,
                (c, b) -> {
                    LivingEntity tgt = Targeting.targetEntity(c.player, 30);
                    Location center = tgt != null ? tgt.getLocation() : c.player.getLocation();
                    c.add(new VortexEffect(c.plugin, c.player, b, VortexEffect.Mode.PRISON,
                            center, tgt, lp(c, 1.3, 2.0), 100, 0, lp(c, 0.5, 1.0), 0, c.power));
                }).requireTarget().cooldown(8);

        // "Водяной Купол" lived here: a round shell of water around the caster that shoved enemies
        // away. Водный Барьер (Поддержка) is the same silhouette and the same idea, only it also
        // drinks incoming damage, puts out fire, visibly thins as it is spent and bursts when it
        // fails. Two abilities that look identical and do nearly the same thing is worse than one
        // good one, so the weaker duplicate is gone rather than kept for the count.

        WaterForm mines = new WaterForm("mines", "&9&lВодяные Мины",
                "Водяные заряды на земле детонируют гейзером у врага.", 36, true, false,
                (c, b) -> {
                    Location g = aimGround(c.player, 26);
                    c.add(new MineEffect(c.plugin, c.player, b, g, Math.min(li(c, 3, 6), b.size()),
                            lp(c, 2.5, 4.5), lp(c, 2.2, 3.2), 220, lp(c, 6, 16), 1, c.power));
                }).cooldown(9);

        groups.add(new FormGroup("Контроль", "&9", wall, clones, prison, mines));

        // ---- Group 4: movement & utility -----------------------------------

        // Built by UserManager (like the ultimate) because it must be able to report "there was
        // nothing to part" before the caster is charged for it.
        WaterForm part = new WaterForm("part", "&a&lРасступись",
                "Раздвигает воду в две стены вдоль взгляда (до 60 блоков), потом плавно заливает.",
                12, false, false, (c, b) -> { /* handled specially by UserManager */ }).cooldown(6);

        WaterForm relocate = new WaterForm("relocate", "&a&lПризыв Воды",
                "Вода летит туда, куда целишься (или в цель), и бьёт по площади.", 14, false, false,
                (c, b) -> c.add(new RelocateEffect(c.plugin, c.player, 14, 120, lp(c, 1, 2)))).cooldown(4);

        WaterForm walk = new WaterForm("walk", "&a&lВодная Поступь",
                "На время позволяет ходить по воде.", 16, false, false,
                (c, b) -> c.add(new WaterWalkEffect(c.plugin, c.player, 200, 1))).cooldown(12);

        WaterForm dash = new WaterForm("dash", "&a&lВодяной Рывок",
                "Вода вырывается из-под ног и подбрасывает тебя туда, куда смотришь.", 14, false, false,
                (c, b) -> c.add(new WaterDashEffect(c.plugin, c.player, 1.5, 0.55)));

        WaterForm surf = new WaterForm("surf", "&a&lПрибой",
                "Встань НА свою волну и мчись по земле, рулём — мышь. Sneak — спрыгнуть.",
                20, true, false,
                (c, b) -> c.add(new SurfEffect(c.plugin, c.player, b, lp(c, 4, 7), lp(c, 2.2, 3.4),
                        c.charge.isLight() ? 1.15 : 0.85, lp(c, 3, 8), lp(c, 0.9, 1.6), li(c, 70, 130))))
                .cooldown(8);

        groups.add(new FormGroup("Особые", "&a", part, relocate, walk, dash, surf));

        // ---- Group 5: support ----------------------------------------------

        WaterForm heal = new WaterForm("heal", "&d&lЖивая Вода",
                "Ленты воды лечат тебя и союзников, тушат огонь и смывают яд.", 28, true, false,
                (c, b) -> c.add(new HealEffect(c.plugin, c.player, b, lp(c, 3, 6), 160,
                        lp(c, 1.0, 2.5), c.awakened ? 6 : 0))).cooldown(15);

        WaterForm barrier = new WaterForm("barrier", "&d&lВодный Барьер",
                "Панцирь воды пьёт входящий урон, гасит огонь и расталкивает тех, кто подошёл вплотную.",
                24, true, false,
                (c, b) -> c.add(new BarrierEffect(c.plugin, c.player, b, lp(c, 1.4, 2.2), 200,
                        lp(c, 8, 26), c.charge.isHeavy() ? 0.75 : 0.55, lp(c, 0.35, 0.7)))).cooldown(14);

        WaterForm spring = new WaterForm("spring", "&d&lРодник",
                "Фонтан на земле: союзников лечит и даёт дышать под водой, врагов вязнет и выталкивает.",
                32, true, false,
                (c, b) -> c.add(new SpringEffect(c.plugin, c.player, b, aimGround(c.player, 20),
                        lp(c, 3, 5.5), 300, lp(c, 0.8, 2.0), lp(c, 0.25, 0.5)))).cooldown(18);

        groups.add(new FormGroup("Поддержка", "&d", heal, barrier, spring));

        // ---- Group 6: awakening ultimate -----------------------------------

        WaterForm nuke = new WaterForm("nuke", "&4&l☢ Аква-Армагеддон ☢",
                "Только в Авакенинге при полной стамине: вся вода в 60 блоках — и ядерный гриб-взрыв.",
                0, false, true, (c, b) -> { /* handled specially by UserManager */ });

        groups.add(new FormGroup("Авакенинг", "&4", nuke));

        for (FormGroup g : groups) flat.addAll(g.forms());
    }

    public List<FormGroup> groups() { return groups; }
    public int groupCount() { return groups.size(); }
    public FormGroup group(int i) { return (i < 0 || i >= groups.size()) ? null : groups.get(i); }
    public List<WaterForm> all() { return flat; }

    // ---- helpers ------------------------------------------------------------

    /**
     * Interpolate an ability stat between its weak and full-charge values.
     *
     * <p>These used to clamp {@code power} at 1.0, which silently deleted the entire environment and
     * awakening system: at a full orb — the state everyone plays in — Погружение ×1.30 and Авакенинг
     * ×1.25 both multiplied a number that had already been pinned, so they changed nothing about
     * damage, radius, count or duration. The clamp is gone; {@link FormContext} caps the multiplier
     * itself, so a peak state now extrapolates a little past the listed maximum instead of being
     * quietly discarded. Server-wide tuning lives in {@code damage.global-multiplier}.
     */
    private static double lp(FormContext c, double a, double b) {
        return a + (b - a) * c.power;
    }

    private static int li(FormContext c, int a, int b) {
        return (int) Math.round(a + (b - a) * c.power);
    }

    private static Vector dir(Player p) {
        return p.getEyeLocation().getDirection();
    }

    private static Location aimGround(Player p, double range) {
        Location aim = Targeting.lookPoint(p, range);
        return Targeting.groundBelow(aim.clone().add(0, 1, 0), 26);
    }

    /** Homing target pick — same "may water hit this?" rule as the damage code, so spears
     *  don't lock onto armour stands, spectators or corpses. */
    private static LivingEntity nearestNear(FormContext c, Location at, double r) {
        if (at.getWorld() == null) return null;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : at.getWorld().getNearbyEntities(at, r, r, r)) {
            if (!WorldFx.targetable(c.plugin.cfg(), e, c.player)) continue;
            double d = e.getLocation().distanceSquared(at);
            if (d < bd) {
                bd = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    private static double rnd(double mag) {
        return (ThreadLocalRandom.current().nextDouble() * 2 - 1) * mag;
    }

    private static List<List<WaterBlock>> split(List<WaterBlock> src, int parts) {
        return Geometry.partition(src, parts);
    }
}
