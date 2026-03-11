# MaxDonates

Minecraft (Paper 1.20+) plugin pro posilani donatu mezi hraci a firmami pres Vault ekonomiku.

## Funkce
- Donate mezi hraci s potvrzenim kliknutim.
- Donate firme s potvrzenim kliknutim.
- Sprava firem (create/remove/list/description/give).
- Profilovy popis hrace.
- Statistiky prijatych donatu (celkem, den, tyden, mesic, rok).
- Strankovani historie (nastavitelne v configu).
- Hover informace u zaznamu hrace (`kolik donor celkem poslal`).
- Ukladani dat do MySQL a automaticke vytvoreni tabulek.
- Offline notifikace: pokud hrac obdrzi donate behem offline, po pripojeni dostane souhrn pro osobni ucet i firmy.
- Podpora barevnych kodu `&` i hex `{#RRGGBB}`.

## Instalace
1. Nahraj plugin jar do `plugins/MaxDonates`.
2. Uprav `config.yml` (MySQL udaje).
3. Uprav texty v `lang_cz.yml` (prefix, help, hlasky).
4. Ujisti se, ze server ma Vault a economy provider (EssentialsX apod.).
5. Restart serveru.

## Prikazy
- `/maxdonates donate <hrac> <castka> [confirm]`
- `/maxdonates cdonate <firma> <castka> [confirm]`
- `/maxdonates company create <nazev>`
- `/maxdonates company remove <nazev> [confirm]`
- `/maxdonates company list [hrac]`
- `/maxdonates company description <firma> edit <text>`
- `/maxdonates company description <firma> remove`
- `/maxdonates company give <firma> <target> [confirm]`
- `/maxdonates description edit <text>`
- `/maxdonates description remove`
- `/maxdonates recived [hrac|stranka]`
- `/maxdonates crecived <firma> [stranka]`

## Permission nodes
- `maxdonates.donate.player`
- `maxdonates.donate.company`
- `maxdonates.company.create`
- `maxdonates.company.create.<N>` (napr. `maxdonates.company.create.5`)
- `maxdonates.company.create.*`
- `maxdonates.company.remove`
- `maxdonates.company.list.own`
- `maxdonates.company.list.other`
- `maxdonates.company.description.edit`
- `maxdonates.company.description.remove`
- `maxdonates.company.give`
- `maxdonates.description.edit`
- `maxdonates.description.remove`
- `maxdonates.lookup.player.own`
- `maxdonates.lookup.player.other`
- `maxdonates.lookup.company.own`
- `maxdonates.lookup.company.other`

## Poznamky
- Alias `compan` je podporeny stejne jako `company`.
- Help seznam i prefix jsou v `lang_cz.yml`.
