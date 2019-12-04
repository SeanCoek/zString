//package com.zstring.utils;
//
//import com.zstring.structs.Relation;
//import soot.RefType;
//import soot.Unit;
//import soot.Value;
//import soot.jimple.InvokeExpr;
//import soot.jimple.internal.JArrayRef;
//
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//public class RelationUtils {
//
//    public static Set<Relation> extendFromInvoke(Set<Relation> originRelations, Map<String, Map<String, List<Value>>> methodInfoMap, String callee, Value receiver, Value caller, InvokeExpr invokeExpr) {
//        // caller is null means that this is a static invocation.
//        if(caller == null) {
//            List<Value> params = methodInfoMap.get(callee).get("param");
//            List<Value> returns = methodInfoMap.get(callee).get("return");
//            // x' (- return(c,m)
//            if(receiver != null) {
//                if(receiver instanceof JArrayRef) {
//                    receiver = ((JArrayRef) receiver).getBase();
//                }
//                for(Value vReturn: returns) {
//                    originRelations.add(new Relation(vReturn, receiver));
//                }
//            }
//
//            for(int i=0; i < invokeExpr.getArgCount(); i++) {
//                Value z = invokeExpr.getArg(i);
//                if(!(z.getType() instanceof RefType)) {
//                    continue;
//                }
//                Value p = params.get(i);      // static method don't hold the "this" variable, so we start the index from 0
//                if(z instanceof JArrayRef) {
//                    z = ((JArrayRef) z).getBase();
//                }
//                if(p instanceof JArrayRef) {
//                    p = ((JArrayRef) p).getBase();
//                }
//                originRelations.add(new Relation(z, p));
//            }
//        } else {
//            if(methodInfoMap.get(callee) == null) {
//                return originRelations;
//            }
//        }
//    }
//}
