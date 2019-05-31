package com.zstring.utils;

import soot.SootMethod;
import soot.jimple.InvokeExpr;

public class SootUtils {

    public static Object[] resolveInvokeUnit(InvokeExpr exp) {
        SootMethod callee = exp.getMethod();
        return new Object[]{null, callee};

//        if(srcUnit instanceof JInvokeStmt) {
//            InvokeExpr invokeExpr = ((JInvokeStmt) srcUnit).getInvokeExpr();
//            Value caller = null;
//            SootMethod callee = null;
//            Iterator useBoxIter = invokeExpr.getUseBoxes().iterator();
//            while(useBoxIter.hasNext()) {
//                ValueBox vb = (ValueBox) useBoxIter.next();
//                caller = vb.getValue();
//            }
//            callee = invokeExpr.getMethod();
//            return new Object[]{caller, callee};
//        }
//        return null;
    }
}
