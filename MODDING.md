# Adding Works and colony items from your own mod

Nomad Flotilla reads its buildable Works from `data/config/nomad/nomad_work_sizes.csv`. It merges that
path across every enabled mod.

For a more complete example of this and everything else below, see `https://github.com/mamick2006/crossmod-demo`.

## Adding a Work

Add to `nomad_work_sizes.csv` something like:

```
id,work,size,name,category,scope,space,staff,cost,makes,uses,params,illustration,effect,plugin
mymod_choir_1,mymod_choir,1,Chapel Choir,Fortune,flotilla,60,20,40000,,,,,Crew morale improves.,mymod.choir.Choir
mymod_choir_2,mymod_choir,2,,,,180,40,80000,,,,,,
```

Each row is one Work at one size. The player builds size 1 and can upgrade it to size 2. A Work with one size has one row. Sizes run from 1 with no gap.

Fill in the Work's name, category, scope, illustration, effect and plugin on its size 1 row only. They are ignored on the rows above it.

Every Work needs an illustration, so register one under the derived key in your own `settings.json`:

```
"graphics": { "illustrations": { "nomad_work_illus_mymod_choir": "graphics/mymod/works/choir.png" } },
```

480x300 source. A Work that resolves no illustration renders an empty space on its card where every other Work shows art.

## The columns

| Column | What it does |
|---|---|
| `id` | The row's id, unique across every mod. Use `<work>_<size>`. A row with the id of one of Nomad Flotilla's rows replaces it, and has to keep that row's `work` and `size`. |
| `work` | The Work's id. The player's save holds this. |
| `size` | 1 for the first size, 2 for the first upgrade, and so on. |
| `name` | The display name. Size 1 only. |
| `category` | `Economy`, `Capacity`, `Fleet`, `Combat` or `Fortune`. Shown in the build list. Size 1 only. |
| `scope` | `fleet` if the effect covers every ship in the fleet carrying the flagship, `flotilla` if it covers only ships converted by a Nomad hullmod. Size 1 only. |
| `space` | Amount of space for the work at this size. |
| `staff` | Staffing of the work at this size. |
| `cost` | Credits to build this size: the build for size 1, the upgrade from the size below for the rest. |
| `makes` | What the Work makes a month at 100% global efficiency, as `good:amount` pairs joined by `;`, for example `supplies:9.4`. Blank if it makes nothing. |
| `uses` | What it uses a month, written the same way. A pair ending in `>good` feeds only that good, so that good stops on its own when the input runs out: `ore:86>metals`. |
| `params` | Numbers for Nomad Flotilla's own Works. Your plugin can't read them, so leave it blank. |
| `illustration` | Registry key for the illustration, which is required. Blank defaults to `nomad_work_illus_<work>`. Size 1 only. |
| `effect` | The one-line effect shown in the Work's hover. Size 1 only. |
| `plugin` | Your `nomad.api.NomadWorkEffect` class, if applicable. Size 1 only. |

Goods in `makes` and `uses` are the fifteen commodities the Flotilla stores: `food`, `organics`, `domestic_goods`, `luxury_goods`, `supplies`, `fuel`, `metals`, `rare_metals`, `ore`, `rare_ore`, `volatiles`, `heavy_machinery`, `hand_weapons`, `drugs` and `organs`. `makes` can also name `credits`. `uses` can also name `residents`: the Work then consumes residents, and stops before Basic Needs would fall to a lower level.

A Work with `makes` or `uses` on any of its rows is a production Work. It runs at every Basic Needs level above Non-existent, and global efficiency scales what it makes. A Work with neither, like the example above, is a support Work. Its effect is on only while it has its staff and Basic Needs is at Spartan or better.

A support Work whose `category` is `Fleet`, `Combat` or `Fortune` counts toward the Flotilla's support limit, and one in `Economy` or `Capacity` does not. The Flotilla runs 3 counted Works at once, one more at 200 residents and one more each time that number doubles, and the lowest in the staffing order past that are disabled. A disabled Work gets no effect calls, like a mothballed one: no `applyTick`, and `getEffectSize` returns 0.

