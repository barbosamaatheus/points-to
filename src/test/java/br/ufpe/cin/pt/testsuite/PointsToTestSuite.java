package br.ufpe.cin.pt.testsuite;

import static org.junit.Assert.assertEquals;

import br.ufpe.cin.pt.samples.PointTest;
import br.ufpe.cin.pt.soot.AliasTransformer;
import br.ufpe.cin.pt.soot.CallGraphAlgorithm;
import br.ufpe.cin.pt.soot.Driver;
import br.ufpe.cin.pt.soot.TestConfiguration;
import org.junit.Ignore;
import org.junit.Test;


/**
 * Tests that use Soot to analyze {@link PointTest#testPointsFromSources()} with different
 * call graph / points-to algorithms (CHA, RTA, VTA, Spark) and check whether
 * <code>p1</code> and <code>p2</code> may point to the same objects.
 * <p>
 * In testPointsFromSources(): p1 from PointSourceA.getPoint(), p2 from PointSourceB.getPoint().
 * Spark (precise): NO_ALIAS. CHA (conservative): MAY_ALIAS.
 */
public class PointsToTestSuite {

    /* A test configuration for testing alias between point1 and point2.
    *  This configuration must fail for SPARK.
    */
    private final TestConfiguration configTestAliasForPoint1Point2 = new TestConfiguration("br.ufpe.cin.pt.samples.PointsToAnalysisEntry", "main", "br.ufpe.cin.pt.samples.PointTest", "testPoints", "point1", "point2", "br.ufpe.cin.pt.samples.Point");

    private final TestConfiguration configTestAliasForPoint2Point3 = new TestConfiguration("br.ufpe.cin.pt.samples.PointsToAnalysisEntry", "main", "br.ufpe.cin.pt.samples.PointTest", "testPoints", "point2", "point3", "br.ufpe.cin.pt.samples.Point");

    @Ignore("Unexpected result [...]")
    public void testPointsToWithCHAP1P2() {
        assertEquals(
                "I was expecting that CHA (without SPARK) would report NO_ALIAS for p1/p2, but it is reporting that they may alias.",
                AliasTransformer.Result.PTA_NO_EVIDENCE_OF_ALIAS,
                new Driver().runAnalysis(configTestAliasForPoint1Point2.setCallGraph(CallGraphAlgorithm.SOOT_CHA)));
    }

    @Ignore("Unexpected result [...]")
    public void testPointsToWithCHAP2P3() {
        assertEquals(
            "I was expecting that CHA (without SPARK) would report NO_ALIAS for p1/p2, but it is reporting that they may alias.",
            AliasTransformer.Result.PTA_NO_EVIDENCE_OF_ALIAS,
            new Driver().runAnalysis(configTestAliasForPoint2Point3.setCallGraph(CallGraphAlgorithm.SOOT_CHA)));
    }

    @Test
    public void testPointsToWithSparkP1P2() {
        assertEquals(
                "Spark (precise) should report NO_ALIAS for p1/p2",
                AliasTransformer.Result.PTA_NO_EVIDENCE_OF_ALIAS,
                new Driver().runAnalysis(configTestAliasForPoint1Point2.setCallGraph(CallGraphAlgorithm.SOOT_SPARK)));
    }

    @Test
    public void testPointsToWithSparkP2P3() {
        assertEquals(
                "Spark (precise) should report MAY_ALIAS for p2/p3",
                AliasTransformer.Result.PTA_SUGGESTS_ALIAS,
                new Driver().runAnalysis(configTestAliasForPoint2Point3.setCallGraph(CallGraphAlgorithm.SOOT_SPARK)));
    }

    @Ignore("Memory issues [...]. I will fix this later.")
    public void testPointsToWithRTAP1P2() {
        assertEquals(
                "RTA should report NO_ALIAS for p1/p2",
                AliasTransformer.Result.PTA_NO_EVIDENCE_OF_ALIAS,
                new Driver().runAnalysis(configTestAliasForPoint1Point2.setCallGraph(CallGraphAlgorithm.SOOT_RTA)));
    }

    @Ignore("Memory issues [...]. I will fix this later.")
    public void testPointsToWithRTAP2P3() {
        assertEquals(
                "RTA should report NO_ALIAS for p2/p3",
                AliasTransformer.Result.PTA_NO_EVIDENCE_OF_ALIAS,
                new Driver().runAnalysis(configTestAliasForPoint2Point3.setCallGraph(CallGraphAlgorithm.SOOT_RTA)));
    }

    @Ignore("Unexpected result [...]")
    public void testPointsToWithVTAP1P3() {
        assertEquals(
                "I was expecting that VTA (even with SPARK) would report NO_ALIAS for p1/p2, but it is reporting that they may alias.",
                AliasTransformer.Result.PTA_NO_EVIDENCE_OF_ALIAS,
                new Driver().runAnalysis(configTestAliasForPoint1Point2.setCallGraph(CallGraphAlgorithm.SOOT_VTA)));
    }

    @Ignore("Unexpected result [...]")
    public void testPointsToWithVTAP2P3() {
        assertEquals(
                "I was expecting that VTA (even with SPARK) would report NO_ALIAS for p2/p2, but it is reporting that they may alias.",
                AliasTransformer.Result.PTA_NO_EVIDENCE_OF_ALIAS,
        new Driver().runAnalysis(configTestAliasForPoint2Point3.setCallGraph(CallGraphAlgorithm.SOOT_VTA)));
    }

    // Qilin tests in PointsToTestSuiteQilinP1P2Test and PointsToTestSuiteQilinP2P3Test (this package);
    // each runs in a fresh JVM (Surefire forks per test class), avoiding static state issues.
}
