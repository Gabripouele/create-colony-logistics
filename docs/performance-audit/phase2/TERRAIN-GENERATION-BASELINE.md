# Terrain Generation Runtime Baseline

Status: **in progress; idle capture complete, controlled traversal captures pending**.

The production server was not launched or modified. A 2.61 GiB stopped-world copy was created at `run-performance-audit/world-qa`, isolated on port 25566. Configuration and mod hashes are preserved under `performance-audit-results/raw/configs`.

The first QA idle capture maintained 20.00 TPS with 27.33 ms mean, 31.09 ms p95, and 48.69 ms maximum MSPT over Spark's last-minute window. This leaves measurable but limited headroom before the 50 ms tick budget. Zero players were connected, yet 1,280 entities were loaded, including 219 MineColonies citizens, 37 stationary Create contraptions, seven moving contraptions, five carriage contraptions, 126 camera image frames, and 107 item entities. Terrain conclusions cannot be drawn from this background-only sample.

Spark: https://spark.lucko.me/vj1VaeFAFb

## Current Classification

| Bottleneck | Result | Evidence |
|---|---|---|
| Background simulation contention | Verified material baseline | 27.33 ms mean idle MSPT and loaded entity inventory |
| Garbage collection | Not demonstrated as idle bottleneck | G1; no GC pause data reported in capture metadata |
| Region-file save latency | Not demonstrated in idle shutdown | all dimensions saved in about 2.7 seconds |
| Shutdown executor leak | Verified, outside terrain scope | parked non-daemon `pool-34-thread-1`; raw thread dump |
| Server/client terrain stages | Pending | existing/new terrain routes not yet run |

