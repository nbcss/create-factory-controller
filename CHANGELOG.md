## 1.1.1

Changes:
- Added recipe output tooltip for production order.

Localization:
- Added French translation, thanks to @totof-prod

Bugfixes:
- Fixed compatibilities issue with older version of fluid transportation mods.
- Fixed item rendering brightness was incorrect in certain cases.

## 1.1.0

Features:
- Network can set custom name and item icon now.
- Added toast notification for order status change.
- Ignore data ingredient is now allowed for batch crafting & large crafting, but each slot cannot have different data item.
- Added "Custom Arrangement" mode in gauge; you can customize which item needs to be in which slot, and packager can only unpackage items to correct slot in target inventory.
- Added "Request Multiplier" feature in gauge: by set the maximum request multiplier value, gauge would attempt to combine ingredients from multiple requests into one request if stock allows.
- Added per gauge "Request Interval" setting, player could also visualize gauge request progress in RecipeSettingScreen.
- Added blueprint system to allow save components & connections to file.
- Added tooltip warning and controller indicator for unloaded stock links.

Changes:
- Maximum crafting batch size is 64 now, regardless recipe output count.
- Gauge ingredient slot need to use Shift-Click to remove now.

Optimization:
- Dashboard sync use much less bandwidth data now.

Bugfixes:
- Fixed gauge connection flow animation won't play without made at least one request attempt.
- Fixed a compatibility issue which could cause connections to render incorrectly.
- Fixed gauge request timer do not get reset when redstone powered state changes.
- Fixed click suggested address in Recipe Settings screen could activate the count box input below.
- Fixed controller screen view could move by WASD keys when typing in JEI/EMI search box.
- Fixed drag view while having selected components would clear the selections.
- Fixed FluidLogistics compat does not properly work in 1.20 port.

## 1.0.0
IMPORTANT: this version changed Factory Controller block data structure, once upgraded to this version, your factory controller cannot downgrade to previous mod version, so backup before upgrade.

Features:
- Allow drag-to-select components (hold ctrl by default) and perform batch relocate & remove
- Electron Tube is now a valid component in Factory Controller. It acts as "Logical Tube" component, which can perform redstone logical operations (1 tick delay)
- Display Link can now read pending orders from Factory Controller
- Added new keybinding (default F) to change component operation mode, works on Redstone Link, Logical Tube and Connection
- Added new keybindings to relocate component (default Q) and start connection (default C)
- If gauge input/output slot item > 1 stack, stack count label will appear in slot tooltip
- Relocate/Placing components now render component ghost
- Connecting new component now render connection ghost; you can also change connection ghost arrow mode before connect
- Connections are now selectable, and allow change bend mode / reverse direction / delete specific connection
- Gauge can set request limit to avoid total number of pending requests overload the chain network.
- Added alternative passive request strategy "Full Passive Demand Strategy" (experimental, need to enable in configuration).
- Added compact font for input/output count rendering in recipe settings screen (configurable)
- JEI/EMI support for drag-to-set empty gauge item while in Controller Screen
- Create Production Task icon in Stock Keeper GUI will now include associated gauge ingredients and target address in tooltip.

Changes:
- Request limit for unit item & stack are increased to 1000
- Existing keybinding "Interact" (R) changed to "cycle arrow mode"
- Default drag view key is left mouse button now
- Gauge recipe output slot now supports up to 9 stacks of expecting product
- Gauge timeout will now only apply to requests created by this gauge.

Optimization:
- Controller GUI will not render out-of-viewport components now

Compatibility:
- Allow Fluid Gauge from Repackaged mod to be used in Factory Controller

Bugfixes:
- Fixed fluid cannot properly work to create production order
- Fixed connection arrow tip does not have flow animation
- Fixed place configured controller block will keep dirty data from item

## 0.2.1
Features:
- Allow to type number to set gauge request amount
- Allow to change controller background texture (client setting)
- When Controller block breaks, it will now store the virtual board data in dropped item (configurable)
- Provide material list to Stock Keeper can now create production order for missing items (configurable)
- Controller GUI saves last view position & zoom, and restore when re-open controller GUI.
- Redstone Link is now an allowed component in Factory Controller
- Added Ignore Data setting to gauge:
  - Once enabled, the gauge monitor all items for given type, ignore NBT data.
  - The setting cannot be changed after output item been decided.
  - When use "Ignore Data" item as ingredient of a gauge, the gauge could use any item as input (ignore data).
  - Batch Crafting and Large Crafting Grid feature will be DISABLED when ingredient contains ignore data item.
  - Warning: the feature does have performance cost, so only enable Ignore Data when needed.

Changes:
- Default background texture is plain_cardboard now
- Increase the hardness of Factory Controller block
- Add Summary to Factory Controller item
- Fluid System Compatibility: Bucket Unit can set request up to 1000B fluid now
- Deployer API is now a soft-dependency (but recommended)
- Updated gauge bulb texture

Bugfixes:
- Fixed orderable items are not show up in Stock Keeper immediately when open GUI
- Fixed network selector tooltip list items incorrectly when network count > 7
- Fixed Large Crafting may not work when missing dependency

## 0.1.1
- Introduced Follow Demand and Order mode in gauge
- Introduced Production Order system

## 0.1.0
- Initial Release