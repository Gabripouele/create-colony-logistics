# Disk and Save Analysis

H: is reported by Spark as 1.8 TB total and 1.6 TB used (86%); storage media and filesystem remain unverified. Production uses synchronous chunk writes and deflate region compression. Smoothchunk delays saves 300 seconds, caps unloads at 20/tick and disables protochunk saves.

The idle QA shutdown logged Overworld save start at 21:24:31.036 and all dimensions saved at 21:24:32.450, about 1.4 seconds for chunk-save logging and 2.7 seconds from `Saving worlds`. The JVM then remained alive due an unrelated parked executor. This does not demonstrate a disk bottleneck. Per-volume latency, queue depth and read/write throughput are pending traversal instrumentation.

