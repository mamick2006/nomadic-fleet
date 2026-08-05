# Adding Works, colony items and allocations from your own mod

Nomad Flotilla reads its buildable Works from `data/config/nomad/nomad_works.csv`. The game merges that
path across every enabled mod.

For a more complete example of this and everything else below, see `https://github.com/mamick2006/crossmod-demo`.

## Adding a Work

Add to `nomad_works.csv` something like:

```
id,name,category,scope,cost,space,staff,pctOfTotal,illustration,effect,plugin
mymod_choir,Chapel Choir,Fortune,flotilla,40000,60,20,0,,Crew morale improves.,
```

Every Work needs an illustration, so register one under the derived key in your own `settings.json`:

```
"graphics": { "illustrations": { "nomad_work_illus_mymod_choir": "graphics/mymod/works/choir.png" } },
```

480x300 source, drawn into a 96x60 cell at the head of the row. A Work that resolves no illustration
renders an empty cell where every other Work shows art.

## The columns

| Column | What it does |
|---|---|
| `id` | The Work's id. |
| `name` | The display name. |
| `category` | `Economy`, `Capacity`, `Fleet`, `Combat` or `Fortune`. Groups the Works tab. |
| `scope` | `fleet` if the effect covers every ship in the fleet carrying the flagship, `flotilla` if it covers only ships converted by a Nomad hullmod. |
| `cost` | Credits to build. |
| `space` | Amount of space for the work. |
| `staff` | Staffing of the work. |
| `pctOfTotal` | An extra percentage share of the Flotilla's total space. |
| `illustration` | Registry key for the illustration, which is required. Blank defaults to `nomad_work_illus_<id>`. |
| `effect` | The one-line effect shown in the Works tab. |
| `plugin` | Your `nomad.api.NomadWorkEffect` class, if applicable. |

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

## Adding an allocation

"Normal" commodity allocations are currently unsupported. Instead, you can add a custom allocation like mining, autoforge, and credits. Add a row in `data/config/nomad/nomad_allocations.csv` and a class:

```
id,name,effect,staffPerSpace,icon,plugin
mymod_choir_line,Choir Line,Credits and a trickle of supplies.,0.4,mymod_choir_icon,mymod.choir.ChoirLine
```

| Column | What it does |
|---|---|
| `id` | The allocation's id. |
| `name` | The display name. |
| `effect` | The one-line effect shown on the allocation's row. |
| `staffPerSpace` | Residents employed per space unit assigned. |
| `icon` | Registry key for the 32x32 icon, registered in your own `settings.json`. |
| `plugin` | Your `nomad.api.NomadAllocationEffect` class. Required. |

Extend `BaseNomadAllocationEffect` rather than implementing the interface:

```java
public class ChoirLine extends BaseNomadAllocationEffect {
  @Override
  public void applyTick(NomadAllocationTick tick) {
    tick.addCredits(4f * tick.getSpace() * tick.getProductionEfficiency() * tick.getMonthFraction());
  }
}
```

`applyTick` runs once per economy tick. The tick contains the
space, the staff it employs, the residents aboard, this tick's share of a month, and the same production
efficiency every other line runs at. It returns credits and units of a commodity
the Flotilla tracks.

## Knowing whether the player has a Flotilla

Check `Global.getSector().getMemoryWithoutUpdate().getBoolean("$nomad_founded")`.
