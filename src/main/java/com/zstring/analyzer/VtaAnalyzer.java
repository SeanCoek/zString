package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.utils.CommonUtils;
import com.zstring.utils.FileUtil;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.internal.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

import java.util.*;

public class VtaAnalyzer {
    public static String cp = null;
    public static String pp = null;
    public static String outputTxt = null;
    public static CallGraph cg;
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
//            pp = "/home/sean/bench_compared/helloworld";
//            pp = "/home/sean/instruTest";
            pp = "/home/sean/bench_compared/compress";
        }
        if(outputTxt == null) {
            outputTxt = "default.txt";
        }
        VtaAnalyzer.cp = cp;
        VtaAnalyzer.pp = pp;
        new VtaAnalyzer().analyze(cp, pp);
    }

    public void analyze() {
        analyze(VtaAnalyzer.cp, VtaAnalyzer.pp);
    }

    public void analyze(String cp, String pp) {
        // Time [0]
        String[] dataOutput = new String[1];
        long t1 = new Date().getTime();
        SootEnvironment.init(cp, pp);
        setSparkPointsToAnalysis();
        cg = Scene.v().getCallGraph();
        long t2 = new Date().getTime();
//        generateResult();
        CommonUtils.writeReachableMethods(Scene.v().getReachableMethods(), Scene.v().getApplicationClasses(), "vta-reach", outputTxt);
        System.out.println("VTA analysis ended, used " + (t2-t1)/1000.0 + "s");
        dataOutput[0] = String.valueOf((t2-t1)/1000.0);
//        FileUtil.writeLog(dataOutput, "vta-log", outputTxt);
    }

    private static void setSparkPointsToAnalysis() {
        Map<String, String> options = new HashMap<String, String>();
        System.out.println("spark analysis starting......");

        options.put("enabled", "true");
        options.put("verbose", "true");
        options.put("propagator", "worklist");
//        options.put("set-impl", "bit");
        options.put("set-impl", "double");
//        options.put("set-impl", "hybrid");
        options.put("vta", "true");
//        options.put("on-the-fly", "true");
        options.put("double-set-old", "hybrid");
        options.put("double-set-new", "hybrid");

        SparkTransformer.v().transform("", options);

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
                String recordSpecialrefix = "IN METHOD" + SPLITTER + thisMethodSig + SPLITTER + "SPECIALINVOKE";
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
                    } else if(invokeExpr instanceof JSpecialInvokeExpr) {
                        writeLine = recordSpecialrefix + SPLITTER + invokeExpr.getMethod().getSignature() + SPLITTER + lineNum;
                        data2Write.add(writeLine);
                    } else if(invokeExpr instanceof JVirtualInvokeExpr){
                        Value receiver = ((JVirtualInvokeExpr) invokeExpr).getBase();
                        Iterator<Edge> outEdges = cg.edgesOutOf(unit);

                        if(outEdges == null) {
                            continue;
                        }
                        while(outEdges.hasNext()) {
                            Edge e = outEdges.next();
                            SootClass c = e.tgt().getDeclaringClass();
                            writeLine = recordVirtualCallPrefix + SPLITTER + c.getName() + SPLITTER +receiver + SPLITTER + invokeExpr.getMethod().getSignature() + SPLITTER + lineNum;
                            data2Write.add(writeLine);
                        }
                    }
                }
                if(!data2Write.isEmpty()) {
                    filenameMap.put(fileIdx, m.getSignature());
                    FileUtil.writeStaticResult(data2Write,  "vta-result", fileIdx + FILE_SUFFIX);
                    fileIdx++;
                }
            }
            FileUtil.writeMap(filenameMap, "vta-result", "map.txt");
        }
    }
}
