package br.ufpe.cin.pt.soot;

class TestConfiguration {
    String entryClass;
    String entryMethod;
    String targetClass;
    String targetMethod;
    String algorithm;
    String local1;
    String local2;

    public TestConfiguration(String entryClass, String entryMethod, String targetClass, String targetMethod, String local1, String local2) {
        this.entryClass = entryClass;
        this.entryMethod = entryMethod;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.local1 = local1;
        this.local2 = local2;
    }

    public TestConfiguration setCallGraph(String callGraph) {
        this.algorithm = callGraph;
        return this;
    }
}