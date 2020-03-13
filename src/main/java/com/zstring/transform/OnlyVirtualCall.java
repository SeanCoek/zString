package com.zstring.transform;

import com.zstring.utils.FileUtil;
import com.zstring.utils.SootUtils;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.StringConstant;
import soot.jimple.internal.*;
import soot.util.Chain;

import java.util.*;

public class OnlyVirtualCall extends SceneTransformer {

    public static String CLASS_PRINT_STREAM = "java.io.PrintStream";
    public static String CLASS_JAVA_LANG_CLASS = "java.lang.Class";
    public static String CLASS_STRING = "java.lang.String";
    public static String CLASS_STRINGBUILDER = "java.lang.StringBuilder";
    public static String CLASS_OBJECT = "java.lang.Object";
    public static String CLASS_FILEUTIL = "com.zstring.utils.FileUtil";
    public static String METHOD_WRITE = "void writeResult(java.lang.String,java.lang.String)";
    public static String METHOD_PRINTLN = "void println(java.lang.String)";
    public static String METHOD_GETCLASS = "java.lang.Class getClass()";
    public static String METHOD_GETNAME = "java.lang.String getName()";
    public static String METHOD_APPEND = "java.lang.StringBuilder append(java.lang.String)";
    public static String METHOD_TOSTRING = "java.lang.String toString()";
    public static String METHOD_INIT = "void <init>()";
    public static String SPLITTER = "::";
    public static String FILE_SUFFIX = ".txt";
    private static String CALLTYPE_STATIC = "static";
    private static String CALLTYPE_SPECIAL = "special";
    public static Map<Integer, String> filenameMap = new HashMap<>(100);
    public static Map<String, Integer> filenameMapReverse = new HashMap<>(100);
    public static int fileIdx = 0;

    private static OnlyVirtualCall instance = new OnlyVirtualCall();
    public static OnlyVirtualCall v() {
        return instance;
    }

    private SootClass javaIOPrintStream;

    @Override
    protected void internalTransform(String s, Map<String, String> map) {
        Set<SootClass> allClasses = new HashSet<>();
        allClasses.addAll(Scene.v().getApplicationClasses());
        for(SootClass c: allClasses) {
//            if(!c.getName().startsWith("spec.benchmarks.")) {
//                continue;
//            }
            List<SootMethod> methods = c.getMethods();
            for(SootMethod m: methods) {
                if(!m.isConcrete()) {
                    continue;
                }
                Body body = m.retrieveActiveBody();
                Chain units = body.getUnits();
                Iterator<Unit> iter = units.snapshotIterator();
                javaIOPrintStream = javaIOPrintStream == null ? Scene.v().getSootClass(CLASS_PRINT_STREAM) : javaIOPrintStream;
                SootMethod instrument = javaIOPrintStream.getMethod(METHOD_PRINTLN);
                SootMethod getClassMethod = Scene.v().getSootClass(CLASS_OBJECT).getMethod(METHOD_GETCLASS);
                SootMethod getNameMethod = Scene.v().getSootClass(CLASS_JAVA_LANG_CLASS).getMethod(METHOD_GETNAME);
                SootMethod appendMethod = Scene.v().getSootClass(CLASS_STRINGBUILDER).getMethod(METHOD_APPEND);
                SootMethod toStringMethod = Scene.v().getSootClass(CLASS_STRINGBUILDER).getMethod((METHOD_TOSTRING));
                SootMethod writeMethod = Scene.v().getSootClass(CLASS_FILEUTIL).getMethod(METHOD_WRITE);

                Local instruReceiver = null;
                Local record = null;
                Local recordBuilder = null;
                Local recType = null;
                Local recTypeName = null;

                boolean isLocalInstrumented = false;

                String thisMethodSig = body.getMethod().getSignature();
                String recordStaticPrefix = "IN METHOD" + SPLITTER + thisMethodSig + SPLITTER + "STATICINVOKE";
                String recordSpecialPrefix = "IN METHOD" + SPLITTER + thisMethodSig + SPLITTER + "SPECIALINVOKE";
                String recordVirtualCallPrefix = "IN METHOD"+ SPLITTER + thisMethodSig + SPLITTER + "INVOKE";
                while(iter.hasNext()) {
                    Unit unit = iter.next();
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
                    synchronized (this) {
                        if (!isLocalInstrumented) {
                            instruReceiver = Jimple.v().newLocal("ioRef", RefType.v(CLASS_PRINT_STREAM));
                            record = Jimple.v().newLocal("record", RefType.v(CLASS_STRING));
                            recordBuilder = Jimple.v().newLocal("recordBuilder", RefType.v(CLASS_STRINGBUILDER));
                            recType = Jimple.v().newLocal("recType", RefType.v(CLASS_JAVA_LANG_CLASS));
                            recTypeName = Jimple.v().newLocal("recTypeName", RefType.v(CLASS_STRING));

                            body.getLocals().add(instruReceiver);
                            body.getLocals().add(record);
                            body.getLocals().add(recordBuilder);
                            body.getLocals().add(recType);
                            body.getLocals().add(recTypeName);

                            isLocalInstrumented = true;
                        }
                    }

                    Value receiver = null;
                    if(invokeExpr instanceof JStaticInvokeExpr) {
                        insertAfterStaticOrSpecialInvoke(units, unit,
                                recordBuilder, record, appendMethod,
                                toStringMethod, writeMethod, thisMethodSig, recordStaticPrefix, invokeExpr, CALLTYPE_STATIC);
                    } else if(invokeExpr instanceof JDynamicInvokeExpr) {
                        //TODO: dynamic invoke is a new feature and we haven't handle this.
                    } else if(invokeExpr instanceof JSpecialInvokeExpr) {
                        insertAfterStaticOrSpecialInvoke(units, unit,
                                recordBuilder, record, appendMethod,
                                toStringMethod, writeMethod, thisMethodSig, recordSpecialPrefix, invokeExpr, CALLTYPE_SPECIAL);
                    } else if(invokeExpr instanceof JVirtualInvokeExpr){
                        receiver = ((JVirtualInvokeExpr) invokeExpr).getBase();
                        Local recLocal = SootUtils.getLocalByValue(body, receiver);

                        insertAfterVirtualInvoke(units, unit, recordBuilder, record, instruReceiver, recType, recLocal, recTypeName,
                                getClassMethod, getNameMethod, appendMethod, toStringMethod, instrument, writeMethod, thisMethodSig,
                                recordVirtualCallPrefix, invokeExpr);
                    }
                }
                body.validate();
            }
        }

        FileUtil.writeMap(filenameMap, "dynamic", "map.txt");
    }


