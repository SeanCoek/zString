package com.zstring.transform;

import com.zstring.utils.SootUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JDynamicInvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JStaticInvokeExpr;
import soot.util.Chain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class InvokeInstrumenter extends BodyTransformer {
    public static String CLASS_SYSTEM = "java.lang.System";
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

    private static InvokeInstrumenter instance = new InvokeInstrumenter();
    public static InvokeInstrumenter v() {
        return instance;
    }

    private SootClass javaIOPrintStream;

    public static void main(String[] args) {
//        PackManager.v().getPack("jop").add(new Transform("jop.instrumenter", InvokeInstrumenter.v()));
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.intru", new TestTransformer()));

        Scene.v().addBasicClass(CLASS_PRINT_STREAM, SootClass.SIGNATURES);
        Scene.v().addBasicClass(CLASS_SYSTEM, SootClass.SIGNATURES);
        Scene.v().addBasicClass(CLASS_FILEUTIL, SootClass.SIGNATURES);
        //Scene.v().loadClassAndSupport(CLASS_FILEUTIL);
        //Scene.v().setSootClassPath("/home/sean/sootlib/rt.jar:/home/sean/sootlib/fileutils.jar:/home/sean/sootlib/commons-io.jar:.");
        //PackManager.v().runPacks();
        //PackManager.v().writeOutput();
        soot.Main.main(args);

    }


    @Override
    protected void internalTransform(Body body, String s, Map<String, String> map) {

        Chain units = body.getUnits();
        Iterator<Unit> iter = units.snapshotIterator();
        javaIOPrintStream = javaIOPrintStream == null ? Scene.v().getSootClass(CLASS_PRINT_STREAM) : javaIOPrintStream;
        SootMethod instrument = javaIOPrintStream.getMethod(METHOD_PRINTLN);
        SootMethod getClassMethod = Scene.v().getSootClass(CLASS_OBJECT).getMethod(METHOD_GETCLASS);
        SootMethod getNameMethod = Scene.v().getSootClass(CLASS_JAVA_LANG_CLASS).getMethod(METHOD_GETNAME);
        SootMethod appendMethod = Scene.v().getSootClass(CLASS_STRINGBUILDER).getMethod(METHOD_APPEND);
        SootMethod toStringMethod = Scene.v().getSootClass(CLASS_STRINGBUILDER).getMethod((METHOD_TOSTRING));
        SootMethod writeMethod = Scene.v().getSootClass(CLASS_FILEUTIL).getMethod(METHOD_WRITE);

        // object of type: java.io.PrintStream
        Local instruReceiver = null;
        // println(record);
        Local record = null;
        // recordBuilder.append();
        Local recordBuilder = null;
        // recType = obj.getCLass();
        Local recType = null;
        // recTypeName = recType.getName();
        Local recTypeName = null;
        // String[] dataOutput
        //Local data = null;

        boolean isLocalInstrumented = false;

        String thisMethodSig = body.getMethod().getSignature();
        String recordStaticPrefix = "IN METHOD" + SPLITTER + thisMethodSig + SPLITTER + "STATICINVOKE";
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
                    //data = Jimple.v().newLocal("dataOutput", RefType.v(CLASS_STRING));

                    body.getLocals().add(instruReceiver);
                    body.getLocals().add(record);
                    body.getLocals().add(recordBuilder);
                    body.getLocals().add(recType);
                    body.getLocals().add(recTypeName);
                    //body.getLocals().add(data);

                    isLocalInstrumented = true;
                }
            }

            Value receiver = null;
            if(invokeExpr instanceof JStaticInvokeExpr) {
                insertAfterStaticInvoke(units, unit,
                        recordBuilder, record, instruReceiver,
                        appendMethod, toStringMethod, instrument, writeMethod, thisMethodSig, recordStaticPrefix, invokeExpr);
            } else if(invokeExpr instanceof JDynamicInvokeExpr) {
                //TODO: dynamic invoke is a new feature and we haven't handle this.
            } else if(invokeExpr instanceof InstanceInvokeExpr){
                receiver = ((InstanceInvokeExpr) invokeExpr).getBase();
                Local recLocal = SootUtils.getLocalByValue(body, receiver);

                insertAfterVirtualInvoke(units, unit, recordBuilder, record, instruReceiver, recType, recLocal, recTypeName,
                        getClassMethod, getNameMethod, appendMethod, toStringMethod, instrument, writeMethod, thisMethodSig,
                        recordVirtualCallPrefix, invokeExpr);
            }
        }
        body.validate();
    }

    private void insertLastUnit(Chain units, Unit unit, Local data, String filename, SootMethod writeMethod) {
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(writeMethod.makeRef(), data, StringConstant.v(filename))), unit);
    }

    private void insertBeforeStaticInvoke(Chain units, Unit unit, Local recordBuilder, Local record, Local instruReceiver,
                                    SootMethod appendMethod, SootMethod toStringMethod, SootMethod instrument,
                                    String recordStaticPrefix, InvokeExpr invokeExpr) {
        units.insertBefore(Jimple.v().newAssignStmt(
                recordBuilder,Jimple.v().newNewExpr(RefType.v(CLASS_STRINGBUILDER))), unit);
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        recordBuilder, Scene.v().getSootClass(CLASS_STRINGBUILDER).getMethod(METHOD_INIT).makeRef(), new ArrayList(0))),unit);
        units.insertBefore(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder,appendMethod.makeRef(), StringConstant.v(recordStaticPrefix))), unit);
        units.insertBefore(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder,appendMethod.makeRef(), StringConstant.v("  " + invokeExpr.getMethod().getSignature() + "\n"))), unit);
        units.insertBefore(Jimple.v().newAssignStmt(
                record,Jimple.v().newVirtualInvokeExpr(
                        recordBuilder, toStringMethod.makeRef(), new ArrayList<>(0))), unit);
        units.insertBefore(Jimple.v().newAssignStmt(
                instruReceiver, Jimple.v().newStaticFieldRef(
                        Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), unit);
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(
                        instruReceiver, instrument.makeRef(), record)), unit);
    }

    private void insertAfterStaticInvoke(Chain units, Unit unit, Local recordBuilder, Local record, Local instruReceiver,
                                          SootMethod appendMethod, SootMethod toStringMethod, SootMethod instrument, SootMethod writeMethod,
                                          String thisMethodSig, String recordStaticPrefix, InvokeExpr invokeExpr) {
        int lineNum = unit.getJavaSourceStartLineNumber();
        units.insertAfter(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(writeMethod.makeRef(), record, StringConstant.v(thisMethodSig + FILE_SUFFIX))), unit);
