# Create: Factory Controller

Create: Factory Controller addon introduced a new block "Factory Controller" to allow you to easily manage your Factory Gauges in a GUI. Instead of having wall of Factory Gauges in the game world, the Controller block can keep the gauges in a virtual board for you (**Important: Factory Controller currently cannot manage existing factory gauge wall**). Factory Controller integrate and reimplement the dispatch logic of Factory Gauges, so the gauges managed by Factory Controller work more smoothly and received a number of powerful new features. The mod still in early development stage, any bug report and suggestion would be greatly appreciated.

## Features

- **Factory Controller**: Provides a centralized place for you to keep and managing up to 256 gauges (configurable).
- **Virtual Board**: You can move & zoom view in the virtual board like a 2D canvas; the board size also automatically adjusted by screen size.
- **Always Show Label**: You can see the count label of all gauges by toggling Always Show Label mode (default keybinding: Alt).
- **Connection Rotate**: You can cycle arrow mode on gauge or specific connection by press "R" key (could be changed in keybinding).
- **Multi-Select Mode**: By hold Selection Mode key (Ctrl by default), you can drag to select multiple components, and perform batch move/delete.
- **Network Selector**: A component in the GUI which keeps list of known networks on the virtual board, and you can scroll it to highlight the gauges which belong to chosen network.
- **Quick Tune**: When holding a gauge item on cursor and scroll the Network Selector component in GUI (or just Shift+Scroll), you can directly tune the gauge to a known network.
- **JEI/EMI Integration**: Allow to set gauge filter without to actually have the item in inventory.
- **Display Link Integration**: Display Link can read pending orders from Factory Controller block.
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
  - **Create: FluidLogistic**: Gauge recipe allows fluid if installed, you can right-click the gauge with a fluid container item on cursor to set fluid filter, or drag from JEI/EMI.
  - **Repackaged**: Fluid Gauge is a supported component in Factory Controller.

## Dependencies

| Dependency             | Version  |
|------------------------|----------|
| Minecraft              | 1.21.1   |
| NeoForge               | 21.1.227 |
| Create                 | 6.0.10   |
| Deployer (Recommended) | 0.1.2    |

## Links

- Crowdin Translation: https://crowdin.com/project/createfactorycontroller
- Discord Support: https://discord.gg/yv77dsgeVx
- CurseForge: https://www.curseforge.com/minecraft/mc-mods/create-factory-controller
- Modrinth: https://modrinth.com/mod/create-factory-controller

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
<div class="spoiler">
<p>I never expect that I need to extend this part to more than 1 line, but I have to do this now, which makes me very disappointed.</p>
<p>In short, AI has been used in this project for some parts of code implementation, documentation, debugging and solution improvement, but I have reviewed all AI's involvement, and code architecture design, complex code implementations and all graphics assets are still produced by me and my friend. However, I won't consider include AI in the modding process means I produced "AI slop" mod or low-effort mod; AI slop mod won't put AI disclosure section in their mod description.</p>
<p>As a software engineer and an experienced mod developer, I don't trust AI's work, but AI is a new tool and a collaborator for me. For small and well-defined task, AI does extremely well; they can often produce functional code without my modification (but sometimes I still need to fix things after close review). Therefore, it save my time so I can focus on design the code architecture of larger features, doing testing, etc. In this case, AI is a tool to allow me to parallel the development process, which is important since I don't have plenty of time after work.</p>
<p>For large feature, AI's solution is problematic; it can only use for prototyping but also enable me to understand the underlying technical limitation before spend large amount of time in development. Meanwhile, AI is also used in design improvement; it reviews my code &amp; design and may sometime point me a new direction of solving problems, which could be more efficient and optimized. AI's suggestion may not always be true, but it is so valuable to get another technical point of view during the development, to make the mod more optimized and allow me to identify bugs early. In fact, I found that this collaboration pattern significantly reduced the bugs produced, compare to my previous mod; I received almost no bug reports during the first month of release, which is hard to believe when put this amount of features into one system.</p>
<p>In this case, AI has improved the quality of the mod and my productivity, instead of produced "AI slop mod". In fact, I am more worry about the growing "AI hate" in the community. If people keep getting pissed by any AI involve project regardless how AI get used, fewer developers would decide to disclose the use of AI, and player will have to facing more mods without labeled how AI get used.</p>
</div>
