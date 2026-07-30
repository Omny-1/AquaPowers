# AquaPowers

Water-bending for Paper/Spigot 1.21.4. Drink Holy Water, keep the Water Totem in your off hand, and
you can pull water out of the world into an orb over your head, then spend that orb on one of 23
abilities.

The water is not particles. Every droplet is a `BlockDisplay` entity with its own size, tint and
orientation, stretched along whatever direction it is travelling, so a spear reads as a jet instead
of a row of cubes. Water you collect actually leaves the river, and comes back if you disperse the
orb without using it.

Requires Java 21. No dependencies.

---

## Getting started

Drop the jar in `plugins/`, restart, then `/aqua give <player>` and drink the bottle.

Bending only happens when the Totem is in your off hand **and your main hand is empty**. Hold
anything at all and the plugin gets out of the way completely — mining, eating and chests work as
usual.

| Input | Effect |
|---|---|
| First number key | Pick a group |
| Second number key | Pick an ability in that group |
| More number keys | Keep changing the ability; the group stays |
| `F` | Clear the selection, back to picking a group |
| `Shift + F` | Disperse the orb |
| Right-click | Collect water (`Shift` + right-click disperses) |
| Left-click | Cast |

A number key whose slot holds an item just grabs the item. That is also how you leave bending mode,
so it always wins over the ability picker. Keep the low slots empty and every group stays reachable.

The sidebar shows the current group with all its abilities numbered, their cooldowns, your stamina,
how full the orb is, and which attunement you are standing in.

---

## The abilities

**Point** — Water Pike, Water Dragon, Water Shotgun, Needle Rain, Water Whip
**Area** — Geyser, Whirlpool, Water Tornado, Water Meteor, Great Deluge
**Control** — Water Wall, Water Clones, Water Prison, Water Mines
**Special** — Part the Waters, Water Summoning, Water Walk, Water Dash, Surf
**Support** — Living Water, Water Barrier, Spring
**Awakening** — Aqua Armageddon

A few are worth calling out because they do something other than damage:

**Surf** puts you on top of your own wave and carries you across ground and open water. You steer
with the mouse and sneak to get off. It refuses to climb walls taller than about two blocks and sags
rather than hanging in the air when you ride off a cliff.

**Part the Waters** opens a dry corridor up to 60 blocks long through real water and holds it open,
then lets it flood back.

**Needle Rain** holds an area for ten seconds. Individual needles barely sting; the pressure comes
from standing in it. It does not break terrain.

**Water Barrier** drinks incoming damage out of a reservoir and visibly thins as it empties, then
bursts and shoves whoever broke it.

**Spring** is a fountain planted on the ground: allies inside it heal and breathe underwater, enemies
get slowed and pushed out.

### The orb charge is a mode, not a volume

A half-full orb does not cast a weaker version of the same thing. Several abilities branch on it:

| Charge | Pike | Shotgun | Whip | Wall | Geyser |
|---|---|---|---|---|---|
| Light (<40%) | fast piercing dart | 3 tight, hard-hitting bolts | long, quick lash | instant low barrier | narrow jet that launches you |
| Full (>80%) | heavy lance that detonates | wide spread | slow, heavy knockback | tall wall that holds | wide rupture |

That exists because the earlier version scaled everything linearly off the fill percentage, which
made "top up to 100%, then fire" the only correct play. There was no decision in the resource.

---

## Where you stand changes what you can do

| Attunement | When | Cost | Power | Stamina refill |
|---|---|---|---|---|
| Immersion | you are in water | ×0.55 | ×1.30 | 30 s |
| Downpour | rain, open sky | ×0.70 | ×1.18 | ~22 s |
| Flow | lots of water nearby | ×0.80 | ×1.12 | 30 s |
| Balance | ordinary ground | ×1.00 | ×1.00 | 15 s |
| Drought | desert, savanna | ×1.30 | ×0.90 | 10 s, orb evaporates |
| Inferno | lava, fire, the Nether | ×1.70 | ×0.78 | 5 s, orb boils off |

Notice the stamina column runs the other way to the cost column. That is deliberate: near water your
casts are cheap and strong but the bar crawls, and in the desert the bar fills in ten seconds while
every cast is expensive and lands soft. Neither place is simply better.

In rain or underwater you can fill an orb with no body of water in reach, because the water comes out
of the air.

Attunement is decided from biome temperature and sky access rather than a list of biome names, so it
works with modded biomes. Turn it off with `environment.enabled`.

---

## Awakening and the ultimate

Awakening charge builds from damage you actually land and decays while you idle. While awakened,
abilities cost half as much stamina, hit 25% harder, and the orb fills twice as fast. The orb also
changes shape, from a sphere to two crossed counter-rotating rings, which is visible from across the
map — that is the point, so other players can see what state you are in.

**Aqua Armageddon** lifts every water block within 60 blocks and drops it as a mushroom cloud. Needs
Awakening and full stamina, five minute cooldown.

Arming it is free and reversible. If you never release it, the water goes back where it came from.
Everyone nearby hears it charging, the blast footprint is drawn on the ground while it falls, and
anyone standing in that footprint gets a title telling them to run. A 22-block one-shot with no tell
is a coin flip, not a fight.

