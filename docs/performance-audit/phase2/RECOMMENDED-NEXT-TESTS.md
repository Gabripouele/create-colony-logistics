# Required Next Tests

1. Capture a five-minute existing-terrain route with one player and normal DH, preserving Spark and client telemetry.
2. Repeat at the same speed/duration in verified ungenerated Overworld terrain.
3. Repeat new terrain at reduced speed to distinguish throughput saturation from per-chunk spikes.
4. Repeat the new route pattern with DH processing minimized using a temporary client profile.
5. Only after these comparisons, run structure attribution or reduced-background tests targeted at the observed stack.

No optimization change is recommended yet. The idle result proves significant background load but does not identify the exploration bottleneck.
