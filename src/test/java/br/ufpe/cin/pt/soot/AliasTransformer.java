package br.ufpe.cin.pt.soot;

import java.util.Map;

import soot.Body;
import soot.Local;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SceneTransformer;
import soot.util.queue.QueueReader;

/**
 * SceneTransformer that runs in the wjtp pack. Looks for the target method in PointTest,
 * finds the relevant Point locals, queries the points-to analysis, and records whether they may alias.
 */
public final class AliasTransformer extends SceneTransformer {

    public enum Result {
        NOT_PROCESSED,
        PROBLEM_WITH_LOCALS_IDENTIFICATION,
        PTA_UNAVAILABLE,
        PTA_NO_EVIDENCE_OF_ALIAS,
        PTA_SUGGESTS_ALIAS
    }

    private String targetClass;
    private String targetMethod;
    private String local1;
    private String local2;
    private String type;
    private Result result = Result.NOT_PROCESSED;

    public AliasTransformer(String targetClass, String targetMethod, String local1, String local2, String type) {
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.local1 = local1;
        this.local2 = local2;
        this.type = type;
    }

    /** Result after the transformer has run; {@link Result#NOT_PROCESSED} until testPoints() is processed. */
    public Result getResult() {
        return result;
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        PointsToAnalysis pta = Scene.v().getPointsToAnalysis();

        QueueReader<soot.MethodOrMethodContext> reader = Scene.v().getReachableMethods().listener();
        while (reader.hasNext()) {
            SootMethod method = reader.next().method();
            Result r = processTargetMethod(method, pta);
            if (r != Result.NOT_PROCESSED) {
                result = r;
                return;
            }
        }
        // Fallback: with Spark on-fly-cg, testPoints() may not be in reachable set yet; look up by name
        SootClass clazz = Scene.v().getSootClass(targetClass);
        SootMethod method = clazz.getMethodByName(targetMethod);
        Result r = processTargetMethod(method, pta);
        if (r != Result.NOT_PROCESSED) {
            result = r;
        }
    }

    /** Returns the may-alias result if method is the target and could be processed, else {@link Result#NOT_PROCESSED}. */
    private Result processTargetMethod(SootMethod method, PointsToAnalysis pta) {
        if (!this.targetClass.equals(method.getDeclaringClass().getName())
                || !this.targetMethod.equals(method.getName())
                || !method.hasActiveBody()) {
            return Result.NOT_PROCESSED;
        }

        Body body = method.retrieveActiveBody();
        Local l1 = null;
        Local l2 = null;
        for (Local l : body.getLocals()) {
            if (!isTargetType(l)) continue;
            String name = l.getName();
            if (this.local1.equals(name)) l1 = l;
            else if (this.local2.equals(name)) l2 = l;
        }
        if (l1 == null || l2 == null) {
            return Result.PROBLEM_WITH_LOCALS_IDENTIFICATION;
        }
        if (pta == null) {
            return Result.PTA_UNAVAILABLE;
        }
        PointsToSet pts1 = pta.reachingObjects(l1);
        PointsToSet pts2 = pta.reachingObjects(l2);
        return pts1.hasNonEmptyIntersection(pts2) ? Result.PTA_SUGGESTS_ALIAS : Result.PTA_NO_EVIDENCE_OF_ALIAS;
    }

    private boolean isTargetType(Local local) {
        String typeName = local.getType().toString();
        return typeName.equals(type);
    }
}
