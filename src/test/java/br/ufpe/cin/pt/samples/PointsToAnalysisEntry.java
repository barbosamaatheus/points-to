package br.ufpe.cin.pt.samples;

/**
 * Entry point for Soot whole-program analysis. Invokes the method under analysis
 * so it is reachable in the call graph.
 */
public class PointsToAnalysisEntry {
    public static void main(String[] args) {
        PointTest test = new PointTest();
        test.testPoints();
    }
}
