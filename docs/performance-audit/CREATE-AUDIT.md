# Create Performance Audit

Create 6.0.10 and add-ons contribute kinetic networks, belts, chain conveyors, funnels/chutes/depots, arms, item/fluid networks, contraptions, trains/schedules/signals/stations, displays/redstone/stock links, package logistics and processing block entities. Costs scale with connected graph size, ticking block entities, transported stacks, collision shapes, schedule/train count, sync watchers and chunk boundaries.

Verified non-default ceilings are `maxBeltLength=40` (default 20), `maxChainConveyorLength=1000` (32), and `maxBlocksMoved=999999999` (2048). Validation remains every 60 ticks; tickrate sync is every 20 ticks. Power Loader Brass variants enable radius-2 train/contraption loading. Create Colony Logistics bridges package/warehouse/request state, so bursty inventory scans and packet payloads are a profiling target. **Risk:** high potential ceiling, conditional actual cost. **Evidence:** installed configs/JAR metadata and local source. **Validation:** idle/active kinetic graphs, transfer bursts, large contraptions, train network, packager/requester and colony-linked warehouse scenarios.

