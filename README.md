# Kakugane

A Minecraft 1.21.1 NeoForge mod inspired by the anime *Buso Renkin*, by Tygabeast. Requires [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) (9.5 or later for 1.21.1).

A Kakugane is a hexagonal alchemical plate. Activate it ("Buso Renkin!") and it transforms into a weapon or armor shaped by how *you* fight. Hunt Homunculi with it, reach milestones, and choose to deepen your form or re-temper it to match how you fight now.

## Playing

- **Activate / revert:** press **R** (rebindable: "Buso Renkin") while holding a Kakugane, or right-click a dormant one.
- **Form ability:** **V** (rebindable) uses a worn form's key ability, such as Aerial Operator's night vision or Alice's scatter. Hold **Z** to zoom while wearing Aerial Operator.
- **Bonding:** a Kakugane bonds to the first player who activates it and refuses anyone else. Sneak + right-click a dormant Kakugane to release your bond. The bond also breaks when its wielder dies.
- **Taking form:** an unformed Kakugane waits until it "knows you". The mod records your combat choices (a bow kill counts fully when you also had a sword on your hotbar, only a little when the bow was all you had) and fades old activity so recent play matters most. Check with `/kakugane affinity`.
- **One at a time:** activating a second Kakugane reverts the first.
- **Staying active:** an active form stays active anywhere in your inventory, so you can switch to embryos or other items and move it around freely. It reverts when you press R again, or when it leaves your inventory (after a few seconds; a minute in creative, where inventory moves are only reported when you put the item down).
- **Legacy:** a Kakugane passed to a new wielder keeps its form but drops back to tier 0. The tooltip shows who shaped it and its last few wielders.

### Forms in this release

| Form | Kind | Earned by | Ability |
| --- | --- | --- | --- |
| Lance | Held | Sprint hits, critical hits, trident kills | Hold right-click to charge, release to dash through enemies |
| Bow | Held | Ranged kills when you also had a melee weapon | Draw and fire conjured arrows, no ammo needed |
| Breaker | Held | Axe kills when you had other weapons | Heavy hammer; right-click on the ground to slam |
| Silver Skin | Worn: all armor slots | Blocking with a shield, hitting back soon after being hurt | Full suit; each hit taken hardens it |
| Aerial Operator | Worn: Curios head | Kills with potions, tipped arrows or effect weapons; fighting underwater | Gas mask: endless breath, poison immunity, zoom and night vision. Higher tiers heal allies nearby (II) and choke enemies (III, IV) |
| Alice in Wonderland | Worn: Curios back | Kills while gliding with an elytra or attacking from well above | Wings of metal flakes: true flight (double-tap jump) on a reserve that refills on the ground. Climbing drains it fastest, hits in the air knock some off, and running dry turns flight into a glide. Taking the wings off and on again doesn't refill it; only standing on the ground does. Tier IV scatters the wings into a blinding cloud |
| Gauntlets | Worn: Curios hands | Bare-handed hits, and quick chains of hits with a weapon | Harder fists, and hits in a quick chain hit harder, ending in a finisher (with a burst from Tier III) |
| Greaves | Worn: armor legs (not over leggings with Curse of Binding) | Hits on the way up from a jump | Jump again in mid-air to dash where you look and kick what you hit (two dashes from Tier III); Tier IV adds a diving stomp |
| Mantle | Worn: Curios back | Hits and kills on enemies that weren't targeting you | Stand still to fade from view; your first hit from stealth deals extra damage. Sneaking keeps you hidden from Tier III |
| Blade Belt | Worn: Curios belt | Kills while surrounded by three or more enemies | Four blade-arms on thigh cuffs follow up your melee hits and strike on their own in a fight (config). More arms join from Tier II; Tier IV parries |

Worn forms leave your hands free for your own weapons. Whatever was in the slot they take (armor or another curio) is stored inside the Kakugane and given back when the form reverts.

Each Kakugane's serial number gives it one of 8 palettes and one of 14 traits (held forms: offensive and utility traits; worn forms: defensive and utility).

### Milestones

