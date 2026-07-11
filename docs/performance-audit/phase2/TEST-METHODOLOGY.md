# Test Methodology

QA root: local performance-audit runtime folder. Results root: sibling `performance-audit-results`. QA uses exact copied mods/config/world, port 25566, `level-name=world-qa`, separate logs, and one timestamped raw directory per run.

For each traversal, use one player, Overworld, identical client, view/simulation distance 10, travel method, speed, duration and route length. Record coordinates every run. Existing terrain must be verified from region coverage; new terrain must begin beyond existing region extents in the copied world. Start Spark only after login/warmup, capture the route alone, then stop and preserve its URL/raw JSON.

Client metrics require an external overlay/recorder capable of FPS, 1% lows, frametime, CPU/GPU/VRAM, RAM, disk and network. Server process samples are one second. Timestamps must use America/Toronto ISO time. DH comparisons use temporary profiles or restored config copies, never the live client config.

The idle harness currently documents a shutdown hang after worlds save; future harness runs should allow five minutes then capture `jcmd Thread.print` and terminate only the QA JVM if the same parked executor persists.