A row Nomad Flotilla can't use is left out, and `starsector.log` names the row and your mod's folder. Your other rows still load.

## Running code from a Work

Name your class in `plugin`. Extend `BaseNomadWorkEffect` rather than implementing the interface:

```java
public class Choir extends BaseNomadWorkEffect {
  @Override
  public void applyTick(NomadWorkTick tick) {
    tick.addCredits(500f * tick.getSize() * tick.getProductionEfficiency() * tick.getMonthFraction());
  }
}
```

`applyTick` runs once per economy tick while the Work runs: built, not mothballed, suspended or disabled, and with its staff. A support Work also needs Basic Needs at Spartan or better. A production Work gets no tick while it is idle. The tick gives:

| Method | What it does |
|---|---|
| `getSize()` | The size the Work is built at, starting at 1. While an upgrade is being built, the size below it. |
| `getSpace()` | That size's `space`. |
| `getStaff()` | That size's `staff`. |
| `getMonthFraction()` | This tick's share of a month. Multiply a monthly amount by it. |
| `getProductionEfficiency()` | The Flotilla's global efficiency: its Basic Needs level's figure, raised by colony items, the Guild Hall and the difficulty setting. 1.0 at the Normal level in Survival difficulty, with no items and no Guild Hall. |
| `areNeedsMet()` | True while Basic Needs is at Spartan or better. A support Work only runs then, so this is for a production Work or a colony item. |
| `addCredits(amount)` | Pays the player credits, on the monthly income report. |
| `addCommodity(id, units)` | Adds units of one of the fifteen goods to the Flotilla's stores, up to that store's target. Units past the target go to the player's cargo if the good's Out flag is on, the Flotilla is not detached and the cargo has room. Otherwise they are not added. |
| `addFleetStat(stat, amount)` | Asks for a fleet-wide effect from `NomadFleetStat`, such as sensor range or burn. It lasts one tick, so ask every tick. |

Your mod doesn't need Nomad Flotilla as a dependency. Only Nomad Flotilla loads your plugin class, so without it the class is never loaded.

To check a support Work's effect at any other moment, such as when your mod prices something, call:

```java
int size = NomadWorks.getEffectSize("mymod_choir");
```

It returns the size the effect is on at, or 0 when it is off. It reads the Flotilla as it stands, so it reads 0 as soon as the player scraps or mothballs the Work, without waiting for the next tick. Call it only while Nomad Flotilla is enabled: check `Global.getSettings().getModManager().isModEnabled("nomadic_fleet")` first, and make the call from a class your mod loads only after that check. A Flotilla older than 2.0 has no `NomadWorks`, so catch `LinkageError` around the call if your mod should run beside one.

## Work prerequisites

A Work, or its larger sizes, can ask for something the Flotilla's economy cannot make. Add rows to `data/config/nomad/nomad_work_prereqs.csv`, which is merged across mods like the Works table:

```
id,work,from_size,kind,value,at_least
mymod_choir_1_standing,mymod_choir,1,standing,luddic_church|luddic_path,WELCOMING
mymod_choir_2_ship,mymod_choir,2,ship,mymod_chapel_ship,
```

| Column | Holds |
|---|---|
| `id` | The row's id. Reuse one of ours to replace that row. |
| `work` | The Work's id, as `nomad_work_sizes.csv` names it. |
| `from_size` | The lowest size the row covers. 1 is the whole Work. |
| `kind` | `item`, `ship`, `level`, `officers`, `standing`, `mission`, `commission`, `colonies`, `contacts`, `work`, `residents`, or `none`. |
| `value` | For `item`, `ship` and `standing`: item ids from `nomad_items.csv`, hull ids, or faction ids. Separate alternatives with `\|`; any one of them meets the row. For `mission`: entries written `key=Name`, a sector memory flag your quest sets when it is completed and the quest's name as the player reads it, such as `$gaATG_missionCompleted=At The Gates`. For `commission`: faction ids, or blank for a commission with any faction. For `work`: Work ids. Blank for `level`, `officers`, `colonies`, `contacts` and `residents`. |
| `at_least` | A whole number for `level`, `officers`, `colonies`, `contacts` and `residents`. A `RepLevel` name (`FAVORABLE`, `WELCOMING`, ...) for `standing`. For `work`, the size the other Work must be built to; blank is 1. Blank otherwise. |