    private synchronized void insertAfterStaticOrSpecialInvoke(Chain units, Unit unit, Local recordBuilder, Local record,
                                                      SootMethod appendMethod, SootMethod toStringMethod, SootMethod writeMethod,
                                                      String thisMethodSig, String recordPrefix, InvokeExpr invokeExpr, String callType) {
        int lineNum = unit.getJavaSourceStartLineNumber();
        String filename = null;
        if(filenameMapReverse.get(thisMethodSig) == null) {
            filenameMap.put(fileIdx, thisMethodSig);
            filenameMapReverse.put(thisMethodSig, fileIdx);
            fileIdx++;
        }

        filename = filenameMapReverse.get(thisMethodSig) + FILE_SUFFIX;
        units.insertAfter(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(writeMethod.makeRef(), record, StringConstant.v(filename))), unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                record,Jimple.v().newVirtualInvokeExpr(
                        recordBuilder, toStringMethod.makeRef(), new ArrayList<>(0))), unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder,appendMethod.makeRef(), StringConstant.v(invokeExpr.getMethod().getSignature() + SPLITTER + lineNum))), unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder,appendMethod.makeRef(), StringConstant.v(recordPrefix + SPLITTER))), unit);
        units.insertAfter(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        recordBuilder, Scene.v().getSootClass(CLASS_STRINGBUILDER).getMethod(METHOD_INIT).makeRef(), new ArrayList(0))),unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recordBuilder,Jimple.v().newNewExpr(RefType.v(CLASS_STRINGBUILDER))), unit);
    }

    private synchronized void insertAfterVirtualInvoke(Chain units, Unit unit, Local recordBuilder, Local record, Local instruReceiver,
                                                       Local recType, Local recLocal, Local recTypeName,
                                                       SootMethod getClassMethod, SootMethod getNameMethod,
                                                       SootMethod appendMethod, SootMethod toStringMethod, SootMethod instrument, SootMethod writeMethod,
                                                       String thisMethodSig, String recordPrefix, InvokeExpr invokeExpr) {
        /* instrument ===>> recType = recLocal.getClass();
        1. instrument ===>> recTypeName = recType.getName();
        2. instrument ===>> recordBuilder = new StringBuilder();
        3. instrument ===>> recordBuilder.append(recordPrefix);
        4. instrument ===>> recordBuilder.append(recTypeName);
        5. instrument ===>> recordBuilder.append(invocationSignature);
        6. instrument ===>> record = recordBuilder.toString();
        7. instrument ===>> FileUtils.write(record, filename);
        */
        int lineNum = unit.getJavaSourceStartLineNumber();
        String filename = null;
        if(filenameMapReverse.get(thisMethodSig) == null) {
            filenameMap.put(fileIdx, thisMethodSig);
            filenameMapReverse.put(thisMethodSig, fileIdx);
            fileIdx++;
        }

        filename = filenameMapReverse.get(thisMethodSig) + FILE_SUFFIX;
        String recName = recLocal.getName();
        units.insertAfter(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(writeMethod.makeRef(), record ,StringConstant.v(filename))), unit);
        units.insertAfter(Jimple.v().newAssignStmt(record,
                Jimple.v().newVirtualInvokeExpr(recordBuilder, toStringMethod.makeRef(), new ArrayList<>(0))), unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder, appendMethod.makeRef(), StringConstant.v(SPLITTER + recName + SPLITTER + invokeExpr.getMethod().getSubSignature() + SPLITTER  + lineNum))), unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder,appendMethod.makeRef(), recTypeName)), unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder,appendMethod.makeRef(), StringConstant.v(recordPrefix + SPLITTER))), unit);
        units.insertAfter(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        recordBuilder, Scene.v().getSootClass(CLASS_STRINGBUILDER).getMethod(METHOD_INIT).makeRef(), new ArrayList(0))),unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newNewExpr(RefType.v(CLASS_STRINGBUILDER))), unit);
        units.insertAfter(Jimple.v().newAssignStmt(recTypeName,
                Jimple.v().newVirtualInvokeExpr(recType, getNameMethod.makeRef(), new ArrayList<>(0))), unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recType, Jimple.v().newVirtualInvokeExpr(recLocal, getClassMethod.makeRef(), new ArrayList<>(0))), unit);
    }
}
