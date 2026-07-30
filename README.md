# AquaPowers

Drink Holy Water once and you can bend water for good. You pull it out of rivers, lakes, rain — even
out of the air in a storm — hold it as an orb above your head, and spend that orb on one of 23
abilities.

Paper/Spigot 1.21.4, Java 21, no dependencies.

---

## The artifact

Two items matter.

**Holy Water** is a one-time drink. It awakens the power permanently and hands you the Totem.

**The Water Totem** is the thing you actually carry. It sits in your **off hand** and it is the
switch: with it there, an empty main hand means you are bending. It cannot be dropped and it does not
fall out of your inventory when you die.

Everything below only happens while the Totem is in your off hand **and your main hand is empty**.
Pick up a sword, a pickaxe, a stack of dirt — anything — and the plugin goes completely silent.
Mining, eating, opening chests, placing blocks all work normally. That is the escape hatch, and it is
worth learning first.

### Controls

| Input | What happens |
|---|---|
| First number key | Choose a **group** |
| Second number key | Choose an **ability** in that group |
| More number keys | Keep swapping the ability; the group stays put |
| `F` | Clear the selection, back to choosing a group |
| `Shift` + `F` | Throw the orb away |
| Right-click | Pull water in. Press again to top the orb up |
| `Shift` + right-click | Disperse the orb and return the water |
| Left-click | Cast |

Pressing a number whose hotbar slot holds an item just picks up the item, and that drops you out of
bending. Keep your low slots empty and every group stays one keypress away.

The sidebar on the right lists the group you are in, all of its abilities with their numbers and
cooldowns, your stamina, how full the orb is, and which attunement you are standing in.

### The orb

Right-click near water and it rises out of the world into a sphere over your head. It takes about
two seconds to form; you cannot cast until it has. A second right-click tops it up with whatever it
is still missing.

The water you take is real. Drain a stream and it drains. Disperse the orb without using it and the
water goes back where it was.

**How full the orb is decides what you cast, not how hard.** Six abilities read the charge and behave
differently:

| Charge | Water Pike | Water Shotgun | Water Whip | Water Wall | Geyser |
|---|---|---|---|---|---|
| **Light**, under 40% | fast dart that punches through | 3 tight shots, each one hurts | long, quick lash | instant low barrier | narrow jet, throws you high |
| **Full**, over 80% | heavy lance that detonates | wide spread | slow, heavy knockback | tall wall that holds, then sweeps | wide rupture |

So a half-charged Pike is not a weak Pike. It is a faster, longer, piercing one — and it is the right
answer against a runner, while the full charge is the right answer against a group.

---

## The abilities

Twenty-three, in six groups. Cost is stamina; cooldown is per-ability.

### 1. Point — single targets

| Ability | Cost | CD | What it does |
|---|---:|---:|---|
| **Water Pike** | 16 | 1.5 s | Your bread and butter. Light charge pierces and flies flat; full charge is a lance that explodes on impact. Lightly homing. |
| **Water Dragon** | 22 | 4 s | Arcs upward like a catapult shot and comes down on the target. Homes hard, so it works over walls and hills. |
| **Water Shotgun** | 20 | 3 s | Close range. Light charge gives 3 tight bolts that each hit properly; full charge is a wide fan for crowds. |
| **Needle Rain** | 24 | 9 s | Holds ground for ten seconds. Individual needles barely sting — the damage comes from someone standing in it. Does not break terrain. |
| **Water Whip** | 14 | 2 s | Sweeps a wide arc in front of you. Light charge is long and fast, full charge is slow with real knockback. |

### 2. Area — ground control

| Ability | Cost | CD | What it does |
|---|---:|---:|---|
| **Geyser** | 26 | 4 s | Erupts under your aim point. Stand over it yourself and it launches you — a narrow light-charge jet throws you highest. No fall protection. |
| **Whirlpool** | 30 | 8 s | A fixed vortex that drags everything toward the middle and grinds it. |
| **Water Tornado** | 34 | 9 s | A funnel that travels forward, sucking things in from a wide radius and flinging whatever reaches the eye straight up. |
| **Water Meteor** | 45 | 10 s | The orb climbs and comes down on one point. The heaviest single hit in the kit, and it leaves a crater. |
| **Great Deluge** | 55 | 12 s | A wall of water that rolls forward for up to 32 blocks, flattening terrain as it goes. The most expensive ability there is. |

