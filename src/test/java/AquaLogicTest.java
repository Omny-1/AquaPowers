import dev.bibo.aqua.env.Attunement;
import dev.bibo.aqua.form.Selection;
import dev.bibo.aqua.form.WaterForm;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.user.UserManager;
import dev.bibo.aqua.util.CraterShape;
import dev.bibo.aqua.util.Geometry;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything in AquaPowers that is pure logic — no server required.
 *
 * <p>This is deliberately narrow: it covers the places where a mistake is <i>silent</i>. Sphere
 * coverage (easy to leave gaps or duplicates), the display transform (a bug that is invisible at
 * uniform scale and destroys the model the moment anything is stretched), input decoding, the
 * slot↔ability inverse mapping, sidebar entry uniqueness and the sign conventions of the environment
 * table. Anything needing a live world is verified in game.
 *
 * <p>It runs as part of {@code mvn package}. The previous version lived in a class Surefire could
 * never discover, so a safety net that had already caught two real bugs was attached to nothing.
 */
class AquaLogicTest {

    // ---- water collection scan ---------------------------------------------

    @Test
    @DisplayName("scan covers the sphere exactly once, with nothing outside it")
    void scanCoversSphereExactlyOnce() {
        for (int R = 0; R <= 12; R++) {
            final int r = R;
            Set<Long> seen = new HashSet<>();
            int[] count = {0};
            Geometry.forEachInSphere(r, (dx, dy, dz) -> {
                assertTrue(dx * dx + dy * dy + dz * dz <= r * r,
                        "visited a point outside R=" + r);
                assertTrue(seen.add(key(dx, dy, dz)),
                        "duplicate visit at R=" + r + ": " + dx + "," + dy + "," + dz);
                count[0]++;
                return true;
            });
            int expected = 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= r * r) expected++;
                    }
                }
            }
            assertEquals(expected, count[0], "coverage at R=" + r);
        }
    }

    @Test
    @DisplayName("shells expand outward, so nearest water is found first")
    void scanIsNearestFirst() {
        int[] prevShell = {-1};
        Geometry.forEachInSphere(9, (dx, dy, dz) -> {
            int shell = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
            assertTrue(shell >= prevShell[0], "shells went backwards: " + shell + " after " + prevShell[0]);
            prevShell[0] = shell;
            return true;
        });
    }

    @Test
    @DisplayName("returning false stops the walk immediately — this is what caps the ultimate's scan")
    void scanStopsEarly() {
        int[] n = {0};
        Geometry.forEachInSphere(60, (dx, dy, dz) -> ++n[0] < 25);
        assertEquals(25, n[0], "walk did not stop on false");
    }

    // ---- shape generators ---------------------------------------------------

    @Test
    @DisplayName("every generator returns exactly one offset per drop, including zero")
    void shapesHaveRequestedSize() {
        for (int n : new int[]{0, 1, 2, 5, 17, 160}) {
            assertEquals(n, Geometry.sphere(n, 2.0).size(), "sphere(" + n + ")");
            assertEquals(n, Geometry.ball(n, 2.0).size(), "ball(" + n + ")");
            assertEquals(n, Geometry.column(n, 1.0, 3.0).size(), "column(" + n + ")");
            assertEquals(n, Geometry.gyroscope(n, 2.0).size(), "gyroscope(" + n + ")");
        }
    }

    @Test
    @DisplayName("the awakening silhouette is visibly flatter than a sphere")
    void gyroscopeIsFlattened() {
        double maxXZ = 0, maxY = 0;
        for (Vector v : Geometry.gyroscope(120, 2.0)) {
            maxXZ = Math.max(maxXZ, Math.hypot(v.getX(), v.getZ()));
            maxY = Math.max(maxY, Math.abs(v.getY()));
        }
        assertTrue(maxXZ > maxY * 1.3, "not flattened: xz=" + maxXZ + " y=" + maxY);
    }

    @Test
    @DisplayName("partition deals drops round-robin")
    void partitionDealsRoundRobin() {
        List<Integer> src = new ArrayList<>();
        for (int i = 0; i < 7; i++) src.add(i);
        List<List<Integer>> parts = Geometry.partition(src, 3);
        assertEquals(3, parts.size());
        assertEquals(List.of(0, 3, 6), parts.get(0));
        assertEquals(List.of(2, 5), parts.get(2));
        assertEquals(1, Geometry.partition(src, 0).size(), "parts<1 must collapse to one bucket");
    }

    // ---- display transform --------------------------------------------------

    @Test
    @DisplayName("a drop turns about its own centre at any rotation and any non-uniform scale")
    void transformKeepsDropCentred() {
        Quaternionf[] rotations = {
                new Quaternionf(),
                new Quaternionf().rotateY((float) Math.PI / 3),
                new Quaternionf().rotateTo(0, 1, 0, 1, 0, 0),
                new Quaternionf().rotateTo(0, 1, 0, 0.3f, -0.8f, 0.5f).rotateY(1.1f),
                new Quaternionf().rotateXYZ(0.7f, 2.1f, -1.3f),
        };
        float[][] scales = {{1, 1, 1}, {0.6f, 2.4f, 0.6f}, {0.2f, 3.5f, 0.9f}};

        for (Quaternionf q : rotations) {
            for (float[] s : scales) {
                Transformation t = WaterBlock.centeredTransform(new Quaternionf(q), s[0], s[1], s[2]);
                Vector3f centre = new Vector3f(0.5f * s[0], 0.5f * s[1], 0.5f * s[2]);
                t.getLeftRotation().transform(centre);
                centre.add(t.getTranslation());
                assertTrue(centre.length() < 1e-4f,
                        "drop centre drifted to " + centre + " at scale " + Arrays.toString(s)
                                + " — it would orbit its corner");
                assertEquals(s[0], t.getScale().x, 1e-6f);
                assertEquals(s[1], t.getScale().y, 1e-6f);
                assertEquals(s[2], t.getScale().z, 1e-6f);
            }
        }
        Transformation plain = WaterBlock.centeredTransform(new Quaternionf(), 0.6f, 0.6f, 0.6f);
        assertEquals(-0.3f, plain.getTranslation().x, 1e-6f, "uniform case regressed");
    }

    // ---- input decoding -----------------------------------------------------

    @Test
    @DisplayName("first press picks a group, the next picks an ability inside it")
    void twoPressSelectionWalksGroupThenAbility() {
        Selection.Press first = Selection.press(Selection.NO_GROUP, 2, 6, 0);
        assertEquals(Selection.Kind.GROUP, first.kind());
        assertEquals(1, first.group(), "pressing 2 must select the second group");

        Selection.Press second = Selection.press(first.group(), 3, 6, 5);
        assertEquals(Selection.Kind.ABILITY, second.kind());
        assertEquals(1, second.group(), "choosing an ability must not move the group");
        assertEquals(2, second.ability(), "pressing 3 must select the third ability");

        // With a group already chosen, a number is ALWAYS an ability — even one that also names a
        // group. This is the property the old slot-derived scheme could not express.
        Selection.Press again = Selection.press(1, 1, 6, 5);
        assertEquals(Selection.Kind.ABILITY, again.kind());
        assertEquals(0, again.ability());
    }

    @Test
    @DisplayName("out-of-range presses change nothing")
    void twoPressSelectionRejectsOutOfRange() {
        Selection.Press noGroup = Selection.press(Selection.NO_GROUP, 7, 6, 0);
        assertEquals(Selection.Kind.REJECTED, noGroup.kind(), "there is no group 7");
        assertTrue(!noGroup.ok());
        assertEquals(Selection.NO_GROUP, noGroup.group(), "a rejected press must not choose a group");

        // The "Control" group has four abilities after the duplicate dome was removed.
        assertEquals(Selection.Kind.REJECTED, Selection.press(2, 5, 6, 4).kind(),
                "there is no fifth ability in a four-ability group");
        assertEquals(Selection.Kind.ABILITY, Selection.press(2, 4, 6, 4).kind(),
                "but the fourth must still work");

        assertEquals(Selection.Kind.REJECTED, Selection.press(Selection.NO_GROUP, 0, 6, 0).kind(),
                "keys are 1-based");
    }

    @Test
    @DisplayName("every group number is reachable, and every ability within it")
    void twoPressSelectionCoversEverything() {
        int groups = 6;
        for (int gNum = 1; gNum <= groups; gNum++) {
            Selection.Press g = Selection.press(Selection.NO_GROUP, gNum, groups, 0);
            assertEquals(Selection.Kind.GROUP, g.kind(), "group " + gNum + " unreachable");
            assertEquals(gNum - 1, g.group());
            for (int aNum = 1; aNum <= 5; aNum++) {
                Selection.Press a = Selection.press(g.group(), aNum, groups, 5);
                assertEquals(Selection.Kind.ABILITY, a.kind(), "ability " + aNum + " unreachable");
                assertEquals(aNum - 1, a.ability());
            }
        }
    }

    // ---- crater shape -------------------------------------------------------

    @Test
    @DisplayName("the crater is not radially symmetric — it reads as many blasts, not one bowl")
    void craterIsNotRound() {
        CraterShape s = CraterShape.of(1000, -2000, 22, 11, 18, 0.55);

        // Sample a ring at a fixed distance from the centre. A smooth bowl gives the same depth all
        // the way round; a union of sub-blasts must vary a lot, which is the whole point of the change.
        List<Double> ring = new ArrayList<>();
        for (int i = 0; i < 72; i++) {
            double a = 2 * Math.PI * i / 72;
            int dx = (int) Math.round(Math.cos(a) * 12);
            int dz = (int) Math.round(Math.sin(a) * 12);
            ring.add(s.depthAt(dx, dz));
        }
        double min = ring.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = ring.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        assertTrue(max > 0, "the ring is entirely outside the crater — radius wiring is wrong");
        assertTrue(max - min > max * 0.35,
                "depth around the ring only varies by " + (max - min) + " of " + max
                        + " — that is still a bowl");
        assertTrue(ring.stream().anyMatch(d -> d == 0.0),
                "no gaps anywhere on the ring — the rim would read as a drawn circle");
    }

    @Test
    @DisplayName("the same impact point always carves the same crater")
    void craterIsDeterministic() {
        CraterShape a = CraterShape.of(77, -412, 20, 10, 18, 0.55);
        CraterShape b = CraterShape.of(77, -412, 20, 10, 18, 0.55);
        for (int dx = -20; dx <= 20; dx += 3) {
            for (int dz = -20; dz <= 20; dz += 3) {
                assertEquals(a.depthAt(dx, dz), b.depthAt(dx, dz), 1e-9,
                        "crater differs between runs at " + dx + "," + dz);
            }
        }
        // Different places must not produce the same crater.
        CraterShape elsewhere = CraterShape.of(5000, 5000, 20, 10, 18, 0.55);
        boolean anyDifference = false;
        for (int dx = -18; dx <= 18 && !anyDifference; dx += 2) {
            for (int dz = -18; dz <= 18; dz += 2) {
                if (Math.abs(a.depthAt(dx, dz) - elsewhere.depthAt(dx, dz)) > 1e-6) {
                    anyDifference = true;
                    break;
                }
            }
        }
        assertTrue(anyDifference, "every crater on the map would look identical");
    }

    @Test
    @DisplayName("crater depth stays within its budget and the hole is neither empty nor a full square")
    void craterIsBounded() {
        int maxDepth = 11;
        CraterShape s = CraterShape.of(-333, 888, 22, maxDepth, 18, 0.55);
        int carved = 0, total = 0;
        for (int dx = -s.reach(); dx <= s.reach(); dx++) {
            for (int dz = -s.reach(); dz <= s.reach(); dz++) {
                double d = s.depthAt(dx, dz);
                total++;
                assertTrue(d >= 0, "negative depth");
                assertTrue(d <= maxDepth, "depth " + d + " blew past the budget " + maxDepth);
                if (d > 0) carved++;
            }
        }
        assertTrue(carved > total * 0.10, "crater carved almost nothing: " + carved + "/" + total);
        assertTrue(carved < total * 0.85, "crater filled its whole bounding square: " + carved + "/" + total);
    }

    @Test
    @DisplayName("blast-count 0 falls back to a single smooth bowl")
    void craterDegradesToOneBowl() {
        CraterShape s = CraterShape.of(10, 10, 20, 10, 0, 0.0);
        // With one centred sub-blast and no stump rolls, opposite sides must match.
        assertEquals(s.depthAt(8, 0), s.depthAt(-8, 0), 3.0,
                "a single bowl should be roughly symmetric");
        assertTrue(s.depthAt(0, 0) > s.depthAt(11, 0), "should still be deepest in the middle");
    }

    // ---- HUD entries --------------------------------------------------------

    @Test
    @DisplayName("a truncated sidebar line never ends on a dangling section sign")
    void sidebarEntryNeverEndsMidColourCode() {
        // Build a line where the cut lands so the LAST kept character is the '§' of a colour code —
        // the case that renders as garbage. The token takes 4 characters, leaving 36 for the body,
        // so the section sign has to sit at index 35.
        String body = "x".repeat(35) + "§a" + "tail";
        String entry = UserManager.uniqueEntry(body, 3);
        assertTrue(entry.length() <= 40, "entry too long: " + entry.length());
        assertTrue(!entry.endsWith("§"), "entry ends on a dangling section sign: " + entry);

        // And the length bound holds for every row of a full sidebar, colour codes included.
        String longName = "§4§l☢ Aqua Armageddon ☢ an extremely long ability name";
        for (int i = 0; i < 14; i++) {
            String e = UserManager.uniqueEntry(longName, i);
            assertTrue(e.length() <= 40, "entry too long at line " + i);
            assertTrue(!e.endsWith("§"), "dangling section sign at line " + i);
        }
    }

    @Test
    @DisplayName("rows with identical text still get distinct entries")
    void sidebarRowsAreDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 14; i++) {
            assertTrue(seen.add(UserManager.uniqueEntry("same text on every row", i)),
                    "two sidebar rows collapsed to the same scoreboard entry at line " + i);
        }
        assertNotEquals(UserManager.uniqueEntry("x", 0), UserManager.uniqueEntry("x", 1));
    }

    // ---- cooldown units -----------------------------------------------------

    @Test
    @DisplayName("cooldowns are stored in ticks, not milliseconds")
    void cooldownsAreInTicks() {
        WaterForm f = new WaterForm("t", "t", "t", 1, false, false, (c, b) -> { }).cooldown(1.5);
        assertEquals(30, f.cooldownTicks(), "1.5s must be 30 ticks");
        WaterForm none = new WaterForm("n", "n", "n", 1, false, false, (c, b) -> { });
        assertEquals(0, none.cooldownTicks());
    }

    // ---- environment table --------------------------------------------------

    @Test
    @DisplayName("wet places are generous, hot places grudging")
    void environmentTableIsCoherent() {
        assertTrue(Attunement.SUBMERGED.costMult < Attunement.NEUTRAL.costMult, "water should be cheap");
        assertTrue(Attunement.SCORCHED.costMult > Attunement.DRY.costMult, "lava should cost most");
        assertTrue(Attunement.SUBMERGED.powerMult > Attunement.SCORCHED.powerMult, "water hits hardest");
        assertTrue(Attunement.SCORCHED.evaporatePerSec > Attunement.DRY.evaporatePerSec, "lava boils faster");
        assertEquals(0.0, Attunement.NEUTRAL.evaporatePerSec, "no evaporation in temperate weather");
        assertEquals(0.0, Attunement.RAIN.evaporatePerSec, "no evaporation in the rain");
        assertTrue(Attunement.RAIN.freeWater() && Attunement.SUBMERGED.freeWater(), "sky collection");
        assertTrue(!Attunement.DRY.freeWater() && !Attunement.NEUTRAL.freeWater(), "no free water when dry");
        for (Attunement a : Attunement.values()) {
            assertTrue(a.costMult > 0 && a.powerMult > 0, a + " has a non-positive multiplier");
            assertTrue(a.label != null && !a.label.isBlank(), a + " has no label");
        }
    }

    private static long key(int x, int y, int z) {
        return ((x + 64L) << 32) | ((y + 64L) << 16) | (z + 64L);
    }
}
