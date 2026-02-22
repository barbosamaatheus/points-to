# Points-to and Call Graph

This project explores **the impact of the call graph on points-to relationships** using [Soot](https://soot-oss.github.io/soot/) 4 (org.soot-oss:soot) and [Qilin](https://github.com/rbonifacio/QilinPTA) (our fork of [QilinPTA/Qilin](https://github.com/QilinPTA/Qilin), where we downgrade the build from newer Java versions to Java 8). It focuses on a single test scenario, `PointTest.testPoints()`, and compares how different call-graph and points-to configurations affect the resulting alias information.

A notable finding: for some imprecise algorithms (e.g. **CHA**), Soot still populates points-to information **even when Spark is disabled** in the configuration. That is, the call graph alone (CHA) drives a form of points-to result that we can query.

---

## Scenario: `PointTest.testPoints()`

The scenario under analysis is the following method in `br.ufpe.cin.pt.samples.PointTest`:

```java
public void testPoints() {
  Point point1 = new Point(1, 2);
  Point point2 = new Point(3, 4);
  Point point3 = point2;
  // ...
  assertEquals(5.0, point1.distance(point2), 0.000001);
  assertEquals(5.0, point3.distance(point2), 0.000001);
}
```

- **point1** and **point2** point to different allocations → no alias.
- **point2** and **point3** refer to the same object (`point3 = point2`) → they alias.

The analysis entry point is `PointsToAnalysisEntry.main()`, which invokes `new PointTest().testPoints()` so that `testPoints()` is reachable in the call graph.

---

## Call Graph and PTA Configurations (Driver)

Call graph and points-to analysis are configured in the **`Driver`** class (`br.ufpe.cin.pt.soot.Driver`) via `setCallGraph(algorithm)`:

| Algorithm | Configuration |
|-----------|----------------|
| **CHA**  | `cg.cha` enabled, `cg.spark` **disabled**. Call graph is built only with Class Hierarchy Analysis; Spark is excluded from execution. |
| **SPARK** | `cg.spark` enabled, `on-fly-cg:true`. Spark builds the call graph on the fly with its points-to analysis. |
| **RTA**   | `cg.spark` enabled, `rta:true`, `on-fly-cg:false`. Rapid Type Analysis style. |
| **VTA**   | `cg.spark` enabled, `vta:true`, `on-fly-cg:false`. Variable Type Analysis style. |
| **QILIN_INSENS** | Soot’s CHA and Spark disabled; Qilin runs its context-insensitive PTA and builds the call graph. |

Relevant snippet from `Driver.setCallGraph()`:

```java
case "CHA":
    Options.v().setPhaseOption("cg.cha", "enabled:true");
    Options.v().setPhaseOption("cg.spark", "enabled:false");
    break;
case "SPARK":
    Options.v().setPhaseOption("cg.spark", "enabled:true");
    Options.v().setPhaseOption("cg.spark", "on-fly-cg:true");
    break;
// RTA and VTA use cg.spark with rta:true / vta:true and on-fly-cg:false
```

So for **CHA**, Spark is explicitly turned off; for **SPARK**, **RTA**, and **VTA**, Spark is used with different options.

---

## Test Suite: Purpose and Structure

The **`PointsToTestSuite`** (`br.ufpe.cin.pt.testsuite.PointsToTestSuite`) has two goals:

1. **Document observed behaviour** – For each call-graph configuration (CHA, SPARK, RTA, VTA) and each pair of locals, the tests encode what the current Soot setup reports (no evidence of alias vs suggests alias). This gives a clear, executable record of how the call graph affects points-to in this scenario.
2. **Regression and exploration** – Tests that pass act as regression tests; tests that are `@Ignore`d document surprising or not-yet-understood results (or environment issues) without failing the build.

Each test runs the **Driver** with a **`TestConfiguration`** that specifies:

- Entry class and method (e.g. `PointsToAnalysisEntry.main`)
- Target class and method (`PointTest.testPoints()`)
- The two locals to check for may-alias
- The call graph / PTA algorithm (CHA, SPARK, RTA, VTA, QILIN_INSENS)

There are two configurations:

| Configuration | Locals | Relationship in code |
|---------------|--------|------------------------|
| **configTestAliasForPoint1Point2** | point1, point2 | Different allocations → no alias |
| **configTestAliasForPoint2Point3** | point2, point3 | `point3 = point2` → they alias |

Results are compared against **`AliasTransformer.Result`**:

- **`PTA_NO_EVIDENCE_OF_ALIAS`** – points-to sets do not overlap.
- **`PTA_SUGGESTS_ALIAS`** – non-empty intersection (analysis suggests may-alias).
- **`PTA_UNAVAILABLE`** – points-to information could not be used (e.g. not `PointsToSetInternal`).

---

### Why some tests are `@Ignore`d

Several tests are marked **`@Ignore`** so that the suite still documents expectations and current behaviour without failing the build.

| Test | Reason ignored |
|------|----------------|
| **testPointsToWithCHAP1P2** | **Unexpected result.** The test expects `PTA_NO_EVIDENCE_OF_ALIAS` for point1 vs point2 when using CHA (with Spark disabled). In practice, CHA still populates points-to and reports that they may alias. The test is ignored to record this surprising behaviour (points-to populated even without Spark) until the cause is understood or accepted. |
| **testPointsToWithCHAP2P3** | **Unexpected result.** Same situation with point2 vs point3 under CHA: the test expected `PTA_NO_EVIDENCE_OF_ALIAS`, but CHA reports may-alias. (Note: in the code, point2 and point3 do alias; the ignore documents that CHA’s result differs from the test’s original expectation.) |
| **testPointsToWithRTAP1P2**, **testPointsToWithRTAP2P3** | **Memory issues.** RTA in this setup triggers high memory use or timeouts. Ignored with the intent to fix the configuration or environment later. |
| **testPointsToWithVTAP1P3**, **testPointsToWithVTAP2P3** | **Unexpected result.** The tests expect `PTA_NO_EVIDENCE_OF_ALIAS` for the chosen pairs, but VTA (with Spark) reports may-alias. Ignored to document the mismatch while the behaviour is investigated. |

**Tests that run (no `@Ignore`):**

- **testPointsToWithSparkP1P2** – Spark reports no evidence of alias for point1 vs point2 (as expected).
- **testPointsToWithSparkP2P3** – Spark reports may-alias for point2 vs point3 (as expected).
- **testPointsToQilinINSENSP1P2** – Qilin context-insensitive PTA reports no alias for point1 vs point2 (`PointsToTestSuiteQilinP1P2Test`, runs in its own JVM).
- **testPointsToQilinINSENSP2P3** – Qilin context-insensitive PTA reports may-alias for point2 vs point3 (`PointsToTestSuiteQilinP2P3Test`, runs in its own JVM).

The two Qilin tests live in separate test classes so that each runs in a fresh JVM (Surefire `reuseForks=false`), avoiding static state issues between runs. The suite validates Spark and Qilin on this scenario and keeps a written record of CHA, RTA, and VTA behaviour (and known issues) via ignored tests.

---

### Running the tests

**Use Java 8 to run tests.** Soot/ASM do not support Java 21 bytecode; on Java 21 you will see `Unsupported class file major version 65`. Switch to JDK 8 and run:

```bash
export JAVA_HOME=/path/to/jdk8   # or use jenv/sdkman to select Java 8
mvn clean test
```

`mvn clean test-compile` first ensures test classes (including `CallGraphAlgorithm` and `PointTest`) are compiled by Maven; without a clean build, stale IDE class files can cause “CallGraphAlgorithm cannot be resolved” at test time.

---

## Project Layout

All packages live under `br.ufpe.cin.pt` (test source root: `src/test/java/`).

- **`samples`** – Scenario code: `PointTest`, `Point`, `PointsToAnalysisEntry`.
- **`soot`** – Soot/Qilin wiring: `Driver`, `AliasTransformer`, `TestConfiguration`, and `pta` (SootPTA, QilinPTA, PTASingleton).
- **`testsuite`** – Test suites (same level as `soot`): `PointsToTestSuite` (Spark, CHA, RTA, VTA), `PointsToTestSuiteQilinP1P2Test`, `PointsToTestSuiteQilinP2P3Test`.

---

## Requirements

- **Java 8–11** recommended (Soot 4.x may support newer JDKs; on Java 21+ you may see “Unsupported class file major version 65” with older Soot 4.3—use 4.4.1 or newer). For JDK 8, `rt.jar` is expected on the classpath when resolving the JDK.
- **Soot 4** (org.soot-oss:soot) is required; the [Qilin](https://github.com/rbonifacio/QilinPTA) dependency (our fork, built for Java 8) is built against Soot 4’s API.
- **Maven 3.x.**
