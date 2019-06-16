package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
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



    public PointsToAnalysis pta;
    public Hierarchy hierarchy;


    public static void main(String[] args) {
        // we can pass classpath(cp) and process path(pp) through parameter "args"
//        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar:" + "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/jce.jar";
        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
        String pp = "/home/sean/bench_compile/";
        pp = "/home/sean/bench_compared/crypto/";
        PointsToAnalyzer.cp = cp;
        PointsToAnalyzer.pp = pp;
        new PointsToAnalyzer().analyze(cp, pp);
    }

    public void analyze() {
        analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    public void analyze(String cp, String pp) {
        SootEnvironment.init(cp, pp);
        setCHA();
        hierarchy = Scene.v().getActiveHierarchy();
        calcCHA();
//        setSparkPointsToAnalysis();
//        pta = Scene.v().getPointsToAnalysis();

//        calcPTA();
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
        long t1 = new Date().getTime();
        SparkTransformer.v().transform("", options);
        long t2 = new Date().getTime();

        System.out.println("spark analysis ended, used " + (t2-t1) + "ms");
    }

    private static void setCHA() {
        long t1 = new Date().getTime();
        CHATransformer.v().transform();
        long t2 = new Date().getTime();
        System.out.println("CHA analysis ended, used " + (t2-t1) + "ms");
    }

    private int calcCHA() {
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
                            SootClass c = Scene.v().getSootClass(caller.getType().toString());
                            List<SootClass> chas = null;
                            if(c.isInterface()) {
                                chas = hierarchy.getImplementersOf(c);
                            } else {
                                try {
                                    chas = hierarchy.getDirectSubclassesOf(c);
                                } catch (Exception e) {
//                                    e.printStackTrace();
                                    callsites++;
                                    continue;
                                }
                            }
                            for(SootClass sc : chas) {
                                try {
                                    SootMethod sm = sc.getMethod(invokeExpr.getMethod().getSignature());
                                    if(sm.isConcrete()) {
                                        callsites++;
                                    }
                                } catch (Exception e) {

                                }
                            }
                            callsites++;
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

}
