package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.structs.CG;
import soot.*;
import soot.jimple.spark.SparkTransformer;

import java.util.*;

public class PointsToAnalyzer {

    public static String cp = null;
    public static String pp = null;



    public PointsToAnalysis pta;


    public static void main(String[] args) {
        // we can pass classpath(cp) and process path(pp) through parameter "args"
        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar:" + "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/jce.jar";
        String pp = "/home/sean/bench_compile/";
        PointsToAnalyzer.cp = cp;
        PointsToAnalyzer.pp = pp;
//        String appclass = pp + "appclass.txt";
        new PointsToAnalyzer().analyze(cp, pp);
    }

    public void analyze() {
        analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    public void analyze(String cp, String pp) {
        SootEnvironment.init(cp, pp);
        setSparkPointsToAnalysis();
        pta = Scene.v().getPointsToAnalysis();
        SootEnvironment.cg = new CG(Scene.v().getCallGraph(), SootEnvironment.allMethods);
    }

    private static void setSparkPointsToAnalysis() {
        Map<String, String> options = new HashMap<String, String>();
        System.out.println("spark analysis starting......");

        options.put("enabled", "true");
        options.put("verbose", "true");
        options.put("propagator", "iter");
//        options.put("set-impl", "double");
        options.put("set-impl", "hybrid");
//        options.put("double-set-old", "hybrid");
//        options.put("double-set-new", "hybrid");
        SparkTransformer.v().transform("", options);

        System.out.println("spark analysis ending......");
    }

}
