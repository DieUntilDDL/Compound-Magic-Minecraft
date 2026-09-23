# Program Magic

Program Magic is a combat-focused magic mod for **Minecraft Forge 1.20.1**. Its central idea is spell composition: every wand loadout contains one main spell and three independently triggered sub-spells, allowing the same spell to behave differently depending on where it is equipped.

The current build includes three animated wand tiers, ten spells, progression-based spell unlocking, vanilla enchanting-table integration, mana, casting strength, purification bonuses, custom status effects, entities, particles, and bilingual English/Chinese text.

> **Development status:** the complete gameplay loop is implemented. Balance, multiplayer edge cases, visual polish, and cleanup of the remaining Forge MDK template names are still in progress.

## Demo

### Purified Lightning Chain

![Purified Lightning Chain gameplay](docs/images/lightning-chain-demo.gif)

When the main spell and all three sub-spells are identical, the loadout is **purified** and receives a spell-specific bonus. Purified Lightning Chain gains its second-conduction effect.

## Core Features

- Three GeckoLib-animated wands with different casting strengths.
- One main spell and three sub-spells in each active loadout.
- Ten spells across instant, sustained, formation, and buff casting styles.
- A 5% enchanting chance for a fully homogeneous, purified loadout.
- Spell unlocks tied to exploration and combat achievements.
- A 100-point mana pool with a custom HUD, delayed regeneration, and continuous drain for sustained spells.
- Server-authoritative damage, movement, mana, spell unlocks, and enchanting.
- Custom spell entities, status effects, models, particles, and renderers.
- English and Simplified Chinese localization.

## Getting Started

### 1. Craft an empty wand

Newly crafted wands contain no magic and cannot cast until they are enchanted.

| Wand | Recipe materials | Casting strength |
| --- | --- | ---: |
| Basic Wand | 1 Lapis Lazuli + 2 Sticks | 0.8x |
| Intermediate Wand | 1 Amethyst Shard + 2 Gold Ingots | 1.0x |
| Advanced Wand | 1 Diamond + 2 Nether Quartz | 1.4x |

All three recipes use the same diagonal shape: the core is placed in the upper-right slot, with the two handle materials continuing diagonally toward the lower-left.

Casting strength changes wind-up speed and scales damaging or buff effects. Ice Shield's layer strength is not scaled, but its casting animation is still accelerated. Blast Dash is the only spell that casts immediately without a wind-up.

### 2. Learn magic

Every player begins with **Fire Breath, Ice Shield, Lightning Chain, and Sanctuary**. Other spells are learned by meeting their unlock conditions:

| Spell | Unlock condition |
| --- | --- |
| Umbral Erosion Ray | Defeat the Ender Dragon |
| Blast Dash | Kill a ghast with its reflected large fireball |
| Thunder Storm | Experience thunderstorm weather |
| Ice Mist | Enter powder snow in a biome cold enough for snow |
| Holy Blessing | Accumulate 100 undead kills |
| Dark Devour | Kill an evoker |

Unlock progress is stored on the player and preserved after death. When a spell is learned, the server broadcasts a themed message to the chat.

### 3. Enchant the wand

Place a wand and Lapis Lazuli into a **vanilla enchanting table**. Wands replace the normal enchanting offers with up to three different main-spell choices drawn only from the player's unlocked spell pool.

- Every wand option costs **3 experience levels**.
- The three rows consume **1, 2, or 3 Lapis Lazuli**, matching their vanilla row positions.
- Bookshelves do not affect wand spell offers.
- Selecting an option mounts that main spell and three random unlocked sub-spells.
- Each enchantment has an explicit **5% chance** to make all three sub-spells match the main spell, producing a purified loadout.
- The wand tooltip shows casting strength, main spell, all three sub-spells, and purification status.

Ordinary enchantable items continue to use the vanilla enchanting system.

### 4. Cast

Hold right-click to play the wind-up and cast the equipped main spell. Sustained spells continue while the button is held and stop when it is released or mana runs out. Blast Dash activates immediately.

The mana bar is rendered above the hunger bar. Underwater, it moves above the air bubbles to avoid overlap. Mana regeneration begins after a short delay without spell expenditure.

## Spell Catalogue

