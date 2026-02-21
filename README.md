# Points-to and Call Graph

This project explores **the impact of the call graph on points-to relationships** using [Soot](https://soot-oss.github.io/soot/) (version 3.3.0). We focus on a single test scenario, `PointTest.testPoints()`, and compare how different call-graph configurations affect the points-to information that Soot produces.

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

## Call Graph Configurations (Driver)

Call graph (and, when used, points-to) is configured in the **`Driver`** class (`br.ufpe.cin.pt.soot.Driver`) via `setCallGraph(algorithm)`:

| Algorithm | Configuration |
|-----------|----------------|
| **CHA**  | `cg.cha` enabled, `cg.spark` **disabled**. Call graph is built only with Class Hierarchy Analysis; Spark is excluded from execution. |
| **SPARK** | `cg.spark` enabled, `on-fly-cg:true`. Spark builds the call graph on the fly with its points-to analysis. |
| **RTA**   | `cg.spark` enabled, `rta:true`, `on-fly-cg:false`. Rapid Type Analysis style. |
| **VTA**   | `cg.spark` enabled, `vta:true`, `on-fly-cg:false`. Variable Type Analysis style. |

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

The **`PointsToTestSuite`** (`br.ufpe.cin.pt.soot.PointsToTestSuite`) has two goals:

1. **Document observed behaviour** – For each call-graph configuration (CHA, SPARK, RTA, VTA) and each pair of locals, the tests encode what the current Soot setup reports (no evidence of alias vs suggests alias). This gives a clear, executable record of how the call graph affects points-to in this scenario.
2. **Regression and exploration** – Tests that pass act as regression tests; tests that are `@Ignore`d document surprising or not-yet-understood results (or environment issues) without failing the build.

Each test runs the **Driver** with a **`TestConfiguration`** that specifies:

- Entry class and method (e.g. `PointsToAnalysisEntry.main`)
- Target class and method (`PointTest.testPoints()`)
- The two locals to check for may-alias
- The call graph algorithm (CHA, SPARK, RTA, VTA)

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

So the suite both validates Spark on this scenario and keeps a written record of CHA, RTA, and VTA behaviour (and known issues) via ignored tests.

---

### Running the tests

```bash
mvn test
```

---

## Project Layout

- **`samples`** – Scenario code: `PointTest`, `Point`, `PointsToAnalysisEntry`.
- **`soot`** – Soot wiring and tests: `Driver`, `AliasTransformer`, `TestConfiguration`, `PointsToTestSuite`.

---

## Requirements

- Java 8 (Soot 3.3.0 expects `rt.jar` on the classpath when resolving the JDK).
- Maven 3.x.
