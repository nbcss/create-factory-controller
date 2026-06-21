## 0.2.0
Features:
- Allow to type number to set gauge request amount
- Allow to change controller background texture (client setting)
- When Controller block breaks, it will now store the virtual board data in dropped item (configurable)
- Provide material list to Stock Keeper can now create production order for missing items (configurable)
- Redstone Link is now an allowed component in Factory Controller
- Added Ignore Data setting to gauge:
  - Once enabled, the gauge monitor all items for given type, ignore NBT data.
  - The setting cannot be changed after output item been decided.
  - When use "Ignore Data" item as ingredient of a gauge, the gauge could use any item as input (ignore data).
  - Batch Crafting and Large Crafting Grid feature will be DISABLED when ingredient contains ignore data item.
  - Warning: the feature does have performance cost, so only enable Ignore Data when needed.

Changes:
- Default background texture is cardboard_block_side now
- Increase the hardness of Factory Controller block
- Add Summary to Factory Controller item
- Fluid System Compatibility: Bucket Unit can set request up to 1000B fluid now
- Updated gauge bulb texture

Bugfixes:
- Fixed orderable items are not show up in Stock Keeper immediately when open GUI
- Fixed network selector tooltip list items incorrectly when network count > 7

## 0.1.1
- Introduced Follow Demand and Order mode in gauge
- Introduced Production Order system

## 0.1.0
- Initial Release