package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.utils.FileUtil;
import com.zstring.utils.SootUtils;
import soot.*;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.internal.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.util.Chain;

import java.util.*;

public class ChaAnalyzer {

    public static String cp = null;
    public static String pp = null;
    public static String outputTxt = null;
    public Hierarchy hierarchy;
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
            pp = "/home/sean/bench_compared/check";
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
        long t1 = new Date().getTime();
        SootEnvironment.init(cp, pp);
//        calcOriginCallsite();
//        setCHA();

        hierarchy = Scene.v().getActiveHierarchy();
//        calcCHA();
        generateResult();
        long t2 = new Date().getTime();
        System.out.println("CHA analysis ended, used " + (t2-t1)/1000.0 + "s");
        dataOutput[0] = String.valueOf((t2-t1)/1000.0);
        FileUtil.writeLog(dataOutput, "cha-log", outputTxt);
    }

    private static void setCHA() {
        Map<String, String> options = new HashMap<String, String>();
        System.out.println("cha analysis starting......");

        options.put("enabled", "true");
        options.put("verbose", "true");
//        options.put("set-impl", "double");
//        options.put("set-impl", "double");
//        options.put("set-impl", "hybrid");
//        options.put("vta", "true");
        options.put("apponly", "true");
//        options.put("double-set-old", "hybrid");
//        options.put("double-set-new", "hash");

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
                        SootClass c = Scene.v().getSootClass(receiver.getType().toString());

                        List<SootClass> posibleTypes = new ArrayList<>(10);
                        if(c.isInterface()) {
                            posibleTypes.addAll(hierarchy.getImplementersOf(c));
                        } else {
                            posibleTypes.addAll(hierarchy.getSuperclassesOf(c));
                        }
                        List<SootClass> chas = new ArrayList<>(10);

                        for(SootClass sc : posibleTypes) {
                            try {
                                SootMethod sm = sc.getMethod(invokeExpr.getMethod().getSubSignature());
                                if(sm.isConcrete()) {
                                    chas.add(sc);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

//                        try {
//                            SootMethod sm = c.getMethod(invokeExpr.getMethod().getSubSignature());
//                            List<SootClass> subclass = hierarchy.getSubclassesOf(c);
                            // means this method can be access by all subclass
//                            if(!sm.isPrivate()) {
//                                chas.addAll(subclass);
//                            } else {
//                                for(SootClass sc : subclass) {
//                                    try {
//                                        sm = c.getMethod(invokeExpr.getMethod().getSubSignature());
//                                        if(sm.isConcrete()) {
//                                            chas.add(sc);
//                                        }
//                                    } catch (Exception e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                            }
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
                        if(!c.isInterface()) {
                            try {
                                List<SootClass> subclass = hierarchy.getSubclassesOf(c);
                                chas.addAll(subclass);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            chas.add(c);
                        }
                        for(SootClass sc : chas) {
                            writeLine = recordVirtualCallPrefix + SPLITTER + sc.getName() + SPLITTER + receiver + SPLITTER + invokeExpr.getMethod().getSubSignature() + SPLITTER + lineNum;
                            data2Write.add(writeLine);
                        }

                    }
                }
                if(!data2Write.isEmpty()) {
                    filenameMap.put(fileIdx, m.getSignature());
                    FileUtil.writeStaticResult(data2Write,  "cha-result", fileIdx + FILE_SUFFIX);
                    fileIdx++;
                }
            }
            FileUtil.writeMap(filenameMap, "cha-result", "map.txt");
        }
    }

}