//        units.insertAfter(Jimple.v().newInvokeStmt(
//                Jimple.v().newVirtualInvokeExpr(
//                        instruReceiver, instrument.makeRef(), record)), unit);
//        units.insertAfter(Jimple.v().newAssignStmt(
//                instruReceiver, Jimple.v().newStaticFieldRef(
//                        Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                record,Jimple.v().newVirtualInvokeExpr(
                        recordBuilder, toStringMethod.makeRef(), new ArrayList<>(0))), unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder,appendMethod.makeRef(), StringConstant.v(invokeExpr.getMethod().getSignature() + SPLITTER + lineNum))), unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder,appendMethod.makeRef(), StringConstant.v(recordStaticPrefix + SPLITTER))), unit);
        units.insertAfter(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        recordBuilder, Scene.v().getSootClass(CLASS_STRINGBUILDER).getMethod(METHOD_INIT).makeRef(), new ArrayList(0))),unit);
        units.insertAfter(Jimple.v().newAssignStmt(
                recordBuilder,Jimple.v().newNewExpr(RefType.v(CLASS_STRINGBUILDER))), unit);
    }

    private void insertBeforeVirtualInvoke(Chain units, Unit unit, Local recordBuilder, Local record, Local instruReceiver,
                                     Local recType, Local recLocal, Local recTypeName,
                                     SootMethod getClassMethod, SootMethod getNameMethod,
                                     SootMethod appendMethod, SootMethod toStringMethod, SootMethod instrument,
                                     String recordVirtualCallPrefix, InvokeExpr invokeExpr) {
        units.insertBefore(Jimple.v().newAssignStmt(
                recType, Jimple.v().newVirtualInvokeExpr(recLocal, getClassMethod.makeRef(), new ArrayList<>(0))), unit);
        units.insertBefore(Jimple.v().newAssignStmt(recTypeName,
                Jimple.v().newVirtualInvokeExpr(recType, getNameMethod.makeRef(), new ArrayList<>(0))), unit);
        units.insertBefore(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newNewExpr(RefType.v(CLASS_STRINGBUILDER))), unit);
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        recordBuilder, Scene.v().getSootClass(CLASS_STRINGBUILDER).getMethod(METHOD_INIT).makeRef(), new ArrayList(0))),unit);
        units.insertBefore(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder,appendMethod.makeRef(), StringConstant.v(recordVirtualCallPrefix))), unit);
        units.insertBefore(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder,appendMethod.makeRef(), recTypeName)), unit);
        units.insertBefore(Jimple.v().newAssignStmt(
                recordBuilder, Jimple.v().newVirtualInvokeExpr(
                        recordBuilder, appendMethod.makeRef(), StringConstant.v("  " + invokeExpr.getMethod().getSubSignature() + "\n"))), unit);
        units.insertBefore(Jimple.v().newAssignStmt(record,
                Jimple.v().newVirtualInvokeExpr(recordBuilder, toStringMethod.makeRef(), new ArrayList<>(0))), unit);
        units.insertBefore(Jimple.v().newAssignStmt(
                instruReceiver, Jimple.v().newStaticFieldRef(
                        Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), unit);
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(
                        instruReceiver, instrument.makeRef(), record)), unit);
    }

    private void insertAfterVirtualInvoke(Chain units, Unit unit, Local recordBuilder, Local record, Local instruReceiver,
                                           Local recType, Local recLocal, Local recTypeName,
                                           SootMethod getClassMethod, SootMethod getNameMethod,
                                           SootMethod appendMethod, SootMethod toStringMethod, SootMethod instrument, SootMethod writeMethod,
                                           String thisMethodSig, String recordVirtualCallPrefix, InvokeExpr invokeExpr) {
        /* instrument ===>> recType = recLocal.getClass();
        1. instrument ===>> recTypeName = recType.getName();
        2. instrument ===>> recordBuilder = new StringBuilder();
        3. instrument ===>> recordBuilder.append(recordVirtualCallPrefix);
        4. instrument ===>> recordBuilder.append(recTypeName);
        5. instrument ===>> recordBuilder.append(invocationSignature);
        6. instrument ===>> record = recordBuilder.toString();
        7. instrument ===>> System.out.println(record);
        */
        int lineNum = unit.getJavaSourceStartLineNumber();
        String recName = recLocal.getName();
        units.insertAfter(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(writeMethod.makeRef(), record ,StringConstant.v(thisMethodSig + FILE_SUFFIX))), unit);
//        units.insertAfter(Jimple.v().newInvokeStmt(
//                Jimple.v().newVirtualInvokeExpr(
//                        instruReceiver, instrument.makeRef(), record)), unit);
//        units.insertAfter(Jimple.v().newAssignStmt(
//                instruReceiver, Jimple.v().newStaticFieldRef(
//                        Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), unit);
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
                        recordBuilder,appendMethod.makeRef(), StringConstant.v(recordVirtualCallPrefix + SPLITTER))), unit);
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
