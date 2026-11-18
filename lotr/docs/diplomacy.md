# Diplomacy

Read when the task touches `diplomacy/`, treaties, offers between realms, or kingdom-level war and peace. Built
2026-11-18 (Aaron: "a diplomacy table with a gui for peace and war decisions; peace decisions include trade, offers
for land, etc").

## The table and the screen

- **Diplomacy Table** (`diplomacy/DiplomacyTable`, block `lotr_port:diplomacy_table`, creative tab Colonies; recipe:
  cartography table, two books, two paper, a gold ingot; drawn as a cartography table). Using it opens the screen;
  `/lotr_kingdom diplomacy` does too. Hand-registered: its axe tag is in `converter/tags.py HAND`, its name in
  `colonies.py UI_EN` (both also patched into the generated files).
- **Screen** `lotr_port:diplomacy` (`DiplomacyViews`, a server-built view: no client code). Only a kingdom's ruler
  acts (`Treaties.sideOf`); others see the tables with the actions greyed and why. Nothing about diplomacy goes to
  chat (Aaron 2026-11-18): offers are answered on the Incoming tab, answers show on Sent, and what an action did shows
  in the screen under the frame (`UiNet.Notice`, for every UI-core screen; over the hotbar if no screen is open).
  Sending an offer goes to the Sent tab.

| Tab | Shows | Does |
|---|---|---|
| Realms | every kingdom, AI realm and playable great power: kind, relation, treaties (incl. vassal/overlord), war, land won | Make an offer (or double-click), Declare war (confirm) |
| New offer | context = the other side. Relation, war, how it answers; **You give** / **You ask**: coins (steppers, up to each treasury), land squares (up to `max_land`), a colony (picker); **Agreements**: end the war (when at war), trade agreement, truce days, alliance, vassalage (they swear to you / you swear to them); the offer in words and why it can't be sent | Send offer, Clear. Drafts are kept per player and side for the session |
| Incoming (n) | offers to you: from, terms from your side, whether they can be carried out now, days left. Double-click or Look closer opens one in full (context = its id): relation, every term, why it can't be done | Accept (confirm), Decline; All offers |
| Sent | your offers still waiting, and what became of recent offers to and from you (`Treaties.Answer`, the last 20 per side): accepted, refused (an AI's no), declined, withdrawn, expired | Withdraw |
| Treaties | truces, trade agreements, alliances: days held, end | Break (confirm; a truce holds until it ends) |
| Wars | sides you're at war with (realm wars and colony wars), state, days | Offer peace (opens Offer with end the war and a 10-day truce) |

## Rules (`Treaties`, SavedData `lotr_port_treaties`, `dataVersion` 1)

- Sides are factions: player kingdoms (realms with a ruler), AI realms and great powers. A side's coins are its
  capital's treasury (its oldest colony in Middle-earth, else any), so a side without colonies has none.
- **Offers** (`Terms`): coins, land and a colony each way, trade, truce, alliance, vassalage, end the war. Checked when
  drafted, sent and accepted (`check`): treasuries, enough land, colonies still theirs, colonies only to a kingdom with
  a ruler, no truce/trade/alliance while at war unless the war ends, no second overlord. **Accepting carries out every
  term or none.** A kingdom's offer waits `offer_days` in the other ruler's Incoming tab; a great power or AI realm answers
  at once (`aiAnswer`).
- **Land** in a treaty: the giver's held squares nearest the receiver's capital (else the giver's), within
  `land_search` map pixels, moved by `Borders.take` (the original map stays; see `borders.md`).
- **Colonies** in a treaty: `Colonies.transfer` to the receiving kingdom's ruler (their land goes with them).
- **Trade agreement**: caravans trade and supply as between friends (`Caravans.standing`). **Alliance**: relation ally
  while it lasts (broken: neutral). **Truce**: neither side may declare war until it ends. **Vassalage**:
  `Diplomacy.setOverlord`. **End the war**: every war between the two sides ends (`Wars.end`, "treaty"); an enemy
  relation set in play goes back to neutral.
- **War** (`declareWar`): the ruler only, not during a truce, not on your overlord. Trade and alliances with them break,
  waiting offers between you go, relation turns enemy, and a `war/` war between the two realms starts after the usual
  warning; its land can then be taken (`border/Conquest`).
- **AI answers**: worth = relation bias + coins given x `coin_value` - coins asked x `coin_value` x `keep_factor` (land
  `land_value`, colonies `colony_value` the same way) + trade (`trade_value`, against it with an enemy) + peace
  (`peace_value` + `war_day_value` per day at war, the war's bitterness not counted twice) + alliance (`alliance_value`
  with a friend, three times against otherwise) + vassalage (`vassal_value` for you swearing to it; it never swears).
  Accepts at 0 or more. Numbers: `data/lotr_port/diplomacy/settings.json`.
- History: `diplomacy_offer` (proposed, accepted, declined, withdrawn, with the terms), `diplomacy_treaty`,
  `diplomacy_broken`; wars and land as before.

## Tests

GameTests `DiplomacyTests` (batch `diplomacy`): two kingdoms agree coins + trade + truce (coins move, trade holds, no
war in a truce, breaking ends trade; the proposer can't accept their own offer); Gondor refuses to swear fealty and
takes trade at neutral standing; war on Mordor makes enemies and blocks trade, and Mordor takes peace with a truce.

## Not yet

- AI powers making offers or declaring war on players themselves; reputation for broken treaties.
- Councils (a steward deciding for an absent ruler: a `Perm`); tribute over time (vassal tribute is per colony today).
- Trade agreements doing more than caravans (market prices, intake caps).
- Not looked at in game: the screen's layout with five sections on the Offer tab.
