# Client Rendering Audit

Sodium 0.6.13 supplies chunk meshing/culling with automatic builder threads; Iris 1.8.12 supplies shaders; Flywheel bundled with Create handles instanced Create rendering with automatic workers. ImmediatelyFast 1.6.10 batches GUI/immediate rendering. Entity Culling 1.8.0 traces to 128 blocks but explicitly lists Create contraption entity types in exclusions. EMF/ETF, 3D Skin Layers, Sodium Dynamic Lights, Sound Physics, Distant Horizons, Xaero maps, particles, animations (GeckoLib/AzureLib/player-animation-lib), and large TACZ/audio assets add client CPU/GPU/memory/I/O pressure.

DH is configured for 16 workers and 256-chunk LOD radius; its debug `enableRendering=false` value requires contextual verification rather than assuming the entire mod is inactive. These systems can reduce FPS or increase client frametime while server TPS remains healthy. Shader choice and GPU/driver data were not available. **Confidence:** high installed/config, medium interaction. **Validation:** GPU utilization/VRAM, CPU frametime, chunk-build queues and no-shader/shader controlled comparisons, without treating the result as server optimization.

