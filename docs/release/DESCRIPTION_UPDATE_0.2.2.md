# Store description: what changed since the 0.2.1 text

Only these 2 parts changed; everything else (and every image) stays as it is. Each part has the Modrinth text
(Markdown) and the CurseForge text (HTML, editor in Source code view). Full files: `DESCRIPTION.md` (Modrinth),
`DESCRIPTION.curseforge.html` (CurseForge). No new pictures. The 0.2.2 changelog is in `UPLOAD.md`.

## 1. Requirements: a new line after the Create line

JEI and Jade are supported now (both optional).

Modrinth (add after the Create line):

```markdown
- Optional: [JEI](https://modrinth.com/mod/jei) 15.20+ (recipe pages for the workstations and weapons, the + button) and [Jade](https://modrinth.com/mod/jade) 11.13+ (turret, workstation and hub info when you look at them)
```

CurseForge (add after the Create `<li>`):

```html
<li>Optional: <a href="https://www.curseforge.com/minecraft/mc-mods/jei">JEI</a> 15.20+ (recipe pages for the workstations and weapons, the + button) and <a href="https://www.curseforge.com/minecraft/mc-mods/jade">Jade</a> 11.13+ (turret, workstation and hub info when you look at them)</li>
```

## 2. Known limits of this beta: delete the first paragraph

Delete this paragraph (keep the Embeddium/Oculus one and the bug-report line after it):

Modrinth:

```markdown
There's no JEI or Jade support yet (each station's GUI lists its own recipes).
```

CurseForge:

```html
<p>There's no JEI or Jade support yet (each station's GUI lists its own recipes).</p>
```
