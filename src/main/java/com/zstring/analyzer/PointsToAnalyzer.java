package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.structs.CG;
import soot.*;
import soot.jimple.spark.SparkTransformer;

import java.util.*;

public class PointsToAnalyzer {


    public PointsToAnalysis pta;


    public static void main(String[] args) {
        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar:" + "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/jce.jar";
        String pp = "/home/sean/bench_compile/";
        String appclass = pp + "appclass.txt";
        new PointsToAnalyzer().analyze(cp, pp, appclass);
    }

    public void analyze(String cp, String pp, String classlist) {
        SootEnvironment.init(cp, pp, classlist);
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
        options.put("set-impl", "double");
        options.put("double-set-old", "hybrid");
        options.put("double-set-new", "hybrid");
        SparkTransformer.v().transform("", options);

        System.out.println("spark analysis ending......");
    }

}
