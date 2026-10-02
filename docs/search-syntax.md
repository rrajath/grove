# Search syntax

Grove's search uses Orgzly-compatible query syntax (see the [Orgzly Revived search docs](https://www.orgzlyrevived.com/docs#search)), so saved searches and habits transfer directly. A query is a sequence of space-separated tokens.

## Combining terms

| Syntax | Meaning |
|---|---|
| `term1 term2` / `term1 AND term2` | AND: both must match |
| `term1 OR term2` | OR: binds looser than AND, so `a b OR c d` means `(a AND b) OR (c AND d)` |
| `.term` | NOT: prefix any term with `.` to negate it (e.g. `.i.done`, `.t.archive`) |
| `( … )` | Grouping, nestable to any depth: `i.todo (s.today OR d.today)` |
| `.( … )` | Negates a whole group: `.(t.work OR t.home)` = neither tag |
| `"…"` | Quoting: keeps spaces, brackets and keywords inside one value or text term |

`AND`/`OR` are case-insensitive (`or`, `Or` and `OR` all work), as in Orgzly. Brackets always group, even when attached to a word: `(a b)OR(c)` works. Parsing is lenient: a missing `)` or closing `"` ends at the end of the query; a stray `)`, an empty `()` and empty quotes `""` are ignored.

Quoting:

- `b."My Notebook"` searches a notebook whose name has a space.
- `"phone call"` is one text term, matched as a phrase rather than two words.
- `"or"`, `"f(x)"`, `"i.todo"` are searched as literal text instead of being parsed as syntax.
- `."draft copy"` negates a quoted term.

An empty query matches everything.

## Term types

| Syntax | Matches notes that… | Example |
|---|---|---|
| `word` | contain the text in their heading, body or own tags (case-insensitive substring, anywhere in the word: `meet` matches `committee`). A `word` is also matched against notebook **file names** / vault-relative paths: a file whose name matches is listed first, above the content results, and opens its outline when tapped | `meeting` |
| `i.STATE` | have that TODO keyword (case-insensitive); `i.none` = no keyword | `i.todo`, `i.in-progress`, `.i.done` |
| `it.TYPE` | have a keyword of that type: `todo` = any not-done keyword, `done` = any done keyword, `none` = no keyword | `it.todo`, `.it.done` |
| `b.NAME` | live in that notebook (`.org` suffix optional; quote names with spaces) | `b.inbox`, `b."My Notebook"` |
| `t.TAG` | have the tag, **including inherited** tags from ancestor headings and `#+FILETAGS:`. Substring match: `t.bee` matches `:beeblebrox:` | `t.work` |
| `tn.TAG` | have the tag on the heading itself (own tags only, no inheritance) | `tn.urgent` |
| `p.X` | have priority X. A heading with no priority counts as the **Default priority** from Settings › Notes, like org's default priority (with no default set, only explicit priorities match) | `p.a`, `.p.c` |
| `ps.X` | have priority X written on the heading (the default priority never counts) | `ps.b` |
| `s.DATE` | are scheduled matching DATE (default operator `le`) | `s.today`, `s.3d` |
| `d.DATE` | have a deadline matching DATE (default `le`) | `d.le.2d` |
| `e.DATE` / `a.DATE` | have a bare active `<date>` timestamp (an event) on a day matching DATE (default `eq`). A ranged event counts on every day it spans | `e.today`, `e.ge.now`, `.a.none` |
| `c.DATE` | were closed matching DATE (default `eq`) | `c.yesterday`, `c.ge.-1w` |
| `cr.DATE` | were created matching DATE (from the `CREATED` property; default `le`) | `cr.ge.-2w` |

## Dates

A date token is `PREFIX.[OP.]TIME`. TIME names one day, and the timestamp's date is compared against it with OP, or one moment (`now`, `Nh`), and the timestamp's time is compared too (see below). When OP is omitted, each prefix uses Orgzly's default: **`le` for `s.`, `d.` and `cr.`; `eq` for `c.` and `e.`/`a.`**. (Orgzly's docs don't name a default for `e.`; Grove uses `eq` so `e.today` means "events today".)

| OP | Meaning |
|---|---|
| `eq` / `ne` | on / not on TIME |
| `lt` / `le` | before / on or before TIME |
| `gt` / `ge` | after / on or after TIME |