### 3. Control — shaping a fight

| Ability | Cost | CD | What it does |
|---|---:|---:|---|
| **Water Wall** | 22 | 5 s | Rises in front of you as a barrier, holds, then surges forward. Light charge is instant and low; full charge is tall and lasts. |
| **Water Clones** | 30 | 12 s | Splits the orb into 2–4 humanoid doubles that hunt nearby enemies on their own for about six seconds. |
| **Water Prison** | 24 | 8 s | Seals one target in a sphere and drowns them. **Needs a target** — aim at a mob or player, and the sidebar tells you when you have one. Costs nothing if you miss. |
| **Water Mines** | 36 | 9 s | Plants 3–6 charges on the ground that erupt when something walks near. They wait about eleven seconds. |

### 4. Special — movement and utility

| Ability | Cost | CD | What it does |
|---|---:|---:|---|
| **Part the Waters** | 12 | 6 s | Splits real water into two walls along a dry corridor up to 60 blocks ahead, then lets it flood back. No orb needed. Three corridors at once, maximum. |
| **Water Summoning** | 14 | 4 s | Yanks nearby water to wherever you aim, or onto the target you are looking at, and hits the area on arrival. No orb needed. |
| **Water Walk** | 16 | 12 s | Freezes a platform under your feet for ten seconds so you can cross open water. It melts behind you. |
| **Water Dash** | 14 | none | The only ability with no cooldown. Water bursts under you and throws you where you are looking. Uses ground water if there is any, otherwise it sips your orb. |
| **Surf** | 20 | 8 s | Ride on top of your own wave across ground and open water. Steer with the mouse, sneak to get off. It refuses walls taller than about two blocks and sags off cliffs instead of flying. |

### 5. Support — the half that is not a weapon

| Ability | Cost | CD | What it does |
|---|---:|---:|---|
| **Living Water** | 28 | 15 s | Ribbons wrap you and your allies for eight seconds: heals over time, puts out fire, clears poison, wither, nausea and blindness. In Awakening it also grants absorption. |
| **Water Barrier** | 24 | 14 s | A shell that drinks a share of every hit until its reservoir runs out, then bursts and shoves whoever broke it. It visibly thins as it empties, so you can see what you have left. Fire cannot touch you while it holds. |
| **Spring** | 32 | 18 s | A fountain planted on the ground for fifteen seconds. Allies inside heal and breathe underwater; enemies get slowed and pushed out. This is how you hold a doorway. |

Who counts as an ally depends on the server: with PvP off, everyone nearby is healed; with PvP on,
only you and your tamed animals.

### 6. Awakening — the ultimate

**☢ Aqua Armageddon ☢** — every water block within 60 blocks lifts, rises into the sky and comes down
as a mushroom cloud. Needs Awakening **and** full stamina, five minute cooldown.

It takes two clicks. The first arms it and the water gathers over your head; the second drops it
where you aim. Arming costs nothing and is reversible — walk away and the water goes home.

It is loud on purpose. Everyone nearby hears it charging, the blast footprint gets drawn on the
ground while it falls, and anyone standing inside that circle gets a title telling them to run.

---

## Stamina, and where you are standing

Every ability costs stamina. It refills on its own, and how fast depends on your surroundings —
as does how much each cast costs and how hard it lands.

| Attunement | When | Cost | Power | Stamina refill |
|---|---|---:|---:|---|
| **Immersion** | you are in water | ×0.55 | ×1.30 | 30 s |
| **Downpour** | rain, open sky above you | ×0.70 | ×1.18 | ~22 s |
| **Flow** | plenty of water nearby | ×0.80 | ×1.12 | 30 s |
| **Balance** | ordinary ground | ×1.00 | ×1.00 | 15 s |
| **Drought** | desert, savanna | ×1.30 | ×0.90 | 10 s |
| **Inferno** | lava, fire, the Nether | ×1.70 | ×0.78 | 5 s |

