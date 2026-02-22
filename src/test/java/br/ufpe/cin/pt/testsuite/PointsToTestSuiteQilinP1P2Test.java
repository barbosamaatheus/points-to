package br.ufpe.cin.pt.testsuite;

import static org.junit.Assert.assertEquals;

import br.ufpe.cin.pt.soot.AliasTransformer;
import br.ufpe.cin.pt.soot.CallGraphAlgorithm;
import br.ufpe.cin.pt.soot.Driver;
import br.ufpe.cin.pt.soot.TestConfiguration;
import org.junit.Test;

/**
 * Runs in a separate JVM (own test class) so Qilin/Soot static state does not affect other Qilin tests.
 */
public class PointsToTestSuiteQilinP1P2Test {

    private final TestConfiguration config = new TestConfiguration(
            "br.ufpe.cin.pt.samples.PointsToAnalysisEntry", "main",
            "br.ufpe.cin.pt.samples.PointTest", "testPoints",
            "point1", "point2", "br.ufpe.cin.pt.samples.Point");

    @Test
    public void testPointsToQilinINSENSP1P2() {
        assertEquals(
                "Qilin context-insensitive PTA should report NO_ALIAS for point1/point2.",
                AliasTransformer.Result.PTA_NO_EVIDENCE_OF_ALIAS,
                new Driver().runAnalysis(config.setCallGraph(CallGraphAlgorithm.QILIN_INSENS)));
    }
}