Each Kakugane has three milestones, one per deed, done with it active and in any order:
- Destroy Homunculus embryos (right-click an embryo while your Kakugane is active; 10 by default)
- Defeat a human-type Homunculus (a lab's Homunculus Leader counts)
- Defeat an Elite Homunculus *(arrives in a later wave)*

When one is reached, click **[Deepen]** (next tier: stronger form and trait) or **[Re-temper]** (reshape to your current affinity, tier unchanged) in chat.

### The heart Kakugane

Once ever per player: if you would die while carrying an unbonded Kakugane, it replaces your heart and saves you. It can't be dropped or stolen. Call it with **R** on an empty hand. On a true death it leaves your body as an unbonded Kakugane that keeps its form.

### Homunculi

- **Hound:** pack hunter that leaps and pins. Hunts players and villagers. Drops embryos.
- **Ram:** lowers its head, winds up and charges, knocking you back and leaving you slowed and weakened. Its front plating takes 60% less from hits within 60 degrees of its face, unless you hit it with an active Breaker. Flank it.
- **Crawler:** a big spider-type that keeps its distance and spits webs that slow you (they never place cobweb blocks).
- **Homunculus Leader:** looks human until it has a target or gets hurt, then shifts into a hybrid of its lab's beast (Hound, Ram or Crawler) with a boss bar. It regenerates unless it took alchemical damage in the last five seconds, calls nearby Homunculi to help, and summons two of its beasts once at half health. Each lab stage makes it tougher.
- Homunculi take 75% less damage from anything that isn't alchemical (Kakugane forms, conjured arrows, or weapons in the `kakugane:alchemical_weapons` item tag).
- Ambient spawning follows the rarity preset in the config. **Rare** (default) makes ambient Hounds almost absent; labs are where you're sure to find Homunculi.

### Hidden labs

Look for a small ruin of mossy cobblestone and spruce logs with a trapdoor hatch in the middle. A ladder leads about 25 to 30 blocks down to a lab of 7 to 9 rooms joined by corridors: a hub, vat halls full of Embryo Vats, cell blocks, storage rooms, barracks, and the Leader's chamber, the room farthest from the ladder. Every lab is laid out differently. Storage and cells hold loot, sometimes a Kakugane.

- Labs generate on dry land in most overworld biomes (not oceans, rivers or caves). How many candidate sites become labs depends on the rarity preset: half on Rare, three quarters on Standard, all on Common (or `customLabChance` on Custom). Labs exist at every preset.
- **Embryo Vats** drop embryos when broken.
- **Labs grow.** An uncleared lab advances a stage every few in-game days (3 by default): *Dormant*, then *Active* (broken vats regrow and guards return), *Spreading* (night outbreaks) and *Overrun* (bigger outbreaks).
- **Once found, a lab keeps growing.** By default a lab's clock starts the first time a player comes within 48 blocks of it ("Something stirs below your feet"), and then runs whether or not anyone is there. Come back and it catches up in one go: it tells you it grew while you were away, regrows its vats and refills its guards. At night, a Spreading or Overrun lab with nobody at it sends a pack after the nearest player within 192 blocks, so ignoring one has a cost. The `growth` setting picks *DISCOVERY* (default), *PRESENCE* (only time spent at the lab counts, as in 0.2.0) or *ALWAYS* (from when the lab is first loaded, with an extra stage's wait before the first stage).
- Killing the Leader stops the growth. Break every vat after that and the lab is **cleared** for good.
- `/kakugane lab` while inside a lab shows its stage, vats left and the Leader's state.

## Commands

| Command | Who | Does |
| --- | --- | --- |
| `/kakugane affinity` | Everyone | Shows how your fighting style reads |
| `/kakugane choose deepen\|retemper` | Everyone | Answers a waiting milestone (the chat buttons run this) |
| `/kakugane give <players> [form] [tier]` | Ops | A new Kakugane, optionally already formed |
| `/kakugane affinity add <players> <form> <amount>` | Ops | Adds affinity (testing) |
| `/kakugane deed <players> human\|embryo\|elite` | Ops | Credits a deed to the active Kakugane (testing) |
| `/kakugane heart reset <players>` | Ops | Returns the heart as an item and allows a new one |
| `/kakugane lab` | Everyone | Inside a lab: its stage, day, vats left, Leader alive or dead |
| `/kakugane lab stage <0-3>` | Ops | Sets the lab's stage (testing) |
| `/kakugane lab advance` | Ops | Runs one day of lab growth now (testing) |

## Config (`config/kakugane-common.toml`)

Imprint threshold, no-choice weight, affinity half-life, serial cap, embryos per milestone, heart on/off, Homunculus damage reduction, rarity preset (RARE / STANDARD / COMMON / CUSTOM), custom Hound weight, Homunculus density cap, custom lab chance, days per lab stage, lab outbreaks on/off, lab growth mode, discovery range, outbreak range, Alice's flight mode (true flight, glide only, off), wing reserve and refill time, dimensions where the wings can't fly, Blade Belt strikes on their own on/off, debug (also logs where labs generate and why sites were turned down, and lab discoveries and outbreaks).

## Performance notes