---

## Running it on a real server

**Protection plugins work without a dependency.** Before touching a single block, AquaPowers fires a
vanilla `BlockExplodeEvent` carrying the exact list of blocks it wants to change. WorldGuard,
GriefPrevention, Towny, Lands and CoreProtect already listen for that: they either cancel it or strip
their own blocks out of the list, and the plugin edits whatever survives. Nothing to install, nothing
to configure, no version coupling. Turn it off with `griefing.respect-protection` if you want the old
behaviour.

Everything goes through that gate, including water collection, the ice under Water Walk, the Part the
Waters corridor and Water Summoning's delivery.

**Collection leaves some things alone.** Waterlogged stairs and slabs are structure, not a puddle, so
they are skipped. So is water holding kelp, seagrass or coral, because removing the water kills the
plant permanently and putting the water back afterwards does not bring it back. Only loaded chunks
are scanned — otherwise one cast of the ultimate could trigger world generation for dozens of chunks
inside a single tick.

**Area damage is reduced by cover** (`damage.through-cover`, 45% by default). Not blocked entirely,
because water goes round corners, but a sealed bunker five blocks from a meteor should be worth
something.

**Nothing is left standing in the Nether.** The crater gets dug, but no water is placed there.

**Bending is disabled while riding a vehicle.**

### Performance

The cost of this plugin is not CPU. It is display entities being broadcast to every client in
tracking range, and that scales with the *square* of the number of benders in one place: five benders
inside one radius is twenty-five packet streams, not five.

This matters because it is invisible to every tool a server owner has. TPS stays at 20, spark shows a
clean profile, timings show almost nothing, and players report rubber-banding. So there are hard caps
in `limits`, and hitting one refuses the cast with a message instead of quietly grinding the network.

```
/aqua debug
```

lists live effects by type, current droplet count against the cap, and per-player stamina,
attunement, effect count and orb size. If the server is loaded, tune `collect.max-blocks`,
`display.view-range` and `limits` before anything else.

### Commands and permissions

```
/aqua give [player]          give Holy Water
/aqua catalyst [player]      give a Water Totem
/aqua grant|revoke <player>  grant or take the power
/aqua on|off                 toggle your own power
/aqua awaken <player>        Awakening + full stamina, for testing
/aqua forms                  list every ability
/aqua debug                  live effects, entities, per-player state
/aqua reload                 reload config
```

Aliases: `/voda`, `/water`, `/aquapowers`.

`aquapowers.use` (default: everyone) gates the whole plugin for a player. `aquapowers.admin`
(default: op) covers the administrative subcommands.

Stamina, awakening charge and every cooldown survive a relog. Config updates only add missing keys
and keep your values, with a backup written next to the file anyway.

---

## How it is built

This section is for people who want to read the code. It is mostly about the things that were wrong
before and why the fix took the shape it did.

### Drops are entities, and that decides everything

`BlockDisplay` gives you a real block model you can move, rotate and scale per-tick without touching
the world. The catch is that every drop is an entity with a network ID, so a full orb is 120 entities
being tracked and re-sent to everyone nearby. That single fact drives most of the design: the
per-effect entity budget, the recycled droplets in Needle Rain, the default `view-range` of 1.5
instead of 4.0, and the decision to send one move per interpolation window rather than one per tick.

That last one is a real trade, not a free win. Sending every tick means a dropped packet is covered
by the next one and nobody notices. Sending once per interpolation window halves the traffic but a
dropped packet freezes a drop for the whole window. The compromise is an interpolation duration one
tick longer than the send interval, so a loss degrades into slightly stale but continuous motion.

### Making cubes look like water

A `Transformation`'s scale is a `Vector3f`, not a single number. That is the whole trick: squash a
drop across its motion and draw it out along it, and a line of drops reads as a jet. Add per-drop
size jitter and a weighted palette with a little white foam in it, and the lattice disappears.

There is a trap in the maths. A block model spans `[0,1]`, and a display renders as
`translation + leftRotation · scale · vertex`, so to spin a drop about its own centre the translation
has to be the *rotated* half-extent, negated. Get it wrong and every rotated drop orbits its own
corner. At uniform scale you cannot see it. The moment anything is stretched, the model falls apart.
`WaterBlock.centeredTransform` is a pure function for exactly this reason, and it has a test that
fails if you drop the rotation.

### Borrowing the world instead of taking it

Several abilities remove real blocks: the orb drains water, Part the Waters holds a corridor open,
Water Walk freezes a platform. Two of them used to drain and then simply forget. The ultimate was the
worst case, because arming it costs nothing and can be abandoned. You could arm it, walk away, let
Awakening lapse, and a few hundred blocks of water were gone from a 60-block radius for free,
repeatably, with nothing in any log.

Patching those two effects would have left the next one free to make the same mistake. Instead
`BaseEffect` owns the borrowed blocks and hands them back in `cleanup()` unless the effect explicitly
calls `keepChanges()`. A nuclear crater is supposed to stay; forgetting is no longer expressible.
Restores are batched across ticks and only write back where our own block is still standing, so a
player who built on a temporary ice bridge keeps their block.

