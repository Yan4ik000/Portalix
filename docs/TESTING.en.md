[← Portalix](README.en.md) · [Русский](../tests/README.md) · **English ✓**

# Tests

The suite includes JUnit tests for mod logic and gameplay scenarios on Fabric and NeoForge. Gradle requires JDK 25. The matrix runner also requires Python 3. Run commands from the project root.

## Running tests

| Command | What it checks |
| --- | --- |
| `./gradlew :test` | Common and client logic without launching the game |
| `./gradlew :testServer` | Server GameTests on both loaders |
| `./gradlew :testClient` | Game client for the selected profile |
| `./gradlew :testProfile` | Logic, server and client for one profile |
| `./gradlew :testInfrastructure` | Launching tests and checking results |
| `./gradlew build` | Build, JUnit and contents of release JARs |

Set the profile with `-PtestProfile`; the default is `fabric-native-opengl`. List profiles with `./gradlew :listTestProfiles`.

Select an individual client scene with `-PclientTest` or a server group with `-PserverTest`:

```sh
./gradlew :testClient -PtestProfile=fabric-sodium-iris-opengl -PclientTest=portal_views
./gradlew :testServer -PserverTest=carry
```

Run all supported graphics profiles using the built JARs:

```sh
python3 tests/test_matrix.py --packaged
```

The matrix includes native, Sodium and Sodium + Iris on OpenGL, as well as native and Sodium on Vulkan, for both loaders. Iris profiles use Complementary Unbound with HIGH settings.

## Scenarios

Server groups: `placement`, `traversal`, `player_traversal`, `projectiles`, `combat`, `carry`, `chunks`, `content`. They check portal placement and reset, ordinary movement, projectile flight and impacts, combat, carrying objects, remote world loading, entity persistence, crafting and drops.

Client scenes:

| Scene | Actions |
| --- | --- |
| `camera` | First- and third-person views, movement through wall and floor portals |
| `portal_views` | View through a portal, orientation, transparent materials and parallax |
| `gun` | Equipping gun variants, primary and secondary fire, effects and reset |
| `player_traversal` | Walking, jumping and falling through portals |
| `carry` | Picking up, moving with, releasing and throwing an object |
| `combat` | Attacks and defense through a portal |
| `entity_traversal` | Movement of items, creatures and projectiles through a portal |
| `vehicles` | Vehicles and passengers |
| `world_updates` | Changes to blocks, lighting and entities on the other side |
| `audio` | Positional audio, pausing, portal closure and stopping |
| `resources` | Resource reload and display of a fresh remote block change |
| `lifecycle` | Disconnecting and rejoining the world |

Scenes use the real client, ordinary gameplay actions and native physics. Minecraft chooses the window size. Rendering checks use captures at the actual framebuffer resolution, which is recorded in the report. `-PtestDiagnostics=full` additionally saves a frame recording for manual review.

Results are stored in `tests/build/reports/regression`. Server reports are stored in the loaders' build directories. If a scene skips a case, JSON marks the run as `incomplete`, and both JSON and JUnit record the reason. Launch errors, failed scenes and skipped cases produce a nonzero exit code.

## Performance

A separate route measures frame times during firing, traversal to a remote area, a repeat visit and return traversal through the same pair:

```sh
./gradlew :benchmarkClient -PtestProfile=fabric-native-opengl
```

The report is saved in `tests/build/reports/benchmarks`. Compare results at the same framebuffer resolution. The default render distance is 16 chunks. Set `-PbenchmarkRenderDistance=32` to increase it to 32.
