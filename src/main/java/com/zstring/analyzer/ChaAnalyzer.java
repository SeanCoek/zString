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

public class ChaAnalyzer {

    public static String cp = null;
    public static String pp = null;
    public static String outputTxt = null;
    public Hierarchy hierarchy;



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
//        pp = "/home/sean/bench_compared/antlr.jar";
        ChaAnalyzer.cp = cp;
        ChaAnalyzer.pp = pp;
        new ChaAnalyzer().analyze(cp, pp);
    }

    public void analyze() {
        analyze(ChaAnalyzer.cp, ChaAnalyzer.pp);
    }

    public void analyze(String cp, String pp) {
        // Time [0]
        String[] dataOutput = new String[1];
        SootEnvironment.init(cp, pp);
//        calcOriginCallsite();
        long t1 = new Date().getTime();
        setCHA();
        long t2 = new Date().getTime();
        System.out.println("CHA analysis ended, used " + (t2-t1)/1000.0 + "s");
        dataOutput[0] = String.valueOf((t2-t1)/1000.0);
        hierarchy = Scene.v().getActiveHierarchy();
        calcCHA();
        FileUtil.writeResult(dataOutput, outputTxt);
    }

    private static void setCHA() {
        CHATransformer.v().transform();
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
                                    SootMethod sm = sc.getMethod(invokeExpr.getMethod().getSubSignature());
                                    if(sm.isConcrete()) {
                                        callsites++;
                                    }
                                } catch (Exception e) {
//                                    System.out.println("b");
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