| Spell | Type | Main and sub-spell behavior | Purified effect |
| --- | --- | --- | --- |
| Lightning Chain | Instant | Strikes a target and chains between visible enemies; sub copies react to confirmed hits | Enables second conduction |
| Umbral Erosion Ray | Sustained | Charges and projects a ray up to 32 blocks; sub copies can heal from confirmed damage | Pierces every enemy in the line and enables life steal |
| Dark Devour | Instant | Creates a rectangular fang field, executes enemies within a total health budget, and returns proportional damage to the caster; sub copies may devour hit targets | Ignores the health budget and devours every valid target, while retaining the health backlash |
| Blast Dash | Instant | Dashes forward, pauses briefly, then creates a non-block-breaking explosion; the caster takes knockback but no explosion damage, with doubled knockback while airborne | Increases dash distance, damage, and knockback by 50% |
| Fire Breath | Sustained | Deals damage every 5 ticks in a forward cone and ignites targets; sub copies may ignite hit targets | Uses blue soul-flame visuals and applies an eternal blue flame that only water extinguishes |
| Ice Mist | Formation | Creates a damaging slowing mist; prolonged exposure fully freezes movement and actions; sub copies may create a smaller mist around hit targets | Applies a stronger, longer movement-speed and attack-damage reduction |
| Sanctuary | Formation | Creates a luminous area that repeatedly deals heavy damage to undead; sub copies greatly amplify damage against undead targets | Multiplies the radius by about 1.414, doubling the area, and instantly kills undead inside |
| Thunder Storm | Formation | Pulls enemies toward the center with controlled velocity and deals periodic damage; sub copies may pull enemies around a killed target | Stuns affected enemies for 8 seconds at both the beginning and end of the storm |
| Holy Blessing | Buff | Grants custom outgoing-damage and damage-resistance bonuses to the caster and nearby allies; attacks by blessed entities can dispatch sub-spells | Also grants Regeneration II for the blessing duration |
| Ice Shield | Buff | Creates up to four evenly spaced orbiting shields, each blocking one complete attack; sub copies add layers during buff casts or may restore one after a kill | Freezes an attacker for 3 seconds when it breaks a shield |

## Mana Costs

The current values are centralized in [`MagicManaCosts.java`](src/main/java/com/yxty/examplemod/magic/MagicManaCosts.java):

| Spell | Activation cost | Cost per tick |
| --- | ---: | ---: |
| Lightning Chain | 18 | 0 |
| Umbral Erosion Ray | 6 | 0.80 |
| Dark Devour | 35 | 0 |
| Blast Dash | 16 | 0 |
| Fire Breath | 4 | 0.40 |
| Ice Mist | 26 | 0 |
| Sanctuary | 30 | 0 |
| Thunder Storm | 40 | 0 |
| Holy Blessing | 28 | 0 |
| Ice Shield | 22 | 0 |

The default maximum is 100 mana. Regeneration starts 60 ticks after spending mana and restores 0.20 mana per tick. These values are defined in `ManaData.java`.

## Architecture

### Event-driven spell composition

Every spell extends `BaseMagic`. Main behavior lives in `castAsMain(MagicContext)`, while secondary behavior is handled through `triggerAsSub(MagicContext)`. The dispatcher evaluates all three sub-spell slots independently.

`MagicContext` carries the caster, owner, target, position, loadout snapshot, casting strength, damage result, origin, and phase. Phases such as `CAST_BEGIN`, `AFTER_DAMAGE`, `ENTITY_HIT`, and `ENTITY_KILLED` allow sub-spells to react only to meaningful events.

### Multi-tick safety

Beams, areas, buffs, storms, shields, and dash controllers remain active after the initial input. `MagicLoadoutSnapshot` freezes the four equipped spell IDs at cast time, so delayed effects cannot accidentally read a later loadout.

`MagicOrigin` distinguishes primary casts, blessed-entity proxy attacks, shield counters, sub-effects, self-cost, and environmental effects. Dispatch rules use the origin and phase to prevent recursive spell-trigger loops.

### Client and server responsibilities

The client handles input, screens, animation, HUD, particles, and rendering. The server validates enchanting, unlocked spells, experience, Lapis Lazuli, and mana before applying damage, movement, statuses, or entity spawning. Wand loadouts and player progression are stored in Forge capabilities and synchronized to the client.

## Project Structure

```text
src/main/java/com/yxty/examplemod/
|-- capability/   Wand loadouts, learned spells, and player mana
|-- client/       Enchanting-table UI integration and mana HUD
|-- entity/       Persistent spell, area, beam, storm, and shield entities
|-- event/        Unlock progression, enchanting hooks, mana, and debug commands
|-- item/         Wand casting, tooltips, animation, and casting strength
|-- magic/        Spell framework, context, dispatch, costs, and implementations
|-- network/      Client/server synchronization packets
|-- particle/     Custom particle definitions and providers
`-- renderer/     GeckoLib and custom spell renderers
```

## Debug Commands

The following commands require permission level 2:

```mcfunction
/magicdebug purified fire_breath
/magicdebug purified all
/magicdebug strength 1.4
```

The first command converts the currently selected loadout on the held wand into a purified spell. Tab completion lists the accepted spell IDs. The second gives one advanced test wand for every purified spell, and the third overrides the held wand's casting strength.

## Technology and Build

- Java 17
- Minecraft 1.20.1
- Minecraft Forge 47.4.0
- GeckoLib 4.4.9
- Gradle with Mojang official mappings

```powershell
.\gradlew.bat runClient
.\gradlew.bat build
```

The packaged mod is generated in `build/libs/`. GeckoLib for Forge 1.20.1 is required when running the mod outside the development environment.

## License

Program Magic is distributed under the [PolyForm Noncommercial License 1.0.0](LICENSE.txt).

You may use, copy, modify, and redistribute this project for noncommercial purposes, provided the license and required copyright notice remain with redistributed copies. Commercial use requires separate permission from the copyright holder. Because commercial use is restricted, this is a **source-available license**, not an OSI-approved open-source license.

GitHub repository write access is separate from copyright licensing: other users cannot directly change this repository unless the owner grants them access, but they may fork or copy it and make noncommercial modifications under the license. Third-party projects and assets retain their own licenses.