| TIME | Meaning |
|---|---|
| `today`, `tod` | today |
| `tomorrow`, `tom`, `tmrw` | today + 1 day |
| `yesterday` | today − 1 day |
| `Nd` / `Nw` / `Nm` / `Ny` | N days / weeks / months / years from today. N can be negative: `-2w` is two weeks ago |
| `now` | this moment (to the minute) |
| `Nh` | N hours from now; `-2h` is two hours ago |
| `yyyy-mm-dd` | that date (a Grove extension, used by the Filters panel's custom range) |

Two Grove specials take no operator: `overdue` (the date is before today) and `none` (aliases `no`, `nodate`: the timestamp is absent).

A note without the timestamp never matches a comparison. `e.`/`a.` match when any of the note's event days satisfies it.

Against `now` and `Nh`, a timed entry (`<2026-10-01 Thu 09:00>`) is compared at its time, so `s.ge.now` drops this morning's 09:00 task by 10:30. An entry with an end time (`10:00-11:00`, or a timed range) counts until it ends. An untimed entry covers its **whole day**: it matches when any moment of that day does, so `e.ge.now` still shows today's all-day events and `s.le.now` today's untimed tasks. (Orgzly treats an untimed entry as midnight; Grove doesn't, so all-day items don't vanish at 00:01.)

| Example | Meaning |
|---|---|
| `s.today` | scheduled today or earlier (overdue included) |
| `s.eq.today` | scheduled exactly today |
| `s.3d` | scheduled within the next three days, or overdue |
| `d.gt.1w` | deadline more than a week out |
| `c.today` | closed today |
| `c.ge.-1w` | closed within the last week |
| `cr.-1m` | created a month ago or earlier |
| `e.ge.today` | has an event today or later |

## Directives

| Syntax | Effect |
|---|---|
| `o.PROP` | Sort results by a property instead of relevance; `.o.PROP` sorts descending. Repeatable: `o.p o.d` sorts by priority, then deadline. Notes without the property go last in either direction (for `o.p`, an unprioritized note sorts at the default priority when one is set). Properties (Orgzly's set): `b`/`book`/`notebook`, `t`/`title`, `s`/`sched`/`scheduled`, `d`/`dead`/`deadline`, `e`/`event` (also `a`/`active`; ascending uses the oldest event, descending the most recent), `c`/`close`/`closed`, `cr`/`created`, `p`/`pri`/`prio`/`priority`, `st`/`state` (the keyword order from Settings, unconfigured keywords after it). Unknown properties are ignored. |
| `ad.N` | Narrows results to notes scheduled or due within the next N days, overdue included. Unlike Orgzly, results are not regrouped by day in Search (Grove's day-grouped view is the separate Agenda screen) and events aren't included. |

## What gets searched

Plain-text terms match against a heading's title plus its **entire** body, and, as in Orgzly, its own tags (substring, so `pho` matches `:phone:`). Inherited tags and `#+FILETAGS:` don't count; use `t.` for those. A heading picked by its tags shows as a heading row, tags visible. (Before the FTS5 migration, body text past the first 4000 characters was silently unsearchable.)

Searching is backed by a SQLite FTS5 index, but the matching rules above are unchanged: the index only narrows which notes get examined, and every result is still decided by the same substring logic. Terms of one or two characters are shorter than the index's smallest unit and are matched by scanning instead, so they work exactly as before, just more slowly on a large vault.

## Ranking

Without `o.` sorts, a query with plain-text terms is ranked by relevance: exact title match, then title contains, then body (or tag) match, with most-recently-modified as the tiebreaker.

A filter-only query (no plain text) uses Orgzly's default order: notebook name, then priority (an unprioritized heading at the default priority when one is set, otherwise last), then scheduled time when the query has an `s.` term and deadline time when it has a `d.` term, then position in the notebook.

## Examples

```
i.todo s.today                  TODOs scheduled today or overdue
i.todo s.eq.today               TODOs scheduled exactly today
i.todo t.work .t.someday        work TODOs, excluding :someday:
(b.Home or b.Work) phone        "phone" in either notebook
b."Work Projects" .i.done       not-done notes in a notebook with a space in its name
d.1w o.d                        deadlines within a week (or overdue), soonest first
e.ge.today e.le.7d o.e          events in the next week, earliest first
"phone call" cr.ge.-2w          notes created in the last 2 weeks mentioning "phone call"
p.b                             priority B, plus unprioritized notes if the default priority is B
ps.b                            priority B written on the heading
i.none b.journal grateful       journal prose (no TODO keyword) containing "grateful"
ad.7 t.work                     work items due or scheduled in the next week
.(t.work OR t.home) i.todo      TODOs tagged neither work nor home
b.shopping t.tigros AND (it.todo OR (it.done AND c.eq.today)) o.p o.st o.t
                                open tigros items plus ones checked off today, by priority, state, title
```

## Saved searches

Any query can be saved with the ☆ button on the search screen and appears in the drawer (long-press to delete). Defaults: **Scheduled Today** (`s.eq.today`), **All TODO** (`i.todo`), **This Week** (`s.7d`).

Searches saved before the switch to Orgzly's date defaults are rewritten once, when first read, so they keep their meaning (`QueryMigration`). For example `s.today` becomes `s.eq.today`, `c.1w` becomes `c.ge.-1w`, `a.7d` becomes `a.le.7d`, and a lowercase `or` that used to be plain text becomes `"or"`.
