# Search syntax

Grove's search uses Orgzly-compatible query syntax, so saved searches and habits transfer directly. A query is a sequence of space-separated tokens.

## Combining terms

| Syntax | Meaning |
|---|---|
| `term1 term2` / `term1 AND term2` | AND: both must match |
| `term1 OR term2` | OR: `OR` (uppercase) binds looser than AND, so `a b OR c d` means `(a AND b) OR (c AND d)` |
| `.term` | NOT: prefix any term with `.` to negate it (e.g. `.i.done`, `.t.archive`) |
| `( … )` | Grouping, nestable to any depth: `i.todo (s.today OR d.today)` |
| `.( … )` | Negates a whole group: `.(t.work OR t.home)` = neither tag |

`AND`/`OR` are keywords only in uppercase. Brackets always group (they can't be searched as text), even when attached to a word: `(a b)OR(c)` works. Parsing is lenient: a missing `)` closes at the end of the query, a stray `)` and an empty `()` are ignored.

An empty query matches everything.

## Term types

| Syntax | Matches notes that… | Example |
|---|---|---|
| `word` | contain the text in their heading or body (case-insensitive substring, anywhere in the word: `meet` matches `committee`). A `word` is also matched against notebook **file names** / vault-relative paths: a file whose name matches is listed first, above the content results, and opens its outline when tapped | `meeting` |
| `i.STATE` | have that TODO keyword (case-insensitive); `i.none` = no keyword | `i.todo`, `i.in-progress`, `.i.done` |
| `it.TYPE` | have a keyword of that type: `todo` = any not-done keyword, `done` = any done keyword, `none` = no keyword | `it.todo`, `it.done` |
| `b.NAME` | live in that notebook (`.org` suffix optional) | `b.inbox`, `b.journal.org` |
| `t.TAG` | have the tag, **including inherited** tags from ancestor headings and `#+FILETAGS:`. Substring match: `t.bee` matches `:beeblebrox:` | `t.work` |
| `tn.TAG` | have the tag on the heading itself (own tags only, no inheritance) | `tn.urgent` |
| `p.X` | have priority X | `p.a` |
| `s.PERIOD` | are scheduled on or before the period's end; overdue items always match | `s.today`, `s.3d` |
| `d.PERIOD` | have a deadline on or before the period's end; overdue items always match | `d.1w` |
| `a.PERIOD` | have a bare active `<date>` timestamp (an event) on or before the period's end; `a.today` matches an event exactly on today, `a.overdue` matches an event that has fully passed, `a.none` = no event timestamp. A ranged event counts on every day it spans | `a.today`, `a.7d`, `.a.none` |
| `c.PERIOD` | were closed within the past period | `c.yesterday`, `c.1m` |
| `cr.PERIOD` | were created within the past period (from the `CREATED` property) | `cr.2w` |

## Periods

Used by `s.` `d.` `a.` `c.` `cr.`:

| Token | Meaning |
|---|---|
| `today`, `now` | today |
| `tomorrow` | today + 1 day |
| `yesterday` | today − 1 day |
| `Nd` / `Nw` / `Nm` | N days / weeks / months from today (for `c.`/`cr.`: into the past) |

`s.`/`d.` windows are *"within the period or overdue"*: `s.3d` means scheduled in the next three days **or** any time in the past. `a.` relative windows behave the same way (a past event still matches `a.7d`); `a.today` is exact-day only. `c.`/`cr.` windows are `[today − period, today]`.

## Date comparisons

`PREFIX.OP.DAY` compares the date against one day instead of a window, for any of `s.` `d.` `a.` `c.` `cr.`. Operators: `eq`, `ne`, `lt`, `le`, `gt`, `ge`. `DAY` is `today`/`now`/`tomorrow`/`yesterday` or `Nd`/`Nw`/`Nm`. An unsigned offset counts forward for `s.`/`d.`/`a.` and backward for `c.`/`cr.` (the same direction as their windows); an explicit sign wins (`s.lt.-2d`, `c.le.+0d`). A note without the timestamp never matches, and `overdue`/`nodate` aren't valid comparison days. `a.` matches when any of the note's event days satisfies the comparison.

| Example | Meaning |
|---|---|
| `s.le.today` | scheduled today or earlier |
| `d.gt.1w` | deadline more than a week out |
| `c.eq.today` | closed today |
| `c.ge.1w` | closed within the last week |

## Directives

| Syntax | Effect |
|---|---|
| `o.PROP` | Sort results by a property instead of relevance; `.o.PROP` sorts descending. Repeatable: `o.p o.d` sorts by priority, then deadline. Notes without the property go last in either direction. Properties (Orgzly's set): `b`/`book`/`notebook`, `t`/`title`, `s`/`sched`/`scheduled`, `d`/`dead`/`deadline`, `e`/`event` (also `a`/`active`; ascending uses the oldest event, descending the most recent), `c`/`close`/`closed`, `cr`/`created`, `p`/`pri`/`prio`/`priority`, `st`/`state` (the keyword order from Settings, unconfigured keywords after it). Unknown properties are ignored. |
| `ad.N` | Agenda mode: group results by day over the next N days. A note appears under each day it is scheduled or due; overdue, not-done items surface on today. The drawer's **Agenda** item is `ad.7`. |

## What gets searched

Plain-text terms match against a heading's title plus its **entire** body. (Before the FTS5 migration, body text past the first 4000 characters was silently unsearchable.)

Searching is backed by a SQLite FTS5 index, but the matching rules above are unchanged: the index only narrows which notes get examined, and every result is still decided by the same substring logic. Terms of one or two characters are shorter than the index's smallest unit and are matched by scanning instead, so they work exactly as before, just more slowly on a large vault.

## Ranking

Without `o.` sorts, results are ranked by relevance to the plain-text terms: exact title match, then title contains, then body match, with most-recently-modified as the tiebreaker.

## Examples

```
i.todo s.today                  things to do today (or overdue)
i.todo t.work .t.someday        work TODOs, excluding :someday:
b.inbox OR b.capture            everything in either notebook
d.1w o.d                        deadlines within a week, soonest first
a.7d o.a                        events in the next week, earliest first
phone call cr.2w                notes created in the last 2 weeks mentioning "phone call"
i.none b.journal grateful       journal prose (no TODO keyword) containing "grateful"
ad.7 t.work                     one-week work agenda
i.todo (s.today OR p.A)         TODOs that are scheduled today or are priority A
.(t.work OR t.home) i.todo      TODOs tagged neither work nor home
b.shopping t.tigros AND (it.todo OR (it.done AND c.eq.today)) o.p o.st o.t
                                open tigros items plus ones checked off today, by priority, state, title
```

## Saved searches

Any query can be saved with the ☆ button on the search screen and appears in the drawer (long-press to delete). Defaults: **Scheduled Today** (`s.today`), **All TODO** (`i.todo`), **This Week** (`s.7d`).
