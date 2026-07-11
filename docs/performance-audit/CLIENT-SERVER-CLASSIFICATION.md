# Client and Server Classification

The authoritative row-level classification is in [MOD-INVENTORY.md](MOD-INVENTORY.md). Metadata was preferred, but NeoForge metadata usually declares dependencies rather than a reliable physical-side prohibition.

## Exclude From Server Performance Conclusions

High-confidence client-only systems include Sodium, Sodium Extra/options/dynamic lights, Iris, Reese's Sodium Options, ImmediatelyFast, Entity Culling, Entity Model Features, Entity Texture Features, 3D Skin Layers, AppleSkin, Borderless Window, Controlling, Mouse Tweaks, Freecam, OK Zoomer, EMI/JEI presentation, Jade presentation, Sound Physics Remastered, and Xaero render/UI components. Distant Horizons has client-dominant rendering plus optional generation/network/storage behavior, so it is not treated as purely visual without verifying the server deployment.

The disabled ArmorPoser, Character Selector, Chunk Sending, and Packet Fixer artifacts are inactive. Libraries should be retained only when required by server-relevant dependants. **Confidence:** high for obvious render/UI mods; medium elsewhere. **Validation:** construct the server set from authoritative server files or explicit metadata, never this heuristic list alone.