Read those last two columns together, because they pull against each other. Standing in a lake makes
every cast cheap and strong, but your bar crawls back at half speed. A desert refills you in ten
seconds while charging you a third more for a weaker hit. Neither is straightforwardly better, and
the fight you want is usually the one on your ground.

Two more things the surroundings do:

**In rain or underwater you can fill an orb with nothing around you.** The water condenses out of the
air. This is the difference between being helpless in the middle of a field and not.

**In Drought and Inferno the orb boils away while you hold it.** You will watch it shrink, with
steam. Carrying a full orb around the Nether is not a plan.

---

## Awakening

A meter that fills from damage you actually land, and drains while you do nothing. Fill it and you
enter Awakening:

- abilities cost **half** the stamina
- they hit about **25% harder**
- the orb fills **twice as fast**
- Aqua Armageddon unlocks

The orb also changes shape — from a sphere into two crossed, counter-rotating rings. That is
deliberate. Anyone who can see you can see you are awakened, and so can you when you glance up.

---

## Playing it well

A few things that are not obvious from the ability list.

**Top up rather than re-collect.** A second right-click adds only what the orb is missing, and it is
much faster than dispersing and starting over.

**Not every ability wants a full orb.** Pike, Shotgun, Whip, Wall and Geyser all have a genuinely
different light-charge form. A quick sip and a fast Pike beats waiting two seconds for a lance you do
not need.

**Water Dash has no cooldown.** It is limited by stamina and by water, nothing else. It is the
fastest way to reposition, and it works off ground water when there is any, so it does not always
cost you the orb.

**Geyser under your own feet is a jump.** The narrow light-charge version throws you highest. There
is no fall damage protection, so bring Water Walk, a wall, or a plan.

**Prison and the water-less abilities are free when they fail.** Aim Prison at nothing and it costs
nothing. Same for Part the Waters with no water to part.

**Support is not a consolation prize.** Water Barrier is the only thing in the kit that answers being
hit rather than doing the hitting, and Spring is how you hold a corridor.

**Melee still works.** Put a sword in your hand and you are a normal player again, instantly.

---

## For server owners

Install: drop the jar in `plugins/`, restart, then `/aqua give <player>`.

```
/aqua give [player]          give Holy Water
/aqua catalyst [player]      give a Water Totem
/aqua grant|revoke <player>  grant or take the power directly
/aqua on|off                 toggle your own power
/aqua awaken <player>        Awakening + full stamina, for testing
/aqua forms                  list every ability
/aqua debug                  live effect and entity counts
/aqua reload                 reload the config
```

Aliases: `/voda`, `/water`, `/aquapowers`.

`aquapowers.use` (default: everyone) turns the whole plugin on or off for a player.
`aquapowers.admin` (default: op) covers the subcommands.

**Land protection works with no setup.** Before changing any block, AquaPowers announces it as a
vanilla explosion, so WorldGuard, GriefPrevention, Towny, Lands and CoreProtect gate it the way they
gate any other explosion. No dependency, no configuration. Set `griefing.respect-protection: false`
to turn that off.

**Terrain damage is on by default** and it is permanent. `griefing.break-blocks: false` stops it
entirely. Craters fill with water and stay that way, which on a survival server means the first
meteor near spawn creates a permanent lake.

**Balance in one knob:** `damage.global-multiplier`. For reference, a full-charge Water Pike does 24
damage to an unarmoured player, who has 20 health. Against diamond armour the same hit is about five.

**If the server feels laggy but TPS looks fine**, it is entity traffic, not CPU. Lower
`collect.max-blocks` first, then `display.view-range`, then the `limits` section. `/aqua debug` shows
what is actually live.

Stamina, Awakening charge and cooldowns survive relogs and restarts. Config updates only add new keys
and keep your values.

---

## Building

```
mvn -o clean package
```

Java 21; the jar lands in `target/`.

## License

MIT — see [LICENSE](LICENSE).
