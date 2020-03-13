package com.zstring.transform;

import soot.*;
import soot.jimple.*;
import soot.util.Chain;

import java.util.*;

public class ReachableTransformer extends SceneTransformer {

    public static String CLASS_FILEUTIL = "com.zstring.utils.FileUtil";
    public static String METHOD_WRITE = "void writeResult(java.lang.String,java.lang.String)";

    private static ReachableTransformer instance = new ReachableTransformer();
    public static ReachableTransformer v() { return instance;}

    @Override
    protected void internalTransform(String s, Map<String, String> map) {
        Set<SootClass> allClasses = new HashSet<>();
        allClasses.addAll(Scene.v().getApplicationClasses());

        for(SootClass sc : allClasses) {
            List<SootMethod> methods = sc.getMethods();
            for(SootMethod m : methods) {
                if(!m.isConcrete()) {
                    continue;
                }
                Body body = m.retrieveActiveBody();
                Chain units = body.getUnits();
                Iterator<Unit> iter = units.snapshotIterator();

                SootMethod writeMethod = Scene.v().getSootClass(CLASS_FILEUTIL).getMethod(METHOD_WRITE);
                while(iter.hasNext()) {
                    Unit unit = iter.next();
                    if(unit instanceof ReturnStmt || unit instanceof ReturnVoidStmt || unit instanceof ThrowStmt) {
                        insertReachingRecord(units, unit, writeMethod, m.getSignature(), "reachMethodDynamic.txt");
                    }
                }
                body.validate();
            }
        }
    }

    private synchronized void insertReachingRecord(Chain units, Unit returnUnit, SootMethod writeMethod, String reachedMethod, String fileName) {
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(writeMethod.makeRef(), StringConstant.v(reachedMethod), StringConstant.v(fileName))), returnUnit);
    }
}