The same class of problem produced the other three questions `BaseEffect` now asks its subclasses:
who owns me, is my caster still in this world, and how loud am I allowed to be. Before that, only the
orb checked for a world change, and nine other effects happily kept rendering across two worlds.

### Time

Every cooldown is in server ticks, from a counter the animator increments. `System.currentTimeMillis`
is not monotonic — an NTP correction backwards makes "time since last use" negative and jams every
cooldown — and it keeps running while the server is down. Persisted cooldowns store *ticks remaining*
rather than a timestamp for the same reason: a timestamp would hand everyone a fresh ultimate after
any overnight restart.

### The physics flag has two jobs

Filling a crater with `physics=true` queues thousands of fluid updates and stalls the tick, so the
bulk is placed without physics. But `physics` also means "tell the neighbours", and skipping it left
torches burning underwater, redstone still powered, and sponges that never absorbed. Worse, water
placed without physics in the Nether never schedules the fluid tick that would flash it to steam, so
the plugin could permanently flood a dimension where vanilla does not allow standing water.

The fix is to apply physics only to the topmost water of each column, which is where the torches and
redstone actually are, and to place no water in the Nether at all.

### Input, on the third attempt

Two schemes were tried and thrown away.

The first cancelled `PlayerItemHeldEvent` and re-selected by hand. That fights the client: it has
already moved the slot locally, the server bounces it back, and a fast scroll outruns any de-bounce.
The de-bounce also returned *without* cancelling, which let the held slot leak onto a real item in
the middle of a fight.

The second derived the ability from the slot number directly and never cancelled anything. It was
robust, but it was not the scheme this plugin wanted.

What both attempts shared was one broken idea: telling the scroll wheel from a number key by how far
the slot moved. That cannot work. Pressing 4 while standing on slot 3 is a move of exactly one, so it
was read as a scroll, and `Shift+4` advanced one group instead of jumping to group 4. Since the event
was cancelled the slot never moved, so the same key produced a different result on every press.

The current version reads the destination slot and nothing else, and the group/ability decision lives
in `form/Selection`, a pure function with three tests.

### Craters

A single radius with a smooth `1 - h/R` falloff gives you a bowl that looks turned on a lathe. The
crater is instead the union of eighteen overlapping sub-detonations with their own centres, radii and
depths, which produces lobes, ridges where two of them meet, an outline that wanders, and the odd
pillar of untouched ground left standing inside. It is seeded from the impact coordinates, so the
same spot always produces the same crater.

`CraterShape` is deliberately free of Bukkit types, because "this is not radially symmetric" is worth
asserting and asserting it needs no server. That test immediately caught the depth running 33% past
the configured budget, because two multiplicative terms were stacking.

### Tests

18 JUnit tests run as part of `mvn package`. They cover the places where a mistake is silent: sphere
scan coverage, the display transform, the crater's asymmetry, the selection state machine, sidebar
entry integrity, cooldown units and the sign conventions in the attunement table. Anything that needs
a live world is not tested and is verified in game instead.

A few of them were mutation-checked — break the code deliberately, confirm the test fails. That
exercise found one assertion that could never have failed, and one diagnosis that was simply wrong:
sidebar rows were supposed to be able to collapse into a single scoreboard entry, but the unique
token is a prefix and truncation happens from the end, so it always survived. Only the dangling
section-sign case was real.

---

## Known limits

**A full-charge Water Pike does 24 damage**, and more than that in a favourable attunement. An
unarmoured player has 20 health. That is deliberate for the server this was built for, and it is why
`damage.global-multiplier` exists. Diamond armour drops the same hit to around five, so the gap
between an armoured and an unarmoured target is enormous — four or five hits versus one.

**Every crater fills with source water, which makes it an infinite water source.** The whole resource
economy — draining rivers, orb evaporation, hunting for a lake — is defeated the first time someone
puts a meteor down next to spawn. This is a known consequence and it is not treated as a bug, because
permanent consequences are the point, but you should know it before you enable griefing on a survival
server.

**No anti-cheat integration.** Surf and Water Dash set player velocity every tick, which looks
exactly like a fly or speed hack, and the ultimate deals 220 damage in one hit. There is no bypass
permission or metadata tag. If you run Grim, Matrix or NCP, this is untested and players will
probably get kicked.

**Area damage ignores line of sight in one direction only.** Cover reduces damage but does not block
it, so you can still be hit through a wall for 45% of the value.

**Ability numbers are compiled in, not configured.** Damage, cost, cooldown and radius all live in
`form/Forms.java`, so tuning balance means a rebuild. Moving the numbers to config is the obvious
next step; moving the *behaviour* there is not, because that turns YAML into a programming language
nobody can debug.

---

## Building

```
mvn -o clean package
```

Java 21, output in `target/AquaPowers.jar`. Tests run as part of the build and a failure fails it.
`bash selfcheck.sh` runs just the tests.

## License

MIT, see [LICENSE](LICENSE).
