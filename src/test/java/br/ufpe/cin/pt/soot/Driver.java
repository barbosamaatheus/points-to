package br.ufpe.cin.pt.soot;

import br.ufpe.cin.pt.soot.pta.PTASingleton;
import driver.PTAFactory;
import driver.PTAPattern;
import qilin.CoreConfig;
import qilin.core.PTA;
import qilin.pta.PTAConfig;
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
        try {
            Scene.v().loadNecessaryClasses();
            Scene.v().setEntryPoints(getEntryPoints(config.entryClass, config.entryMethod));
            AliasTransformer transformer = null;
            if (config.algorithm.getName().startsWith("qilin")) {
                configureQilin(config);
                PTA pta = createQilinPTA(config.algorithm);
                pta.run();
                PTASingleton.configureQilinPTA(pta);
                transformer = new AliasTransformer(config, PTASingleton.getInstance());
            }
            else {
                PackManager.v().getPack("cg").apply();
                PTASingleton.configureSootPTA(Scene.v().getPointsToAnalysis());
                transformer = new AliasTransformer(config, PTASingleton.getInstance());
            }
            PackManager.v().getPack("wjtp").add(new Transform("wjtp.ptcheck", transformer));
            PackManager.v().getPack("wjtp").apply();
            return transformer.getResult();
        } catch (Exception e) {
            throw new RuntimeException("Soot run failed for " + config.algorithm, e);
        }
    }

    private void setSootOptions() {
        G.reset();

        String classpath = buildClassPath();
        String processDir = new File("target/test-classes").getAbsolutePath();

        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_include(getIncludeList());
        Options.v().set_exclude(getExcludeList());  // jdk.internal.* only; sun.*/com.sun.* must resolve for Qilin PAG
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


    /**
     * Configures Soot call-graph / points-to for the given algorithm.
     * For SOOT_* algorithms, enables the corresponding Soot cg phase.
     * For QILIN_INSENS, disables Soot's PTA (cha and spark off) so Qilin runs as the only PTA.
     */
    private static void setCallGraph(CallGraphAlgorithm algorithm) {
        switch (algorithm) {
            case SOOT_CHA:
                Options.v().setPhaseOption("cg.cha", "enabled:true");
                Options.v().setPhaseOption("cg.spark", "enabled:false");
                break;
            case SOOT_SPARK:
                Options.v().setPhaseOption("cg.spark", "enabled:true");
                Options.v().setPhaseOption("cg.spark", "on-fly-cg:true");
                break;
            case SOOT_RTA:
                Options.v().setPhaseOption("cg.spark", "enabled:true");
                Options.v().setPhaseOption("cg.spark", "rta:true");
                Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
                break;
            case SOOT_VTA:
                Options.v().setPhaseOption("cg.spark", "enabled:true");
                Options.v().setPhaseOption("cg.spark", "vta:true");
                Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
                break;
            case QILIN_INSENS:
                // Qilin runs its own PTA; disable Soot's so cg.apply() does not run Spark/CHA
                Options.v().setPhaseOption("cg.cha", "enabled:false");
                Options.v().setPhaseOption("cg.spark", "enabled:false");
                break;
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }

    /**
     * Sets Qilin's ApplicationConfiguration to match our Soot setup. We do <em>not</em> call
     * Qilin's {@code driver.Main.setupSoot()} (see
     * <a href="https://github.com/QilinPTA/Qilin/blob/main/qilin.pta/src/driver/Main.java">Main.java</a>)
     * because we already configure Soot in {@link #setSootOptions()} and {@link #setCallGraph(CallGraphAlgorithm)}
     * (process_dir, classpath, include, entry points, cg). We only set Qilin's app config so any
     * code paths that read it see consistent values. Uses the same fields Main.setSootOptions /
     * setSootClassPath read: APP_PATH, LIB_PATH, JRE (JRE base dir, as in Main.getJreJars), MAIN_CLASS, INCLUDE.
     */
    private static void configureQilin(TestConfiguration config) {
        CoreConfig.ApplicationConfiguration app = PTAConfig.v().getAppConfig();
        app.APP_PATH = new File("target/test-classes").getAbsolutePath();
        app.LIB_PATH = buildLibPath();
        app.JRE = System.getProperty("java.home");  // JRE base dir (Main.getJreJars expects this)
        app.MAIN_CLASS = config.entryClass;
        app.INCLUDE = getIncludeList();
        app.EXCLUDE = Arrays.asList("sun.misc.*");
    }

    /** Library path for Qilin (-libpath): classpath minus app dir (java.class.path + rt + jce). */
    private static String buildLibPath() {
        String cp = System.getProperty("java.class.path");
        StringBuilder sb = new StringBuilder();
        if (cp != null && !cp.isEmpty()) {
            sb.append(cp);
        }
        String rt = pathToRT();
        if (rt != null) {
            if (sb.length() > 0) sb.append(File.pathSeparator);
            sb.append(rt);
        }
        String jce = pathToJCE();
        if (jce != null) {
            if (sb.length() > 0) sb.append(File.pathSeparator);
            sb.append(jce);
        }
        return sb.length() > 0 ? sb.toString() : "";
    }

    /**
     * Creates a Qilin PTA for the chosen algorithm using PTAConfig.PointerAnalysisConfiguration.ptaPattern.
     * Map additional algorithms in {@link #qilinPtaPatternFor(CallGraphAlgorithm)} (e.g. "kc", "ko").
     */
    private static PTA createQilinPTA(CallGraphAlgorithm algorithm) {
        String pattern = qilinPtaPatternFor(algorithm);
        PTAConfig.reset();
        PTAConfig.v().getPtaConfig().ptaPattern = new PTAPattern(pattern);
        return PTAFactory.createPTA(PTAConfig.v().getPtaConfig().ptaPattern);
    }

    /** Returns the Qilin PTAPattern string for the given algorithm (e.g. QILIN_INSENS → "insens"). */
    private static String qilinPtaPatternFor(CallGraphAlgorithm algorithm) {
        assert(algorithm.name.startsWith("qilin"));
        return algorithm.name.replaceAll("qilin::", "").toLowerCase();
    }

    /** Include list so these packages are treated as application classes (SVFA pattern). */
    private static List<String> getIncludeList() {
        return Arrays.asList("br.ufpe.cin.pt.*", "sun.misc.*");
    }

    /**
     * Exclude list: only jdk.internal.* (Java 9+). Do not exclude sun.* or com.sun.* — Qilin's PAG
     * requires those types to be resolved when it creates nodes (e.g. for sun.misc.Launcher$AppClassLoader).
     * They are resolved from the JDK (rt.jar on Java 8) when on the classpath.
     */
    private static List<String> getExcludeList() {
        return Arrays.asList("jdk.internal.");
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
