package com.zstring.utils;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeExpr;
import soot.jimple.internal.JInvokeStmt;

import java.util.Iterator;

public class SootUtils {

    public static Object[] resolveInvokeUnit(Unit srcUnit) {
        if(srcUnit instanceof JInvokeStmt) {
            InvokeExpr invokeExpr = ((JInvokeStmt) srcUnit).getInvokeExpr();
            Value caller = null;
            SootMethod callee = null;
            Iterator useBoxIter = invokeExpr.getUseBoxes().iterator();
            while(useBoxIter.hasNext()) {
                ValueBox vb = (ValueBox) useBoxIter.next();
                caller = vb.getValue();
            }
            callee = invokeExpr.getMethod();
            return new Object[]{caller, callee};
        }
        return null;
    }
}
