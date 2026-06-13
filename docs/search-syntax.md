# Search syntax

Grove's search uses Orgzly-compatible query syntax, so saved searches and habits transfer directly. A query is a sequence of space-separated tokens.

## Combining terms

| Syntax | Meaning |
|---|---|
| `term1 term2` | AND — both must match |
| `term1 OR term2` | OR — `OR` (uppercase) binds looser than the implicit AND, so `a b OR c d` means `(a AND b) OR (c AND d)` |
| `.term` | NOT — prefix any term with `.` to negate it (e.g. `.i.done`, `.t.archive`) |

An empty query matches everything.

## Term types

| Syntax | Matches notes that… | Example |
|---|---|---|
| `word` | contain the text in their heading or body (case-insensitive substring) | `meeting` |
| `i.STATE` | have that TODO keyword (case-insensitive); `i.none` = no keyword | `i.todo`, `i.in-progress`, `.i.done` |
| `b.NAME` | live in that notebook (`.org` suffix optional) | `b.inbox`, `b.journal.org` |
| `t.TAG` | have the tag — **including inherited** tags from ancestor headings and `#+FILETAGS:`. Substring match: `t.bee` matches `:beeblebrox:` | `t.work` |
| `tn.TAG` | have the tag on the heading itself (own tags only, no inheritance) | `tn.urgent` |
| `p.X` | have priority X | `p.a` |
| `s.PERIOD` | are scheduled on or before the period's end — overdue items always match | `s.today`, `s.3d` |
| `d.PERIOD` | have a deadline on or before the period's end — overdue items always match | `d.1w` |
| `c.PERIOD` | were closed within the past period | `c.yesterday`, `c.1m` |
| `cr.PERIOD` | were created within the past period (from the `CREATED` property) | `cr.2w` |

## Periods

Used by `s.` `d.` `c.` `cr.`:

| Token | Meaning |
|---|---|
| `today`, `now` | today |
| `tomorrow` | today + 1 day |
| `yesterday` | today − 1 day |
| `Nd` / `Nw` / `Nm` | N days / weeks / months from today (for `c.`/`cr.`: into the past) |

`s.`/`d.` windows are *"within the period or overdue"*: `s.3d` means scheduled in the next three days **or** any time in the past. `c.`/`cr.` windows are `[today − period, today]`.

## Directives

| Syntax | Effect |
|---|---|
| `o.PROP` | Sort results by a property instead of relevance. Properties: `priority`/`p`, `scheduled`/`s`, `deadline`/`d`, `created`/`cr`, `title`, `notebook`/`b`. Repeatable — `o.p o.d` sorts by priority, then deadline. |
| `ad.N` | Agenda mode: group results by day over the next N days. A note appears under each day it is scheduled or due; overdue, not-done items surface on today. The drawer's **Agenda** item is `ad.7`. |

## Ranking

Without `o.` sorts, results are ranked by relevance to the plain-text terms: exact title match, then title contains, then body match — with most-recently-modified as the tiebreaker.

## Examples

```
i.todo s.today                  things to do today (or overdue)
i.todo t.work .t.someday        work TODOs, excluding :someday:
b.inbox OR b.capture            everything in either notebook
d.1w o.d                        deadlines within a week, soonest first
phone call cr.2w                notes created in the last 2 weeks mentioning "phone call"
i.none b.journal grateful       journal prose (no TODO keyword) containing "grateful"
ad.7 t.work                     one-week work agenda
```

## Saved searches

Any query can be saved with the ☆ button on the search screen and appears in the drawer (long-press to delete). Defaults: **Scheduled Today** (`s.today`), **All TODO** (`i.todo`), **This Week** (`s.7d`).
