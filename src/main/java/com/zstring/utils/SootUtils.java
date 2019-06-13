package com.zstring.utils;

import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.jimple.InvokeExpr;

public class SootUtils {

    public static Object[] resolveInvokeUnit(InvokeExpr exp) {
        SootMethod callee = exp.getMethod();
        return new Object[]{null, callee};
    }

    public static String getMethodSigByType(Type t, String subSig) {
        if(t instanceof RefType) {
            SootClass c = ((RefType) t).getSootClass();
            try {
                return c.getMethod(subSig).getSignature();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
