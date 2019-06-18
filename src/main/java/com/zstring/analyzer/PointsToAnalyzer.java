package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.utils.FileUtil;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.internal.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CHATransformer;

import java.util.*;

public class PointsToAnalyzer {

    public static String cp = null;
    public static String pp = null;
    public static String outputTxt = null;
    public PointsToAnalysis pta;


    public static void main(String[] args) {
        // we can pass classpath(cp) and process path(pp) through parameter "args"
//        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar:" + "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/jce.jar";
        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
        String pp = null;
        for(int i=0; i<args.length; i++) {
            switch (args[i]) {
                case "-pp": pp = args[i+1]; break;
                case "-d" : outputTxt = args[i+1]; break;
                default:
            }
        }
        if(pp == null) {
            pp = "/home/sean/bench_compile/";
        }
        if(outputTxt == null) {
            outputTxt = "default.txt";
        }
        PointsToAnalyzer.cp = cp;
        PointsToAnalyzer.pp = pp;
        new PointsToAnalyzer().analyze(cp, pp);
    }

    public void analyze() {
        analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    public void analyze(String cp, String pp) {
        // Time [0]
        String[] dataOutput = new String[1];
        SootEnvironment.init(cp, pp);
//        calcOriginCallsite();
        long t1 = new Date().getTime();
        setSparkPointsToAnalysis();
        long t2 = new Date().getTime();
        System.out.println("spark analysis ended, used " + (t2-t1)/1000.0 + "s");
        dataOutput[0] = String.valueOf((t2-t1)/1000.0);
        pta = Scene.v().getPointsToAnalysis();
        calcPTA();
        FileUtil.writeResult(dataOutput, outputTxt);
//        SootEnvironment.cg = new CG(Scene.v().getCallGraph(), SootEnvironment.allMethods);
    }

    private int calcPTA() {
        int callsites = 0;
        Iterator<SootMethod> mIter = SootEnvironment.allMethods.iterator();
        while(mIter.hasNext()) {
            SootMethod m = mIter.next();
            if(m.isConcrete()) {
                JimpleBody jb = (JimpleBody) m.retrieveActiveBody();
                Iterator<Unit> uIter = jb.getUnits().iterator();
                while(uIter.hasNext()) {
                    Unit u = uIter.next();
                    InvokeExpr invokeExpr = null;
                    if(u instanceof JInvokeStmt) {
                        invokeExpr = ((JInvokeStmt) u).getInvokeExpr();
                    } else if(u instanceof JAssignStmt && ((JAssignStmt) u).getRightOp() instanceof InvokeExpr) {
                        invokeExpr = (InvokeExpr) ((JAssignStmt) u).getRightOp();
                    }
                    if(invokeExpr instanceof AbstractInstanceInvokeExpr) {
                        Value caller = ((AbstractInstanceInvokeExpr) invokeExpr).getBase();
                        if(caller instanceof Local) {
                            PointsToSet pts = pta.reachingObjects((Local) caller);
                            callsites += pts.possibleTypes().size();
                        }
                    } else if(invokeExpr instanceof JStaticInvokeExpr) {
                        callsites++;
                    }
                }
            }
        }
        System.out.println("total callsites: " + callsites);
        return callsites;
    }

    private static void setSparkPointsToAnalysis() {
        Map<String, String> options = new HashMap<String, String>();
        System.out.println("spark analysis starting......");

        options.put("enabled", "true");
        options.put("verbose", "true");
        options.put("propagator", "iter");
        options.put("set-impl", "double");
//        options.put("set-impl", "hybrid");
        options.put("double-set-old", "hybrid");
        options.put("double-set-new", "hybrid");

        SparkTransformer.v().transform("", options);

    }

    private int calcOriginCallsite() {
        int callsites = 0;
        Iterator<SootMethod> mIter = SootEnvironment.allMethods.iterator();
        while(mIter.hasNext()) {
            SootMethod m = mIter.next();
            if(m.isConcrete()) {
                JimpleBody jb = (JimpleBody) m.retrieveActiveBody();
                Iterator<Unit> uIter = jb.getUnits().iterator();
                while(uIter.hasNext()) {
                    Unit u = uIter.next();
                    InvokeExpr invokeExpr = null;
                    if(u instanceof JInvokeStmt) {
                        callsites++;
                    } else if(u instanceof JAssignStmt && ((JAssignStmt) u).getRightOp() instanceof InvokeExpr) {
                        callsites++;
                    }
                }
            }
        }
        System.out.println("total callsites: " + callsites);
        return callsites;
    }

}
