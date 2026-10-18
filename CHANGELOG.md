# Changelog

## 2.2.0
- Minions now work exactly like on SkyBlock: fill the whole area, break one block, put a block back in the same
  spot, break another one, repeat. The old `work-mode` option (FILL / ALTERNATE) is gone.
- No more ore / coal blocks on minion heads. Heads come from `config.yml`, then `skins.yml`, then the built-in
  Cobblestone set; if nothing is found the minion wears a helmet dyed in its colour.
- `tools/import_hypixel_skins.py` downloads head textures for every tier into `skins.yml`.
- Working Minion Skin slot (any custom player head).
- Compactor / Super Compactor 3000 chain: resource -> Enchanted -> Enchanted Block (Lapis, Coal, Iron, Gold,
  Diamond, Redstone, Emerald, Quartz). Enchanted Blocks can be crafted and count towards tier upgrades.
- Lapis Minion drops 4-8 Lapis Lazuli.
- Menu shows which upgrades work on a minion (Super Compactor / Compactor / Auto Smelter) and rejects the ones that don't.
- `/minions reload` also reloads skins; `/minions give <player> enchantedblock_<minion>`.

## 2.1.0
- Initial public version: 23 minions, 12 tiers, fuels, upgrades, storage chests, automated shipping, offline work.
