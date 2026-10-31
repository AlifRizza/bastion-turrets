# Store description: what changed since the 0.5.0 text

Only these parts changed; everything else (and every old image) stays as it is. Each part has the Modrinth text
(Markdown) and the CurseForge text (HTML, editor in Source code view). Full files: `DESCRIPTION.md` (Modrinth),
`DESCRIPTION.curseforge.html` (CurseForge). The 0.6.0 changelog is in `UPLOAD.md`.

Not uploaded yet: 0.6.0 goes up after 0.5.0.

## 1. A new section: Research and the Turret Codex

Between "Workstations" and "Effects". Image: `screenshots/23_turret_codex.png` (CurseForge: `screenshots-840/23_turret_codex.jpg`).

Modrinth:

```markdown
## Research and the Turret Codex

Survival worlds open Bastion step by step. Every player gets a Turret Codex the first time they join (or crafts one from a book, an iron ingot and redstone). Its left half is the research tree in three tabs, Turrets, Workshop and Modules, growing from the four early turrets up to the 3x3 ones, with what each card brings along (ammo, stations, tools) beside it. Its right half is a page for the card you pick: the item in 3D, the base it needs and its ammo, bars for damage, fire rate and range, what it unlocks and what it still needs.

- A card needs the cards below it, and a turret also needs the base of its size. Press Research to pay its cost from your inventory: iron and copper for the first steps, up to diamonds and netherite scrap for the 3x3 turrets.
- Research unlocks recipes. A researched turret opens its parts and its ammo too. Gear you got another way (loot, creative, another mod) still works.
- Research is shared by everyone in the world, and a chat line tells them. Worlds from before 0.6.0 start with everything researched.
- Servers can change every cost in `config/bastion-research.toml` or switch research off; admins have `/bastion research grant|revoke|list`.

<!-- screenshot: 23_turret_codex.png -->
```

CurseForge:

```html
<h2>Research and the Turret Codex</h2>
<p>Survival worlds open Bastion step by step. Every player gets a Turret Codex the first time they join (or crafts one from a book, an iron ingot and redstone). Its left half is the research tree in three tabs, Turrets, Workshop and Modules, growing from the four early turrets up to the 3x3 ones, with what each card brings along (ammo, stations, tools) beside it. Its right half is a page for the card you pick: the item in 3D, the base it needs and its ammo, bars for damage, fire rate and range, what it unlocks and what it still needs.</p>
<ul>
<li>A card needs the cards below it, and a turret also needs the base of its size. Press Research to pay its cost from your inventory: iron and copper for the first steps, up to diamonds and netherite scrap for the 3x3 turrets.</li>
<li>Research unlocks recipes. A researched turret opens its parts and its ammo too. Gear you got another way (loot, creative, another mod) still works.</li>
<li>Research is shared by everyone in the world, and a chat line tells them. Worlds from before 0.6.0 start with everything researched.</li>
<li>Servers can change every cost in <code>config/bastion-research.toml</code> or switch research off; admins have <code>/bastion research grant|revoke|list</code>.</li>
</ul>
<p>[IMAGE: screenshots-840/23_turret_codex.jpg]</p>
```

## 2. Workstations: one sentence

Modrinth and CurseForge (same text in both):

Old: The crafting table makes only the stations and the early gear (the Makeshift Turret Base and its four turrets).

New: The crafting table makes only the stations (once researched, see below), the Turret Codex and the early gear (the Makeshift Turret Base and its four turrets).

## 3. For modpack makers: one sentence added

Modrinth: after "a changed number in the config wins over a datapack." add: What each research costs, and whether research is on at all, is in `config/bastion-research.toml`.

CurseForge: the same, with `<code>config/bastion-research.toml</code>`.

## 4. Requirements: the JEI line

Old: (recipe pages for the workstations and weapons, the + button)

New: (recipe pages for the workstations and weapons, research pages, the + button)

## 5. Known limits: the placeholder sounds line goes

The sounds are real now: in "Known limits of this beta", delete the paragraph that begins "The four early turrets, the
Makeshift Turret Base and the Javelin Missile" (the one after the Embeddium / Oculus paragraph), on both sites.