A ship counts while it carries one of the two Nomad hullmods and is not mothballed. Its hull id, base hull id or (D) parent hull id may match. Every officer on the player's roster counts. In a sector with no Galatia Academy, a Galatia Academy mission counts as completed, and in one where Volturn is missing or is not the Sindrian Diktat's, "Passage To Volturn" does.

Colonies count the player's own markets and the ones the player governs for another faction. Contacts count suspended ones, not potential or lost ones. A `work` row is met by the other Work built at that size or larger, even mothballed, suspended or disabled; a build under way counts at the size it had. A `work` row that names its own Work, or closes a circle with an earlier row, is dropped. A `residents` row is met from its number, and once the Work runs it holds until the Flotilla is 5% under it (`nomad_prereq_residents_slack` in `settings.json`).

Several rows on one Work must all be met. A Work is disabled while a row covering its built size is unmet. It cannot be built or upgraded into such a size.

To remove one of our rows, ship a row with the same `id` and `kind` `none`.

A row this version cannot use is dropped with one line in `starsector.log`, and the Work asks nothing of it. That covers an unknown kind, a `from_size` the Work does not have, a bad `at_least`, and a `value` that names nothing the game knows. A row for a Work no enabled mod defines is skipped quietly, and it does not replace one of our rows with the same `id`.

## Removed in 2.0

`nomad_works.csv` and its `pctOfTotal` column are gone. A Work's Space is its row's `space`.

`nomad_allocations.csv` is gone, and so are `NomadAllocationEffect`, `BaseNomadAllocationEffect` and `NomadAllocationTick`. To keep a custom allocation, make it a Work: give it a row per size, and move its `applyTick` to a class that extends `BaseNomadWorkEffect`. An allocation that only turned goods into goods needs no class: put them in `makes` and `uses`.

A mod that still ships either file loads, and `starsector.log` names it, but what the file added is missing. A mod whose own code still names one of the removed types crashes the game when that code loads, and Nomad Flotilla can't catch that.

## Adding a colony item

Add a new installable nomad colony item in `data/config/nomad/nomad_items.csv`.

```
id,effect,efficiency,plugin
mymod_choir_engine,Global efficiency +4%.,0.04,
```

| Column | What it does |
|---|---|
| `id` | The item's id, from `special_items.csv`. |
| `effect` | The one-line effect shown in the Items tab. |
| `efficiency` | Global production efficiency the item adds while installed, as a fraction: `0.04` is +4%. Can be left blank. |
| `plugin` | Your `nomad.api.NomadItemEffect` class, if applicable. Independent of `efficiency`: a row can set both. |

## Knowing whether the player has a Flotilla

Check `Global.getSector().getMemoryWithoutUpdate().getBoolean("$nomad_founded")`.

## Knowing whether the Manage screen is open

Nomad Flotilla, Liminal, Captain's Quarters and Project Arondight share one Manage button on the bottom bar and one Manage screen, so this covers all four mods.

Check `Global.getSector().getMemoryWithoutUpdate().getBoolean("$mgmt_open")`.

If the button conflicts with your mod, each mod has a setting that moves its own screen from the button to an intel entry: `nomad_mgmt_intel_only`, `liminal_mgmt_intel_only`, `cq_mgmt_intel_only` and `arondight_mgmt_intel_only`. The button is gone only when every installed one of the four has it on. A change takes effect on the next load.
