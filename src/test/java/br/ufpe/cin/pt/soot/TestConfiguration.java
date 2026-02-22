package br.ufpe.cin.pt.soot;

public class TestConfiguration {
    String entryClass;
    String entryMethod;
    String targetClass;
    String targetMethod;
    String targetType;  // fully-qualified type name of the locals to check (e.g. Point)
    CallGraphAlgorithm algorithm;
    String local1;
    String local2;

    public TestConfiguration(String entryClass, String entryMethod, String targetClass, String targetMethod, String local1, String local2, String targetType) {
        this.entryClass = entryClass;
        this.entryMethod = entryMethod;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.local1 = local1;
        this.local2 = local2;
        this.targetType = targetType;
    }

    public TestConfiguration setCallGraph(CallGraphAlgorithm cg) {
        this.algorithm = cg;
        return this;
    }
}