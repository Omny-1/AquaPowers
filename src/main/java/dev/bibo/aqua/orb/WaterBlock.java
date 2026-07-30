package dev.bibo.aqua.orb;

import dev.bibo.aqua.Keys;
import dev.bibo.aqua.fx.WaterStyle;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A single drop of water — a BlockDisplay with a per-drop size, tilt and material.
 *
 * <p>The important part is {@link #stretch}: a {@link Transformation}'s scale is a
 * <i>vector</i>, not a number, so a drop can be squashed across its motion and drawn out along it.
 * A spear of drops stretched along its flight direction reads as a jet of water; the same drops at
 * uniform scale read as a string of cubes. Every fast-moving ability uses it.
 */
public final class WaterBlock {

    private final BlockDisplay display;
    private final float base;          // this drop's own resting size (jittered, never changes)
    private final Quaternionf rest;    // fixed random tilt, so nothing is axis-aligned
    private final float roll;          // random spin around the stretch axis
    private final int moveDuration;

    private float sizeMult = 1f;
    private Vector lastDir;
    private float lastAlong = -1, lastAcross = -1;
    private boolean stretched = false;

    public WaterBlock(World world, Location at, WaterStyle style, float scale,
                      int teleportDuration, int brightness, float viewRange, boolean glow) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        this.base = style.pickScale(scale);
        this.rest = new Quaternionf().rotateXYZ(
                (float) (rnd.nextDouble() * Math.PI * 2),
                (float) (rnd.nextDouble() * Math.PI * 2),
                (float) (rnd.nextDouble() * Math.PI * 2));
        this.roll = (float) (rnd.nextDouble() * Math.PI * 2);

        // One tick of slack on top of the send interval. The orb sends a move once per interval to
        // keep entity traffic down; with the two exactly equal, a single dropped packet freezes a
        // drop for the whole window and then snaps it. A tick of overlap degrades that into slightly
        // stale but continuous motion instead.
        this.moveDuration = Math.max(1, teleportDuration) + 1;
        this.display = world.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(style.pickMaterial());
            d.setTeleportDuration(moveDuration);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(moveDuration);
            d.setViewRange(viewRange);
            d.setBrightness(brightnessAt(at, brightness));
            d.setPersistent(false);
            if (glow) {
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(64, 180, 255));
            }
            d.addScoreboardTag(Keys.DISPLAY_TAG);
        });
        apply(rest, base, base, base);
    }

    /**
     * Draw this drop out along {@code dir}: {@code along} times its size on the motion axis,
     * {@code across} times on the other two. Re-applied only when the direction or the shape has
     * actually moved — a transformation packet per drop per tick is far too expensive otherwise.
     */
    public void stretch(Vector dir, float along, float across) {
        if (!display.isValid() || dir.lengthSquared() < 1e-9) return;
        Vector d = dir.clone().normalize();
        boolean same = stretched
                && lastAlong == along && lastAcross == across
                && lastDir != null && lastDir.dot(d) > 0.995;
        if (same) return;

        Quaternionf q = new Quaternionf()
                .rotateTo(0f, 1f, 0f, (float) d.getX(), (float) d.getY(), (float) d.getZ())
                .rotateY(roll);
        float w = base * across * sizeMult;
        apply(q, w, base * along * sizeMult, w);
        lastDir = d;
        lastAlong = along;
        lastAcross = across;
        stretched = true;
    }

    /** Back to a rounded, randomly tilted droplet (used when an ability stops moving). */
    public void relax() {
        if (!display.isValid() || !stretched) return;
        float s = base * sizeMult;
        apply(rest, s, s, s);
        stretched = false;
        lastAlong = lastAcross = -1;
    }

    /** Scale this drop up or down (orb growth, evaporation, a burst blowing outward). */
    public void resize(float mult) {
        if (!display.isValid() || Math.abs(mult - sizeMult) < 0.02f) return;
        sizeMult = Math.max(0.05f, mult);
        if (stretched && lastDir != null) {
            float along = lastAlong, across = lastAcross;
            lastAlong = lastAcross = -1;      // force the re-apply below
            Vector d = lastDir;
            lastDir = null;
            stretch(d, along, across);
        } else {
            float s = base * sizeMult;
            apply(rest, s, s, s);
        }
    }

    /**
     * A display renders as {@code translation + leftRotation * scale * vertex} and a block model
     * spans [0,1] — so to spin and stretch a drop about its own centre, the translation must be the
     * rotated, scaled half-extent, negated. Get this wrong and every rotated drop orbits its own
     * corner instead of turning in place, which is invisible at uniform scale and glaring the moment
     * anything is stretched.
     *
     * <p>Pure function, no world access: see {@code GeometrySelfCheck} for the invariant it holds.
     */
    public static Transformation centeredTransform(Quaternionf left, float sx, float sy, float sz) {
        Vector3f t = new Vector3f(sx * 0.5f, sy * 0.5f, sz * 0.5f);
        left.transform(t);
        t.negate();
        return new Transformation(t, new Quaternionf(left), new Vector3f(sx, sy, sz), new Quaternionf());
    }

    private void apply(Quaternionf left, float sx, float sy, float sz) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(moveDuration);
        display.setTransformation(centeredTransform(left, sx, sy, sz));
    }

    /**
     * Light the drop from where it actually is, unless the owner pinned a fixed value.
     *
     * <p>Hard-coding 15/15 made the water permanently noon-lit: identical in a cave, at midnight and
     * in a storm, never picking up a sunset and never darkening underground. It read as a decal
     * pasted over the scene rather than something in it.
     */
    private static Display.Brightness brightnessAt(Location at, int pinned) {
        if (pinned >= 0) return new Display.Brightness(pinned, pinned);
        try {
            var b = at.getBlock();
            return new Display.Brightness(
                    Math.max(4, b.getLightFromBlocks()),   // never fully black; it is water, it glints
                    b.getLightFromSky());
        } catch (Throwable t) {
            return new Display.Brightness(15, 15);
        }
    }

    /** Re-sample the ambient light (the orb does this about once a second as the player moves). */
    public void refreshLight(Location at, int pinned) {
        if (pinned >= 0 || !display.isValid()) return;
        display.setBrightness(brightnessAt(at, pinned));
    }

    public void teleport(Location to) {
        if (display.isValid()) display.teleport(to);
    }

    public Location location() {
        return display.getLocation();
    }

    public void remove() {
        if (display.isValid()) display.remove();
    }
}