- Nothing runs per tick for players without an active form beyond two field reads.
- Active forms do a consistency check and passive traits once a second; the Lance dash scans a small box only during its 10-tick dash.
- Affinity, deeds and damage rules run only on hit and death events. Affinity fading is calculated from a timestamp when needed.
- Spawn weights are read once at world load; the Homunculus density check runs only for Hound spawn attempts.
- Form switching is a model override and an item component change; the Hound draws one extra emissive layer.
- Labs are built once, when their chunks generate. Afterwards the lab tracker runs every 10 seconds: one structure lookup per overworld player, a look at structure references in the already-loaded chunks around them (to discover labs), and a little arithmetic per known lab. A lab growing while unloaded only updates its numbers; vats and guards are placed when it is next loaded, at most once per in-game day. It never loads a chunk itself.
- Worn forms swap their slot only on activate and revert. Per tick they do field checks; searches for nearby entities happen only on hits or on a timer (every 5 ticks for the Blade Belt in a fight, 2 seconds for Aerial Operator's air, 4 ticks for Mantle). Alice's reserve is arithmetic, synced to the HUD at most 4 times a second while it changes. All the worn pieces are drawn as model layers, not entities.
- The Leader's regeneration and alerts are throttled (every 10 and 40 ticks). Ram and Crawler use vanilla-style goals that only run with a target.
- The Lance charge indicator is client-side particles, spawned every other tick only while someone in view is charging.

## Building

Requires Java 21. `gradle build` produces the jar in `build/libs`. `gradle runGameTestServer` runs the automated server-side tests in `src/gametest`. `python3 tools/gen.py` regenerates textures and models (Pillow and NumPy; `tools/gen_wave2.py` holds the lab mobs and vat, `tools/gen_wave3.py` the worn forms) and writes previews to `build/previews`. `python3 tools/pose_preview.py` solves and previews held-item poses. `gradle runShotsClient` (needs a display, or `xvfb-run`) starts a client that poses each form and saves screenshots to `run/screenshots`; it expects a world in `run/saves/shots`. `-Pshots=labs` instead tours the nearest lab (use a normal, not superflat, world), lines up every Homunculus, and runs the lab growth (including time away) and clearing commands. `-Pshots=worn` photographs each worn form from front and back, Alice in flight and a Blade Belt strike.

## License

MIT (see `LICENSE`), covering the code and the original textures and models in this repository. *Buso Renkin* and its names belong to their owners; this is an unofficial fan mod.

## 0.3.0 changes

- **Six new worn forms:** Aerial Operator, Alice in Wonderland, Gauntlets, Greaves, Mantle and the Blade Belt, each with its own affinity signal, tiers and look. Curios API is now required.
- **Lab growth after discovery** (the new default): a lab starts growing once a player finds it and keeps growing while they're away, catching up when they return, and its night outbreaks hunt nearby players. New `growth`, `discoveryRange` and `outbreakRange` settings.
- The Crawler-hybrid Leader's spider legs are bigger and spread out to the sides, so they read from the front.
- Silver Skin now shares the worn-form code that stores and returns your armor.
- Cleanup: removed an unused import (`MaskRenderer`). No dead code found otherwise.

## 0.2.0 changes

- **Hidden labs:** a new structure, a ruined shed over a ladder shaft into a 7 to 9 room underground complex with a randomized layout, Embryo Vats, loot and guards.
- **New Homunculi:** Ram, Crawler and the Homunculus Leader (a boss whose hybrid form matches its lab's beast). Spawn eggs for each.
- **Lab growth:** stages, vat regrowth, guard restocking, night outbreaks, and clearing. New `/kakugane lab` commands and `[labs]` config section.
- **Lance charge indicator:** particles in your Kakugane's palette run along the lance as it charges, and at full charge the tip sparks once with a chime. Everyone nearby sees it.
- The human-type Homunculus milestone can now be earned (by killing a lab's Leader).
- Cleanup: removed an unused method (`SerialRegistry.claimedCount`) and an unused import in the test harness.

## 0.1.2 changes

- Active forms no longer revert when you switch hotbar slots or move them in your inventory, so embryos can be crushed with any form, and Silver Skin can live off the hotbar.
- Bow form is held the right way round (its textures now match vanilla's bow layout).
- New Lance charge pose: couched at the hip and levelled forward in third person, drawn back and trembling at full charge in first person, instead of the trident overhead pose.
- `tools/pose_preview.py` previews and solves held-item poses with the game's own transforms.

## 0.1.1 changes

- Licensed under MIT; `LICENSE` is now included in the jar and the source.

## 0.1.0 changes

- First release: core Kakugane system (bonding, affinity imprinting, serials, palettes, traits, milestones with Deepen / Re-temper, legacy, heart Kakugane), Lance, Bow, Breaker and Silver Skin forms, the Hound Homunculus and Homunculus embryos, commands and config.
- Textures are generated placeholders, to be replaced once designs are approved.
