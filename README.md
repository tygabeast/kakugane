# Kakugane

A Minecraft 1.21.1 NeoForge mod inspired by the anime *Buso Renkin*, by Tygabeast.

A Kakugane is a hexagonal alchemical plate. Activate it ("Buso Renkin!") and it transforms into a weapon or armor shaped by how *you* fight. Hunt Homunculi with it, reach milestones, and choose to deepen your form or re-temper it to match how you fight now.

## Playing

- **Activate / revert:** press **R** (rebindable: "Buso Renkin") while holding a Kakugane, or right-click a dormant one.
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
| Silver Skin | Worn | Blocking with a shield, hitting back soon after being hurt | Full suit over all armor slots; each hit taken hardens it. Your old armor is stored and returned |

Each Kakugane's serial number gives it one of 8 palettes and one of 14 traits (held forms: offensive and utility traits; worn forms: defensive and utility).

### Milestones

Each Kakugane has three milestones, one per deed, done with it active and in any order:
- Destroy Homunculus embryos (right-click an embryo while your Kakugane is active; 10 by default)
- Defeat a human-type Homunculus *(arrives in a later wave)*
- Defeat an Elite Homunculus *(arrives in a later wave)*

When one is reached, click **[Deepen]** (next tier: stronger form and trait) or **[Re-temper]** (reshape to your current affinity, tier unchanged) in chat.

### The heart Kakugane

Once ever per player: if you would die while carrying an unbonded Kakugane, it replaces your heart and saves you. It can't be dropped or stolen. Call it with **R** on an empty hand. On a true death it leaves your body as an unbonded Kakugane that keeps its form.

### Homunculi

- **Hound:** pack hunter that leaps and pins. Hunts players and villagers. Drops embryos.
- Homunculi take 75% less damage from anything that isn't alchemical (Kakugane forms, conjured arrows, or weapons in the `kakugane:alchemical_weapons` item tag).
- Ambient spawning follows the rarity preset in the config. **Rare** (default) makes ambient Hounds almost absent; places and events that guarantee encounters arrive in later waves. Use the spawn egg to meet them now.

## Commands

| Command | Who | Does |
| --- | --- | --- |
| `/kakugane affinity` | Everyone | Shows how your fighting style reads |
| `/kakugane choose deepen\|retemper` | Everyone | Answers a waiting milestone (the chat buttons run this) |
| `/kakugane give <players> [form] [tier]` | Ops | A new Kakugane, optionally already formed |
| `/kakugane affinity add <players> <form> <amount>` | Ops | Adds affinity (testing) |
| `/kakugane deed <players> human\|embryo\|elite` | Ops | Credits a deed to the active Kakugane (testing) |
| `/kakugane heart reset <players>` | Ops | Returns the heart as an item and allows a new one |

## Config (`config/kakugane-common.toml`)

Imprint threshold, no-choice weight, affinity half-life, serial cap, embryos per milestone, heart on/off, Homunculus damage reduction, rarity preset (RARE / STANDARD / COMMON / CUSTOM), custom Hound weight, Homunculus density cap, debug.

## Performance notes

- Nothing runs per tick for players without an active form beyond two field reads.
- Active forms do a consistency check and passive traits once a second; the Lance dash scans a small box only during its 10-tick dash.
- Affinity, deeds and damage rules run only on hit and death events. Affinity fading is calculated from a timestamp when needed.
- Spawn weights are read once at world load; the Homunculus density check runs only for Hound spawn attempts.
- Form switching is a model override and an item component change; the Hound draws one extra emissive layer.

## Building

Requires Java 21. `gradle build` produces the jar in `build/libs`. `gradle runGameTestServer` runs the automated server-side tests in `src/gametest`. `python3 tools/gen.py` regenerates textures and item models (Pillow and NumPy). `python3 tools/pose_preview.py` solves and previews held-item poses. `gradle runShotsClient` (needs a display, or `xvfb-run`) starts a client that poses each form and saves screenshots to `run/screenshots`; it expects a world in `run/saves/shots`.

## License

MIT (see `LICENSE`), covering the code and the original textures and models in this repository. *Buso Renkin* and its names belong to their owners; this is an unofficial fan mod.

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
