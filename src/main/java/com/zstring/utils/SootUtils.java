package com.zstring.utils;

import soot.*;
import soot.jimple.InvokeExpr;
import soot.util.Chain;

import java.util.Iterator;

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

    public static Local getLocalByValue(Body body, Value value) {
        Iterator<Local> locals = body.getLocals().snapshotIterator();
        while(locals.hasNext()) {
            Local local = locals.next();
            if(local.equals(value)) {
                return local;
            }
        }
        return null;
    }
}
