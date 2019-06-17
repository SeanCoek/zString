package com.oldcode;

import com.zstring.structs.Relation;
import com.zstring.utils.SootUtils;
import soot.RefType;
import soot.SootField;
import soot.Type;
import soot.Value;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JVirtualInvokeExpr;

import java.util.*;

public class OldCode {

//    public void dealInvokeInAssign(String hostMethod, JAssignStmt assignStmt) {
//        int count = 0;
//        Value left = assignStmt.getLeftOp();
//        JVirtualInvokeExpr invokeExpr = (JVirtualInvokeExpr) (assignStmt.getRightOp());
//        Value caller = invokeExpr.getBase();
//        String calleeSub = invokeExpr.getMethod().getSubSignature();
//        Set<Relation> hostRelations = allRelations.get(hostMethod);
//        List<Type> typesToCaller = new ArrayList<Type>();
//        Iterator<Relation> rIter = hostRelations.iterator();
//        while(rIter.hasNext()) {
//            Relation r = rIter.next();
//            if(r.relationType.equals(Relation.TYPE_CLASS2VAR) && r.right.equals(caller)) {
//                typesToCaller.add(r.type);
//            }
//        }
//        for(Type t: typesToCaller) {
//            String callee = SootUtils.getMethodSigByType(t, calleeSub);
//            List<Value> params = methodInfoMap.get(callee).get("param");
//            Value vThis = params.get(0);
//            List<Value> returns = methodInfoMap.get(callee).get("return");
//            Set<Relation> calleeRelations = allRelations.get(callee);
//            // c --> this(c,m)
//            calleeRelations.add(new Relation(null, vThis, t));
//            count++;
//            // x' (- return(c,m)
//            for(Value vReturn: returns) {
//                calleeRelations.add(new Relation(vReturn, left));
//                count++;
//            }
//            for(int i=0; i < invokeExpr.getArgCount(); i++) {
//                Value z = invokeExpr.getArg(i);
//                Value p = params.get(i+1);      // "this" variable was store in params(0)
//                calleeRelations.add(new Relation(z, p));
//                count++;
//            }
//            allRelations.put(callee, calleeRelations);
//        }
//        System.out.println("added " + count + " relations from invocation");
//    }

    public void split(List<Set> splited, Set<Value> obj, Set usedSplitter) {

        Set include = new HashSet();
        Set exclude = new HashSet();
        Object splitter = null;

        if(obj.size() == 1) {
            return;
        }

        // find a splitter
        Iterator<Value> vIter1 = obj.iterator();
        while(vIter1.hasNext()) {
            Value v = vIter1.next();
            Set<Relation> vRelations = Relation.valueRelationHolder.get(v);
            Iterator<Relation> rIter = vRelations.iterator();
            while(rIter.hasNext()) {
                Relation r = rIter.next();
                if(r.relationType.equals(Relation.TYPE_FIELD)) {
                    splitter = r.field;
                    if(!usedSplitter.contains(splitter)) {
                        usedSplitter.add(splitter);
                        break;
                    }
                    splitter = r.right;
                    if(!usedSplitter.contains(splitter)) {
                        usedSplitter.add(splitter);
                        break;
                    }
                } else if(r.relationType.equals(Relation.TYPE_VAR2VAR)) {
                    splitter = r.right;
                    if(!usedSplitter.contains(splitter)) {
                        usedSplitter.add(splitter);
                        break;
                    }
                }
            }
            if(splitter != null) {
                break;
            }
        }

        if(splitter == null) {
            return;
        }

        // split
        vIter1 = obj.iterator();
        while(vIter1.hasNext()) {
            Value v = vIter1.next();
            Set<Relation> vRelations = Relation.valueRelationHolder.get(v);
            Iterator<Relation> rIter = vRelations.iterator();
            while(rIter.hasNext()) {
                Relation r = rIter.next();
                if(splitter instanceof SootField) {
                    if(r.relationType.equals(Relation.TYPE_FIELD) && r.field.equals(splitter)) {
                        include.add(v);
                        break;
                    }
                }else {
                    if(!r.relationType.equals(Relation.TYPE_CLASS2VAR)) {
                        include.add(v);
                        break;
                    }
                }
            }

        }
        Set objCopy = new HashSet();
        objCopy.addAll(obj);
        objCopy.removeAll(include);
        exclude.addAll(obj);
        splited.add(include);
        splited.add(exclude);
        split(splited, include, usedSplitter);
        split(splited, exclude, usedSplitter);

    }

//    public void splitRelationToTypeAndValue() {
//        Iterator<Relation> rIter = globalRelations.iterator();
//        while(rIter.hasNext()) {
//            Relation r = rIter.next();
//            if(r.relationType.equals(Relation.TYPE_CLASS2VAR)) {
//                if(r.type instanceof RefType) {
//                    Set<Relation> typeRelations = typeRelationHolder.get(r.type);
//                    if (typeRelations == null) {
//                        typeRelations = new HashSet<Relation>();
//                    }
//                    typeRelations.add(r);
//                    typeRelationHolder.put(r.type, typeRelations);
//                    Set<Relation> valueRelations = valueRelationHolder.get(r.right);
//                    if (valueRelations == null) {
//                        valueRelations = new HashSet<Relation>();
//                    }
//                    valueRelations.add(r);
//                    valueRelationHolder.put(r.right, valueRelations);
//                }
//            } else {
//                if(r.left != null && r.left.getType() instanceof RefType && r.right.getType() instanceof RefType) {
//                    Set<Relation> valueRelations = null;
//                    valueRelations = valueRelationHolder.get(r.left);
//                    if (valueRelations == null) {
//                        valueRelations = new HashSet<Relation>();
//                    }
//                    valueRelations.add(r);
//                    valueRelationHolder.put(r.left, valueRelations);
//                    valueRelations = valueRelationHolder.get(r.right);
//                    if (valueRelations == null) {
//                        valueRelations = new HashSet<Relation>();
//                    }
//                    valueRelations.add(r);
//                    valueRelationHolder.put(r.right, valueRelations);
//                } else if(r.left == null && r.right.getType() instanceof RefType) {
//                    Set<Relation> valueRelations = valueRelationHolder.get(r.right);
//                    if (valueRelations == null) {
//                        valueRelations = new HashSet<Relation>();
//                    }
//                    valueRelations.add(r);
//                    valueRelationHolder.put(r.right, valueRelations);
//                }
//            }
//        }
//    }
}
