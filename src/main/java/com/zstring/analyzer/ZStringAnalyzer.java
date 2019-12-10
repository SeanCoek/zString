package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import soot.PointsToAnalysis;
import soot.Scene;

public class ZStringAnalyzer {

    public static void main(String[] args) {
        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
        String pp = "/home/sean/bench_compared/crypto";
        SootEnvironment.init(cp, pp);
        PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
        SootEnvironment.travelStruct();
    }
}
