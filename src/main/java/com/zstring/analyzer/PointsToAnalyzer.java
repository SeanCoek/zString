package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.structs.Relation;
import com.zstring.utils.FileUtil;
import com.zstring.utils.SootUtils;
import soot.*;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.internal.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.util.Chain;

import java.util.*;

public class PointsToAnalyzer {

    public static String cp = null;
    public static String pp = null;
    public static String outputTxt = null;
    public static PointsToAnalysis pta;
    public static String SPLITTER = "::";
    public static String FILE_SUFFIX = ".txt";

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
//            pp = "/home/sean/bench_compile/";
            pp = "/home/sean/bench_compared/crypto";
//            pp = "/home/sean/instruTest";
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
//        SootEnvironment.travelStruct();
//        calcPTA();
        generateResult();
        FileUtil.writeLog(dataOutput, "pta-log", outputTxt);
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
                            for(Type t : pts.possibleTypes()) {
                                SootClass c = Scene.v().getSootClass(t.toString());
                                try {
                                    SootMethod sm = c.getMethod(invokeExpr.getMethod().getSubSignature());
                                    if (sm.isConcrete()) {
                                        callsites++;
                                    }
                                } catch (Exception e) {

                                }
                            }
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
        options.put("set-impl", "bit");
//        options.put("set-impl", "double");
//        options.put("set-impl", "double");
//        options.put("set-impl", "hybrid");
//        options.put("vta", "true");
        options.put("on-the-fly", "true");
//        options.put("double-set-old", "hybrid");
//        options.put("double-set-new", "hash");

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

    private void generateResult() {
        Map<Integer, String> filenameMap = new HashMap<>(100);
        int fileIdx = 0;
        Chain<SootClass> clsIter = Scene.v().getApplicationClasses();
        for(SootClass cls: clsIter) {
            List<SootMethod> methods = cls.getMethods();
            for(SootMethod m: methods) {
                if(!m.isConcrete()) {
                    continue;
                }
                String thisMethodSig = m.getSignature();
                String recordStaticPrefix = "IN METHOD" + SPLITTER + thisMethodSig + SPLITTER + "STATICINVOKE";
                String recordVirtualCallPrefix = "IN METHOD"+ SPLITTER + thisMethodSig + SPLITTER + "INVOKE";
                Chain<Unit> units = m.retrieveActiveBody().getUnits();
                List<String> data2Write = new ArrayList<>();
                for(Unit unit: units) {
                    int lineNum = unit.getJavaSourceStartLineNumber();
                    InvokeExpr invokeExpr = null;
                    if(unit instanceof JInvokeStmt) {
                        invokeExpr = ((JInvokeStmt) unit).getInvokeExpr();
                    } else if(unit instanceof JAssignStmt) {
                        if(((JAssignStmt) unit).getRightOp() instanceof InvokeExpr) {
                            invokeExpr = (InvokeExpr) ((JAssignStmt) unit).getRightOp();
                        }
                    }
                    if(invokeExpr == null) {
                        continue;
                    }
                    String writeLine = null;
                    if(invokeExpr instanceof JStaticInvokeExpr) {
                        writeLine = recordStaticPrefix + SPLITTER + invokeExpr.getMethod().getSignature() + SPLITTER + lineNum;
                        data2Write.add(writeLine);
                    } else if(invokeExpr instanceof JDynamicInvokeExpr) {
                        //TODO: dynamic invoke is a new feature and we haven't handle this.
                    } else if(invokeExpr instanceof InstanceInvokeExpr){
                        Value receiver = ((InstanceInvokeExpr) invokeExpr).getBase();
                        Local local = SootUtils.getLocalByValue(m.retrieveActiveBody(), receiver);
                        Set<Type> reachTypes = pta.reachingObjects(local).possibleTypes();
                        if(reachTypes == null || reachTypes.isEmpty()) {
                            continue;
                        }
                        for(Type t : reachTypes) {
                            if(t instanceof RefType) {
                                SootClass c = ((RefType) t).getSootClass();
                                writeLine = recordVirtualCallPrefix + SPLITTER + c.getName() + SPLITTER +receiver + SPLITTER + invokeExpr.getMethod().getSubSignature() + SPLITTER + lineNum;
                                data2Write.add(writeLine);
                            } else if (t instanceof AnySubType) {
                                SootClass c = ((AnySubType) t).getBase().getSootClass();
                                writeLine = recordVirtualCallPrefix + SPLITTER + c.getName() + SPLITTER +receiver + SPLITTER + invokeExpr.getMethod().getSubSignature() + SPLITTER + lineNum;
                                data2Write.add(writeLine);
                                writeLine = recordVirtualCallPrefix + SPLITTER + "any_subtype_of" + SPLITTER + c.getName() + SPLITTER +receiver + SPLITTER + invokeExpr.getMethod().getSubSignature() + SPLITTER + lineNum;
                                data2Write.add(writeLine);
                            }
                        }
                    }
                }
                if(!data2Write.isEmpty()) {
                    filenameMap.put(fileIdx, m.getSignature());
                    FileUtil.writeStaticResult(data2Write,  "pta-result", fileIdx + FILE_SUFFIX);
                    fileIdx++;
                }
            }
            FileUtil.writeMap(filenameMap, "pta-result", "map.txt");
        }
    }

}
