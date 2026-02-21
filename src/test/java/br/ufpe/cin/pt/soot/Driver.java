package br.ufpe.cin.pt.soot;

import soot.*;
import soot.options.Options;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Driver {
    /**
     * Runs Soot with the given call graph / points-to configuration and
     * returns the may-alias result.
     */
    public AliasTransformer.Result runAnalysis(TestConfiguration config) {
        setSootOptions();
        setCallGraph(config.algorithm);
        //TODO: parameterize type ...
        AliasTransformer transformer = new AliasTransformer(config.targetClass, config.targetMethod, config.local1, config.local2, "br.ufpe.cin.pt.samples.Point");
        try {
            Scene.v().loadNecessaryClasses();
            Scene.v().setEntryPoints(getEntryPoints(config.entryClass, config.entryMethod));
            PackManager.v().getPack("wjtp").add(new Transform("wjtp.ptcheck", transformer));
            PackManager.v().getPack("cg").apply();
            PackManager.v().getPack("wjtp").apply();
        } catch (Exception e) {
            throw new RuntimeException("Soot run failed for " + config.algorithm, e);
        }

        return transformer.getResult();
    }

    private void setSootOptions() {
        G.reset();

        String classpath = buildClassPath();
        String processDir = new File("target/test-classes").getAbsolutePath();

        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_include(getIncludeList());
        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_whole_program(true);
        Options.v().set_soot_classpath(classpath);
        Options.v().set_process_dir(Arrays.asList(new String[]{processDir}));
        Options.v().set_full_resolver(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_prepend_classpath(true);
        Options.v().setPhaseOption("jb", "use-original-names:true");
        Options.v().setPhaseOption("jop", "enabled:false");   // Jimple optimization pack (copy prop, CSE, etc.)
        Options.v().setPhaseOption("wjop", "enabled:false");  // Whole-Jimple optimization pack (inliner, etc.)
    }


    /** Configures Soot call-graph / points-to analysis for the given algorithm (CHA, SPARK, RTA, VTA). */
    private static void setCallGraph(String algorithm) {
        switch (algorithm.toUpperCase()) {
            case "CHA":
                Options.v().setPhaseOption("cg.cha", "enabled:true");
                Options.v().setPhaseOption("cg.spark", "enabled:false");
                break;
            case "SPARK":
                Options.v().setPhaseOption("cg.spark", "enabled:true");

                Options.v().setPhaseOption("cg.spark", "on-fly-cg:true");
                break;
            case "RTA":
                Options.v().setPhaseOption("cg.spark", "enabled:true");
                Options.v().setPhaseOption("cg.spark", "rta:true");
                Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
                break;
            case "VTA":
                Options.v().setPhaseOption("cg.spark", "enabled:true");
                Options.v().setPhaseOption("cg.spark", "vta:true");
                Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
                break;
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }

    /** Include list so these packages are treated as application classes (SVFA pattern). */
    private static List<String> getIncludeList() {
        return Arrays.asList("br.ufpe.cin.pt.*");
    }

    /** Entry points for the call graph (SVFA pattern: main method of entry class). */
    private static List<SootMethod> getEntryPoints(String entryPointClass, String entryPointMethod) {
        SootClass entryClass = Scene.v().getSootClass(entryPointClass);
        SootMethod mainMethod = entryClass.getMethodByName(entryPointMethod);
        return Collections.singletonList(mainMethod);
    }

    private static String buildClassPath() {
        String cp = System.getProperty("java.class.path");
        String testClasses = new File("target/test-classes").getAbsolutePath();
        StringBuilder sb = new StringBuilder();
        sb.append(testClasses).append(File.pathSeparator).append(testClasses);
        if (cp != null && !cp.isEmpty()) {
            sb.append(File.pathSeparator).append(cp);
        }
        String rt = pathToRT();
        if (rt != null) {
            sb.append(File.pathSeparator).append(rt);
        }
        String jce = pathToJCE();
        if (jce != null) {
            sb.append(File.pathSeparator).append(jce);
        }
        return sb.toString();
    }

    private static String pathToRT() {
        File rt = new File(System.getProperty("java.home"), "lib/rt.jar");
        return rt.exists() ? rt.getAbsolutePath() : null;
    }

    private static String pathToJCE() {
        File jce = new File(System.getProperty("java.home"), "lib/jce.jar");
        return jce.exists() ? jce.getAbsolutePath() : null;
    }
}
