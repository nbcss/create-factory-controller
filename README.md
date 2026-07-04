# Create: Factory Controller

Create: Factory Controller addon introduced a new block "Factory Controller" to allow you to easily manage your Factory Gauges in a GUI. Instead of having wall of Factory Gauges in the game world, the Controller block can keep the gauges in a virtual board for you (**Important: Factory Controller currently cannot manage existing factory gauge wall**). Factory Controller integrate and reimplement the dispatch logic of Factory Gauges, so the gauges managed by Factory Controller work more smoothly and received a number of powerful new features. The mod still in early development stage, any bug report and suggestion would be greatly appreciated.

## Features

- **Factory Controller**: Provides a centralized place for you to keep and managing up to 256 gauges (configurable).
- **Virtual Board**: You can move & zoom view in the virtual board like a 2D canvas; the board size also automatically adjusted by screen size.
- **Always Show Label**: You can see the count label of all gauges by toggling Always Show Label mode (default keybinding: Alt).
- **Connection Rotate**: You can cycle arrow mode on gauge or specific connection by press "R" key (could be changed in keybinding).
- **Network Selector**: A component in the GUI which keeps list of known networks on the virtual board, and you can scroll it to highlight the gauges which belong to chosen network.
- **Quick Tune**: When holding a gauge item on cursor and scroll the Network Selector component in GUI (or just Shift+Scroll), you can directly tune the gauge to a known network.
- **JEI/EMI Integration**: Allow to set gauge filter without to actually have the item in inventory.
- **Redstone Control**: When controller block is powered by redstone signal, all of stored gauges will temporarily stop sending new requests.
- **Redstone Link Support**: Redstone Link could be used in controller as well, it is as large as gauge in controller's grid.
- **Logical Operation**: By place "Electron Tube" in Factory Controller, it acts as a logical operator for your logistics control (constant 1 tick delay).
- **Improved Factory Gauges**: Gauges in virtual board (not include the gauges in game world) receive the following new features:
  - **Batch Crafting**: You can modify batch size in crafting mode gauge now. The gauge will attempt to send out multiple set of ingredients in **one single package**, and **re-packager** can split the package to multiple single-craft packages.
  - **Larger Crafting Grid**: You can now use larger than 3x3 recipes in the crafting mode gauge, as long as a single package can fit the crafting ingredients. Compatible with Batch Crafting feature.
  - **Larger Input/Output Limit**: Output slot and each connected ingredient no longer only able to have up to 64 items. You can continue to scroll & increase the item amount exceed 64.
  - **Ignore Item Data**: You can turn on "Ignore Data" when set item to gauge now. While this option is enabled, the gauge will monitor all items for specified type. Due to internal limitation, batch crafting and large crafting is disabled if any ingredient is in ignore data mode.
  - **Request Limit**: Prevent the gauge from send more requests when there are too many requests created by the gauge or heading to target address. 
  - **Follow Demand Mode**: You cannot manually set request amount of the Passive mode gauge. Instead, the request amount is automatically set when connected gauges need the item for their craft, so you do not need to keep intermediate items in stock. Chain multiple passive mode gauges is also supported.
  - **Follow Demand and Order**: Beyond automatically set request amount, the recipe of the gauge would be added to Stock Keeper GUI to allow manually initialize production orders. You can also track ongoing production order in Stock Keeper now.
- **Mod Compatibility**: The mod "replicate" a new gauge system, so it should not conflict with other gauge related mods, but also cannot receive other mod's modification unless implement dedicate support. The compatibility with following mods are supported:
  - **Create: FluidLogistic**: Gauge recipe allows fluid if installed, you can right-click the gauge with a fluid container item on cursor to set fluid filter.
  - **Repackaged**: Fluid Gauge is a supported component in Factory Controller.

## Dependencies

| Dependency             | Version  |
|------------------------|----------|
| Minecraft              | 1.21.1   |
| NeoForge               | 21.1.227 |
| Create                 | 6.0.10   |
| Deployer (Recommended) | 0.1.2    |

## Links

Crowdin Translation: https://crowdin.com/project/createfactorycontroller
Discord Support: https://discord.gg/yv77dsgeVx

## Credits

Graphics:

- FiveYellowMice

Translations:

- ru_ru: Makisk
- pl_pl: Makisk

Sounds:

- Winding Noise - Music Box by charcrone -- https://freesound.org/s/347035/ -- License: Creative Commons 0
- Pocket watch. Clicking open and closed, chain noise and picking up by JarredKarp -- https://freesound.org/s/490968/ -- License: Attribution NonCommercial 4.0

## AI Disclosure
The system architecture design and graphics assets are produced by human. AI is used for assist part of code implementations to accelerate development but closely reviewed by highly experienced Minecraft mod developer to ensure quality of the content. 
