package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.opti.Optimization;
import com.zstring.structs.Relation;
import com.zstring.utils.FileUtil;
import com.zstring.utils.SootUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.util.Chain;

import java.util.*;

public class RelationAnalyzer {
    public static Map<String, Set<Relation>> allRelations = new HashMap<String, Set<Relation>>();
    public static Map<String, Map<String, List<Value>>> methodInfoMap = new HashMap<String, Map<String, List<Value>>>();
    public static Set<Relation> globalRelations;
    public static List<Set<Value>> splited = new ArrayList<>();
    public static Map<Value, Set<Unit>> invocationIfCallerTypeChange = new HashMap<>();
    public static Set<Unit> globalInvocation = new HashSet<>();
//    public static Set<JAssignStmt> globalFiledLoadStmts = new HashSet<>();
    public static Map<SootField, Set<JAssignStmt>> globalfieldLoadStmts = new HashMap<>();
    public static boolean isSplit = false;
    public static String outputTxt;
    public static String SPLITTER = "::";
    public static String FILE_SUFFIX = ".txt";

    public static void main(String[] args) {
        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
        String pp = null;
        for(int i=0; i < args.length; i++) {
            switch (args[i]) {
                case "-pp": pp = args[i+1]; break;
                case "-split": isSplit = true; break;
                case "-d" : outputTxt = args[i+1]; break;
                default:
            }
        }
        if(pp == null) {
//            pp = "/home/sean/bench_compile/";
//            pp = "/home/sean/bench_compared/compress";
//            pp = "/home/sean/bench_compared/crypto";
            pp = "/home/sean/bench_compared/helloworld";
//            pp = "/home/sean/instruTest";
        }
        if(outputTxt == null) {
            outputTxt = "default.txt";
        }
        new RelationAnalyzer().analyze(cp, pp);
    }

    public void analyze(String cp, String pp) {
        SootEnvironment.init(cp, pp);
        String dotPath = "/home/sean/IdeaProjects/zString/outputDot/eclipse/";
        String[] log = new String[7];
        long gen_basic_time = 0;
        long fixpoint_time = 0;
        long t1 = new Date().getTime();
        genRelationIntra(dotPath);
        long t2 = new Date().getTime();
        gen_basic_time = t2-t1;
        globalize();
        t1 = new Date().getTime();
        calcFixPoint();
        t2 = new Date().getTime();
        fixpoint_time = t2-t1;
        log[0] = "basic:" + (gen_basic_time/1000.0);
        log[1] = "fixpoint:" + (fixpoint_time/1000.0);
        log[2] = "total_t:" + ((gen_basic_time+fixpoint_time) / 1000.0);
        log[3] = "tflow:" + Relation.type_count;
        log[4] = "partial:" + Relation.less_count;
        log[5] = "field:" + Relation.field_count;
        log[6] = "total_r:" + Relation.count;
        FileUtil.writeLog(log, "tfa-log", outputTxt);
        generateResult();
    }

    public void genRelationIntra(String dotPath) {

        Map<String, Set<Unit>> invokeStmtMap = new HashMap<String, Set<Unit>>();

        Iterator<SootMethod> mIter = SootEnvironment.allMethods.iterator();
        while(mIter.hasNext()) {
            SootMethod m = mIter.next();
            if(!m.isConcrete()) {
                continue;
            }
            Map<String, List<Value>> returnOrParam = new HashMap<String, List<Value>>();
            List<Value> params = new ArrayList<Value>();
            List<Value> returns = new ArrayList<Value>();
            Set<JAssignStmt> fieldLoadToResolve = new HashSet<JAssignStmt>();
            Set<Unit> invokeStmtSet = new HashSet<Unit>();
            Set<Relation> relationSet = new HashSet<Relation>();
            JimpleBody jb = (JimpleBody) m.retrieveActiveBody();
            Iterator<Unit> uIter = jb.getUnits().iterator();
            while(uIter.hasNext()) {
                Unit u = uIter.next();
                if(u instanceof JInvokeStmt || (u instanceof JAssignStmt && ((JAssignStmt) u).getRightOp() instanceof InvokeExpr)) {
                    invokeStmtSet.add(u);
                    globalInvocation.add(u);
                    // we will resolve invocation after basic relations have been generated.
                    continue;
                }
                if(u instanceof JReturnStmt) {
                    Value returnValue = ((JReturnStmt) u).getOp();
                    if(returnValue instanceof JArrayRef) {
                        returnValue = ((JArrayRef) returnValue).getBase();
                    }
                    returns.add(returnValue);
                }
                if(u instanceof JIdentityStmt) {
                    // Parameters & @this
                    Value left = ((JIdentityStmt) u).getLeftOp();
                    Value right = ((JIdentityStmt) u).getRightOp();
                    if(left instanceof JArrayRef) {
                        left = ((JArrayRef) left).getBase();
                    }
                    if(right instanceof JArrayRef) {
                        right = ((JArrayRef) right).getBase();
                    }
                    if(left.getType() instanceof RefType && right.getType() instanceof RefType) {
                        relationSet.add(new Relation(right, left));
                        params.add(right);
                    }

                } else if(u instanceof JAssignStmt) {
                    Value left = ((JAssignStmt) u).getLeftOp();
                    Value right = ((JAssignStmt) u).getRightOp();
                    if(left instanceof JArrayRef) {
                        left = ((JArrayRef) left).getBase();
                    }
                    if(right instanceof JArrayRef) {
                        right = ((JArrayRef) right).getBase();
                    }
                    if(right instanceof JNewArrayExpr) {
                        relationSet.add(new Relation(right, left, right.getType()));
                        Type t = ((JNewArrayExpr) right).getBaseType();
                        if(t instanceof RefType) {
                            relationSet.add(new Relation(right, left, t));
                        }
                    } else {
                        if ((left.getType() instanceof RefType || left.getType() instanceof ArrayType)
                                && (right.getType() instanceof RefType || right.getType() instanceof ArrayType)) {
                            if (left instanceof FieldRef) {
                                SootField field = ((FieldRef) left).getField();
                                if (field.isStatic()) {
                                    // TODO: static field
                                    relationSet.add(new Relation(null, right, field));
                                    //relationSet.add(new Relation(null, right, field.getType()));
                                } else {
                                    left = ((JInstanceFieldRef) left).getBase();
                                    relationSet.add(new Relation(left, right, field));
                                }
                            } else if (right instanceof FieldRef) {
                                // x = y.f
                                fieldLoadToResolve.add((JAssignStmt) u);
                                SootField field = ((FieldRef) right).getField();
                                Set<JAssignStmt> fieldLoadStmt = globalfieldLoadStmts.get(field);
                                if(fieldLoadStmt == null) {
                                    fieldLoadStmt = new HashSet<>();
                                }
                                fieldLoadStmt.add((JAssignStmt) u);
                                globalfieldLoadStmts.put(field, fieldLoadStmt);
                                // library treatment
                                if(!field.getDeclaringClass().isApplicationClass()) {
                                    if(left instanceof JArrayRef) {
                                        left = ((JArrayRef) left).getBase();
                                    }
                                    Type t = field.getType();
                                    if(t instanceof RefType) {
                                        relationSet.add(new Relation(null, left, AnySubType.v((RefType)t)));
                                    } else if(t instanceof ArrayType) {
                                        relationSet.add(new Relation(null, left, t));
                                    }
                                }
//                                globalFiledLoadStmts.add((JAssignStmt) u);

                            } else if (right instanceof JNewExpr) {
                                Type t = right.getType();
                                relationSet.add(new Relation(right, left, t));
                            } else if (left instanceof JimpleLocal) {
                                if(right instanceof JCastExpr) {
                                    right = ((JCastExpr) right).getOp();
                                }
                                relationSet.add(new Relation(right, left));
                            }
                        }
                    }
                }
            }
            returnOrParam.put("return", returns);
            returnOrParam.put("param", params);
            methodInfoMap.put(m.getSignature(), returnOrParam);
//            if(fieldLoadToResolve.size() > 0) {
//                resolveFieldLoad(relationSet, fieldLoadToResolve);
//            }
            //extendRelation(relationSet);
            invokeStmtMap.put(m.getSignature(), invokeStmtSet);
            allRelations.put(m.getSignature(), relationSet);
        }

        //resolveMethodCall(invokeStmtMap);

        // draw the graph
//        long t1 = new Date().getTime();
//        Iterator<Map.Entry<String, Set<Relation>>> relationsIter = allRelations.entrySet().iterator();
//        while(relationsIter.hasNext()) {
//            Map.Entry<String, Set<Relation>> relationsEntry = relationsIter.next();
//            drawRelation(relationsEntry.getKey(), relationsEntry.getValue(), dotPath);
//        }
//        long t2 = new Date().getTime();
//        System.out.println("drawing time used: " + (t2-t1) + "s");
    }

    public void resolveFieldLoad(Set relationSet, Set stmts) {
        Iterator<JAssignStmt> stmtIter = stmts.iterator();
        while(stmtIter.hasNext()) {
            JAssignStmt stmt = stmtIter.next();
            Value left = stmt.getLeftOp();
            if(left instanceof JArrayRef) {
                left = ((JArrayRef) left).getBase();
            }
            FieldRef right = (FieldRef) stmt.getRightOp();

            Iterator<Relation> relationIter = relationSet.iterator();
            Set<Relation> relationToAdd = new HashSet<Relation>();
            if(right.getField().isStatic()) {
                while (relationIter.hasNext()) {
                    Relation relation = relationIter.next();
                    if (relation.relationType.equals(Relation.TYPE_FIELD)
                            && relation.field.equals(right.getField())) {
                        relationToAdd.add(new Relation(relation.right, left));
                    }
                }
            } else {
                Value base = ((JInstanceFieldRef)right).getBase();
                if(base instanceof JArrayRef) {
                    base = ((JArrayRef) base).getBase();
                }
                while (relationIter.hasNext()) {
                    Relation relation = relationIter.next();
                    if (relation.relationType.equals(Relation.TYPE_FIELD)
                            && relation.left != null
                            && relation.left.equals(base)) {
                        relationToAdd.add(new Relation(relation.right, left));
                    }
                }
            }
            relationSet.addAll(relationToAdd);
        }
    }

    public void resolveInvokeIntra(Set<Unit> invokeStmts) {
        for(Unit invokeUnit : invokeStmts) {
            Value receiver = null;
            InvokeExpr invokeExpr = null;
            if(invokeUnit instanceof JAssignStmt) {
                receiver = ((JAssignStmt) invokeUnit).getLeftOp();
                invokeExpr = (InvokeExpr) ((JAssignStmt) invokeUnit).getRightOp();
            } else if(invokeUnit instanceof JInvokeStmt) {
                invokeExpr = ((JInvokeStmt) invokeUnit).getInvokeExpr();
            }

            Value caller = null;
            String calleeSub = null;
            if(invokeExpr instanceof JStaticInvokeExpr) {
                genRelationFromStaticInvoke(invokeExpr, receiver);
            } else if(invokeExpr instanceof JSpecialInvokeExpr) {
                caller = ((JSpecialInvokeExpr) invokeExpr).getBase();
                genRelationFromSpecInvoke(invokeExpr, receiver, caller);
            } else {
                if(invokeExpr instanceof JVirtualInvokeExpr) {
                    caller = ((JVirtualInvokeExpr) invokeExpr).getBase();
                    calleeSub = invokeExpr.getMethod().getSubSignature();
                } else if(invokeExpr instanceof JInterfaceInvokeExpr) {
                    try {
                        JInterfaceInvokeExpr interfaceInvokeExpr = (JInterfaceInvokeExpr) invokeExpr;
                        caller = interfaceInvokeExpr.getBase();
                        calleeSub = interfaceInvokeExpr.getMethod().getSubSignature();
                    } catch (Exception e) {
                        return;
                    }
                } else if(invokeExpr instanceof JDynamicInvokeExpr) {
                    return;
                }
                genRelationFromVirtualInvoke(invokeExpr, caller, calleeSub, receiver);
            }

//            String declaredClassName = invokeExpr.getMethodRef().getDeclaringClass().getName();
//            if(receiver != null) {
//                if(declaredClassName.startsWith("java")
//                        || declaredClassName.startsWith("sun")
//                        || declaredClassName.startsWith("com.oracle")
//                        || declaredClassName.startsWith("com.sun")
//                        || declaredClassName.startsWith("jdk")) {
//
//                    Type t = invokeExpr.getMethodRef().getReturnType();
//                    if(t instanceof RefType) {
//                        globalRelations.add(new Relation(null, receiver, AnySubType.v((RefType)t)));
//                    } else if(t instanceof ArrayType) {
//                        globalRelations.add(new Relation(null, receiver, t));
//                    }
//                }
//            }

            // keep "x.m()" so we could extend relation when the type of "x" changing.
            if(caller != null) {
                Set<Unit> invokeSet = invocationIfCallerTypeChange.get(caller);
                if(invokeSet == null) {
                    invokeSet = new HashSet<>();
                }
                invokeSet.add(invokeUnit);
                invocationIfCallerTypeChange.put(caller, invokeSet);
            }

        }
    }

    private void genRelationFromVirtualInvoke(InvokeExpr invokeExpr, Value caller, String calleeSub, Value receiver) {
        String callee = invokeExpr.getMethod().getSignature();
        if(methodInfoMap.get(callee) == null) {
            //Logger.getLogger(callee).info("can't generated relation for " + invokeExpr.toString());
            if(receiver != null) {
                Type t = invokeExpr.getMethodRef().getReturnType();
                if(t instanceof RefType) {
                    globalRelations.add(new Relation(null, receiver, AnySubType.v((RefType)t)));
                } else if(t instanceof ArrayType) {
                    globalRelations.add(new Relation(null, receiver, t));
                }
            }
            //return;
        }

        Set<Type> typeReachByCaller = Relation.typeReachByValue.get(caller);
        if(typeReachByCaller == null) {
            return;
        }
        for(Type t : typeReachByCaller) {
            callee = SootUtils.getMethodSigByType(t, calleeSub);
            if(callee == null) {
                continue;
            }
            if(methodInfoMap.get(callee) == null) {
                //Logger.getLogger(callee).info("can't generated relation for " + invokeExpr.toString());
                continue;
            }
            List<Value> params = methodInfoMap.get(callee).get("param");
            Value vThis = params.get(0);
            List<Value> returns = methodInfoMap.get(callee).get("return");
            // c --> this(c,m)
            globalRelations.add(new Relation(null, vThis, t));

            // x' (- return(c,m)
            if(receiver != null) {
                if(receiver instanceof JArrayRef) {
                    receiver = ((JArrayRef) receiver).getBase();
                }
                for(Value vReturn: returns) {
                    globalRelations.add(new Relation(vReturn, receiver));
                }
            }

            globalRelations.add(new Relation(caller, vThis));

            int paramIdx = 1;
            for(int i=0; i < invokeExpr.getArgCount(); i++) {
                Value z = invokeExpr.getArg(i);
                if(!(z.getType() instanceof RefType)) {
                    continue;
                }
                Value p = params.get(paramIdx++);      // "this" variable was store in params(0)
                if(z instanceof JArrayRef) {
                    z = ((JArrayRef) z).getBase();
                }
                if(p instanceof JArrayRef) {
                    p = ((JArrayRef) p).getBase();
                }
                globalRelations.add(new Relation(z, p));
            }
        }

    }

    private void genRelationFromSpecInvoke(InvokeExpr invokeExpr, Value receiver, Value caller) {
        String callee = invokeExpr.getMethod().getSignature();
        if(methodInfoMap.get(callee) == null) {
            //Logger.getLogger(callee).info("can't generated relation for " + invokeExpr.toString());
            if(receiver != null) {
                Type t = invokeExpr.getMethodRef().getReturnType();
                if(t instanceof RefType) {
                    globalRelations.add(new Relation(null, receiver, AnySubType.v((RefType)t)));
                } else if(t instanceof ArrayType) {
                    globalRelations.add(new Relation(null, receiver, t));
                }
            }
            return;
        }

        List<Value> params = methodInfoMap.get(callee).get("param");
        List<Value> returns = methodInfoMap.get(callee).get("return");
        Value vThis = params.get(0);
        // x' (- return(c,m)
        if(receiver != null) {
            if(receiver instanceof JArrayRef) {
                receiver = ((JArrayRef) receiver).getBase();
            }
            for(Value vReturn: returns) {
                globalRelations.add(new Relation(vReturn, receiver));
            }
        }
        int paramIdx = 1;
        for(int i=0; i < invokeExpr.getArgCount(); i++) {
            Value z = invokeExpr.getArg(i);
            if(!(z.getType() instanceof RefType)) {
                continue;
            }
            Value p = params.get(paramIdx++);      // "this" variable was store in params(0)
            if(z instanceof JArrayRef) {
                z = ((JArrayRef) z).getBase();
            }
            if(p instanceof JArrayRef) {
                p = ((JArrayRef) p).getBase();
            }
            globalRelations.add(new Relation(z, p));
        }
        globalRelations.add(new Relation(caller, vThis));

        Set<Type> typeReachByCaller = Relation.typeReachByValue.get(caller);
        if(typeReachByCaller != null) {
            for(Type t : typeReachByCaller) {
                globalRelations.add(new Relation(null, vThis, t));
            }
        }

    }

    private void genRelationFromStaticInvoke(InvokeExpr invokeExpr, Value receiver) {
        String callee = invokeExpr.getMethod().getSignature();
        if(methodInfoMap.get(callee) == null) {
            //Logger.getLogger(callee).info("can't generated relation for " + invokeExpr.toString());
            if(receiver != null) {
                Type t = invokeExpr.getMethodRef().getReturnType();
                if(t instanceof RefType) {
                    globalRelations.add(new Relation(null, receiver, AnySubType.v((RefType)t)));
                } else if(t instanceof ArrayType) {
                    globalRelations.add(new Relation(null, receiver, t));
                }
            }
            return;
        }
        List<Value> params = methodInfoMap.get(callee).get("param");
        List<Value> returns = methodInfoMap.get(callee).get("return");
        // x' (- return(c,m)
        if(receiver != null) {
            if(receiver instanceof JArrayRef) {
                receiver = ((JArrayRef) receiver).getBase();
            }
            for(Value vReturn: returns) {
                globalRelations.add(new Relation(vReturn, receiver));
            }
        }

        // parameter
        int paramIdx = 0;
        for(int i=0; i < invokeExpr.getArgCount(); i++) {
            Value z = invokeExpr.getArg(i);
            if(!(z.getType() instanceof RefType)) {
                continue;
            }
            Value p = params.get(paramIdx++);      // static method don't hold the "this" variable, so we start the index from 0
            if(z instanceof JArrayRef) {
                z = ((JArrayRef) z).getBase();
            }
            if(p instanceof JArrayRef) {
                p = ((JArrayRef) p).getBase();
            }
            globalRelations.add(new Relation(z, p));
        }
    }

    public void extendRelation(Set relationSet) {

        while(true) {
            Set<Relation> relationToAdd = new HashSet<Relation>();
            Iterator<Relation> relationIter1 = relationSet.iterator();
            while(relationIter1.hasNext()) {
                Relation relation1 = relationIter1.next();
                Iterator<Relation> relationIter2 = null;
                if(relation1.relationType.equals(Relation.TYPE_CLASS2VAR)) {
                    Set<Relation> valueToRelation = new HashSet<Relation>();
                    Set<Relation> relatedRelation = Relation.valueRelationHolder.get(relation1.right);
                    if(relatedRelation == null) {
                        continue;
                    }
                    valueToRelation.addAll(relatedRelation);
                    relationIter2 = valueToRelation.iterator();
                    Type t = relation1.type;
                    while(relationIter2.hasNext()) {
                        Relation relation2 = relationIter2.next();
                        if(relation2.relationType.equals(Relation.TYPE_VAR2VAR)
                                && relation2.left.equals(relation1.right)) {
                            Set<Type> typeToR2Right = Relation.typeReachByValue.get(relation2.right);
                            if(typeToR2Right == null || !typeToR2Right.contains(t)) {
                                relationToAdd.add(new Relation(null, relation2.right, t));
                                // type of right change
                                Set<Unit> invokeUnitByRight = invocationIfCallerTypeChange.get(relation2.right);
                                if(invocationIfCallerTypeChange.get(relation2.right) != null) {
                                    extendRelationWhenCallerTypeChange(relationToAdd, t, relation2.right, invokeUnitByRight);
                                }
                            }
                        }
                    }
                } else if(relation1.relationType.equals(Relation.TYPE_VAR2VAR)) {
                    Set<Relation> valueToRelation = new HashSet<Relation>();
                    Set<Relation> relatedRelation = Relation.valueRelationHolder.get(relation1.right);
                    if(relatedRelation == null) {
                        continue;
                    }
                    valueToRelation.addAll(relatedRelation);
                    relationIter2 = valueToRelation.iterator();
                    while(relationIter2.hasNext()) {
                        Relation relation2 = relationIter2.next();
                        if(relation2.relationType.equals(Relation.TYPE_VAR2VAR)
                                && relation1.right.equals(relation2.left)) {
                            relationToAdd.add(new Relation(relation1.left, relation2.right));
                        }
                    }
                } else if(relation1.relationType.equals(Relation.TYPE_FIELD)) {
                    if(relation1.left != null) {
                        // relation1 : x -> f -> z
                        Set<Relation> valueToRelation = new HashSet<Relation>();
                        Set<Relation> relatedRelation = Relation.partialRelationHolder.get(relation1.left);
                        if(relatedRelation == null) {
                            continue;
                        }
                        valueToRelation.addAll(relatedRelation);
                        relationIter2 = valueToRelation.iterator();
                        while (relationIter2.hasNext()) {
                            Relation relation2 = relationIter2.next();
                            if (relation2.right.equals(relation1.left)) {
                                Set<Relation> zRelation = new HashSet<>();
                                Set<Relation> zHolderRelation = Relation.partialRelationHolder.get(relation2.left);
                                if(zHolderRelation == null) {
                                    continue;
                                }
                                zRelation.addAll(zHolderRelation);
                                Iterator<Relation> zIter = zRelation.iterator();
                                Set<Value> allRight = new HashSet<>();
                                while(zIter.hasNext()) {
                                    Relation z = zIter.next();
                                    if(z.left.equals(relation2.left)) {
                                        // z : z' (= *
                                        allRight.add(z.right);
                                    }
                                }
                                allRight.remove(relation2.right);   // remove z' (= * x
                                if(allRight.size() != 0) {
                                    Iterator<Value> rightIter = allRight.iterator();
                                    while(rightIter.hasNext()) {
                                        // adding new field relation: y -> f* -> z
                                        Relation newFieldRelation = new Relation(rightIter.next(), relation1.right, relation1.field);
                                        relationToAdd.add(newFieldRelation);
                                        Set<JAssignStmt> fieldLoadIfRelationChange = globalfieldLoadStmts.get(relation1.field);
                                        if(fieldLoadIfRelationChange != null) {
                                            extendRelationWhenFieldLoadChange(relationToAdd, newFieldRelation, fieldLoadIfRelationChange);
                                        }
                                    }
                                }

                            }
                        }

                    }
                }
            }
            // extending field load statement
            for(SootField field : globalfieldLoadStmts.keySet()) {
                Set<JAssignStmt> stmts = globalfieldLoadStmts.get(field);
                for(JAssignStmt stmt : stmts) {
                    Value left = stmt.getLeftOp();
                    if(left instanceof JArrayRef) {
                        left = ((JArrayRef) left).getBase();
                    }
                    FieldRef right = (FieldRef) stmt.getRightOp();
                    Set<Value> reachValues = null;
                    if(field.isStatic()) {
                        reachValues = Relation.staticFieldReachValues.get(field);

                    } else {
                        Value y = ((JInstanceFieldRef) right).getBase();
                        if(y instanceof JArrayRef) {
                            y = ((JArrayRef) y).getBase();
                        }
                        Map<SootField, Set<Value>> fieldToValues = Relation.valueRightReachByLeftAndField.get(y);
                        if(fieldToValues != null) {
                            reachValues = fieldToValues.get(field);
                        }
                    }
                    if(reachValues != null) {
                        for (Value z : reachValues) {
                            relationToAdd.add(new Relation(z, left));
                        }
                    }
                }
            }
            int sizeBefore = relationSet.size();
            relationSet.addAll(relationToAdd);
            if(relationSet.size() == sizeBefore) {
                break;
            }
        }
    }

    public void resolveMethodCall(Map invokeStmtMap) {
        Iterator<Map.Entry<String, Set<Unit>>> invokeMapIter = invokeStmtMap.entrySet().iterator();
        while(invokeMapIter.hasNext()) {
            Map.Entry<String, Set<Unit>> invokeSet = invokeMapIter.next();
            String hostMethod = invokeSet.getKey();
            Iterator<Unit> invokeStmtIter = invokeSet.getValue().iterator();
            while(invokeStmtIter.hasNext()) {
                Unit invokeStmt = invokeStmtIter.next();
                dealInvoke(hostMethod, invokeStmt);
            }
        }
    }

    public void dealInvoke(String hostMethod, Unit invokeUnit) {
        Value receiver = null;
        InvokeExpr invokeExpr = null;
        if(invokeUnit instanceof JAssignStmt) {
            receiver = ((JAssignStmt) invokeUnit).getLeftOp();
            invokeExpr = (InvokeExpr) ((JAssignStmt) invokeUnit).getRightOp();
        } else if (invokeUnit instanceof JInvokeStmt){
            invokeExpr = ((JInvokeStmt) invokeUnit).getInvokeExpr();
        }
        Value caller = null;
        String calleeSub = null;
        if(invokeExpr instanceof JSpecialInvokeExpr) {
            JSpecialInvokeExpr specInvokeExpr = (JSpecialInvokeExpr) invokeExpr;
            caller = specInvokeExpr.getBase();
            calleeSub = specInvokeExpr.getMethod().getSubSignature();
        } else if(invokeExpr instanceof JVirtualInvokeExpr) {
            JVirtualInvokeExpr virtualInvokeExpr = (JVirtualInvokeExpr) invokeExpr;
            caller = virtualInvokeExpr.getBase();
            calleeSub = virtualInvokeExpr.getMethod().getSubSignature();
        } else if(invokeExpr instanceof JStaticInvokeExpr) {
            JStaticInvokeExpr staticInvokeExpr = (JStaticInvokeExpr) invokeExpr;
            calleeSub = staticInvokeExpr.getMethod().getSubSignature();
        } else if(invokeExpr instanceof JInterfaceInvokeExpr) {
            try {
                JInterfaceInvokeExpr interfaceInvokeExpr = (JInterfaceInvokeExpr) invokeExpr;
                caller = interfaceInvokeExpr.getBase();
                calleeSub = interfaceInvokeExpr.getMethod().getSubSignature();
            } catch (Exception e) {
                return;
            }
        } else if(invokeExpr instanceof JDynamicInvokeExpr) {
            return;
        }
        //genRelationFromInvoke(invokeExpr, hostMethod, caller, calleeSub, receiver);

        // keep "x.m()" so we could extend relation when the type of "x" changing.
        if(caller != null) {
            Set<Unit> invokeSet = invocationIfCallerTypeChange.get(caller);
            if(invokeSet == null) {
                invokeSet = new HashSet<>();
            }
            invokeSet.add(invokeUnit);
            invocationIfCallerTypeChange.put(caller, invokeSet);
        }
    }

    public void genRelationFromInvoke(InvokeExpr invokeExpr, String hostMethod, Value caller, String calleeSub, Value receiver) {
        int newRelationCount = 0;
        Set<Relation> hostRelations = allRelations.get(hostMethod);
        if(caller == null) {    // Static Invoke
            String callee = invokeExpr.getMethod().getSignature();
            if(methodInfoMap.get(callee) == null) {
                // method from outer library
                SootMethod m = invokeExpr.getMethod();
                if(m.isConcrete()) {
                    Map<String, List<Value>> returnOrParam = new HashMap<String, List<Value>>();
                    List<Value> params = new ArrayList<Value>();
                    List<Value> returns = new ArrayList<Value>();
                    Set<Relation> relationSet = new HashSet<Relation>();
                    JimpleBody jb = (JimpleBody) m.retrieveActiveBody();
                    Iterator<Unit> uIter = jb.getUnits().iterator();
                    while(uIter.hasNext()) {
                        Unit u = uIter.next();
                        if (u instanceof JReturnStmt) {
                            Value returnValue = ((JReturnStmt) u).getOp();
                            if(returnValue instanceof JArrayRef) {
                                returnValue = ((JArrayRef) returnValue).getBase();
                            }
                            returns.add(returnValue);
                        }
                        if (u instanceof JIdentityStmt) {
                            // Parameters & @this
                            Value left = ((JIdentityStmt) u).getLeftOp();
                            Value right = ((JIdentityStmt) u).getRightOp();
                            if(left instanceof JArrayRef) {
                                left = ((JArrayRef) left).getBase();
                            }
                            if(right instanceof JArrayRef) {
                                right = ((JArrayRef) right).getBase();
                            }
                            relationSet.add(new Relation(right, left));
                            params.add(left);
                        }
                    }
                    returnOrParam.put("return", returns);
                    returnOrParam.put("param", params);
                    methodInfoMap.put(m.getSignature(), returnOrParam);
                    allRelations.put(m.getSignature(), relationSet);
                } else {
                    return;
                }
            }
            List<Value> params = methodInfoMap.get(callee).get("param");
            List<Value> returns = methodInfoMap.get(callee).get("return");
            Set<Relation> calleeRelations = allRelations.get(callee);
            // x' (- return(c,m)
            if(receiver != null) {
                if(receiver instanceof JArrayRef) {
                    receiver = ((JArrayRef) receiver).getBase();
                }
                for(Value vReturn: returns) {
                    calleeRelations.add(new Relation(vReturn, receiver));
                    newRelationCount++;
                }
            }

            for(int i=0; i < invokeExpr.getArgCount(); i++) {
                Value z = invokeExpr.getArg(i);
                if(!(z.getType() instanceof RefType)) {
                    continue;
                }
                Value p = params.get(i);      // static method don't hold the "this" variable, so we start the index from 0
                if(z instanceof JArrayRef) {
                    z = ((JArrayRef) z).getBase();
                }
                if(p instanceof JArrayRef) {
                    p = ((JArrayRef) p).getBase();
                }
                calleeRelations.add(new Relation(z, p));
                newRelationCount++;
            }
            allRelations.put(callee, calleeRelations);
        } else {
            List<Type> typesToCaller = new ArrayList<Type>();
            Iterator<Relation> rIter = hostRelations.iterator();
            while(rIter.hasNext()) {
                Relation r = rIter.next();
                if(r.relationType.equals(Relation.TYPE_CLASS2VAR) && r.right.equals(caller)) {
                    typesToCaller.add(r.type);
                }
            }
            for(Type t: typesToCaller) {
                String callee = null;
                if(invokeExpr instanceof JSpecialInvokeExpr) {
                    // TODO: deal with the "super" reference
                    callee = invokeExpr.getMethod().getSignature();
                } else {
                    callee = SootUtils.getMethodSigByType(t, calleeSub);
                }
                if(callee == null) {
                    continue;
                }
                if(methodInfoMap.get(callee) == null) {
                    continue;
                }
                List<Value> params = methodInfoMap.get(callee).get("param");
                Value vThis = params.get(0);
                List<Value> returns = methodInfoMap.get(callee).get("return");
                Set<Relation> calleeRelations = allRelations.get(callee);
                // c --> this(c,m)
                calleeRelations.add(new Relation(null, vThis, t));
                newRelationCount++;

                // x' (- return(c,m)
                if(receiver != null) {
                    if(receiver instanceof JArrayRef) {
                        receiver = ((JArrayRef) receiver).getBase();
                    }
                    for(Value vReturn: returns) {
                        calleeRelations.add(new Relation(vReturn, receiver));
                        newRelationCount++;
                    }
                }

                for(int i=0; i < invokeExpr.getArgCount(); i++) {
                    Value z = invokeExpr.getArg(i);
                    if(!(z.getType() instanceof RefType)) {
                        continue;
                    }
                    Value p = params.get(i+1);      // "this" variable was store in params(0)
                    if(z instanceof JArrayRef) {
                        z = ((JArrayRef) z).getBase();
                    }
                    if(p instanceof JArrayRef) {
                        p = ((JArrayRef) p).getBase();
                    }
                    calleeRelations.add(new Relation(z, p));
                    newRelationCount++;
                }
                allRelations.put(callee, calleeRelations);
            }
        }

//        System.out.println("added " + newRelationCount + " relations from invocation");
    }

    public void calcFixPoint() {
        resolveInvokeIntra(globalInvocation);
        extendRelation(globalRelations);
    }

    public void globalize() {
        globalRelations = new HashSet<Relation>();
        globalRelations.addAll(Relation.globalRelations);
    }

    public void minimalization() {
        // merge by SCC
        Set<Value> groupUnMergeBySCC = new HashSet<>();
        List<Set<Value>> mergeGroupBySCC = Optimization.mergeSCC(Relation.globalValues, groupUnMergeBySCC);

        // merge by Type
        List<Set<Value>> groupMergeByType = new LinkedList<>();
        List<Set<Value>> groupsUnMergeByType = new LinkedList<>();
        Set<Value> groupUnMergeByType = null;
        // deal with the groups merged by SCC
        for (int i = 0; i < mergeGroupBySCC.size(); i++) {
            Set<Value> singleGroup = mergeGroupBySCC.get(i);
            groupUnMergeByType = new HashSet<>();
            groupMergeByType.addAll(Optimization.mergeByType(singleGroup, groupUnMergeByType, true));
            if(!groupUnMergeByType.isEmpty()) {
                groupsUnMergeByType.add(groupUnMergeByType);
            }
        }
        // deal with the group which can not be merged by SCC
        groupUnMergeByType = new HashSet<>();
        groupMergeByType.addAll(Optimization.mergeByType(groupUnMergeBySCC, groupUnMergeByType, false));
        if(!groupUnMergeByType.isEmpty()) {
            groupsUnMergeByType.add(groupUnMergeByType);
        }
        mergeGroupBySCC = null;

        // merge by field
        Set<Value> groupUnMergeByField = null;
        // deal with the groups merged by type
        for (int i = 0; i < groupMergeByType.size(); i++) {
            Set<Value> singleGroup = groupMergeByType.get(i);
            groupUnMergeByField = new HashSet<>();
            splited.addAll(Optimization.mergeByField(singleGroup, groupUnMergeByField, true));
            for(Value finalUnMerge : groupUnMergeByField) {
                Set<Value> valueHolder = new HashSet<>();
                valueHolder.add(finalUnMerge);
                splited.add(valueHolder);
            }
        }
        // deal with the groups which can not be merged by type
        for (int i = 0; i < groupsUnMergeByType.size(); i++) {
            Set<Value> singleGroup = groupsUnMergeByType.get(i);
            groupUnMergeByField = new HashSet<>();
            splited.addAll(Optimization.mergeByField(singleGroup, groupUnMergeByField, false));
            for(Value finalUnMerge : groupUnMergeByField) {
                Set<Value> valueHolder = new HashSet<>();
                valueHolder.add(finalUnMerge);
                splited.add(valueHolder);
            }
        }

//        splited.addAll(mergeGroupByField);
//        for(int i = 0; i < mergeGroupByType.size(); i++) {
//            group = mergeGroupByType.remove(i);
//            mergeGroupByField.addAll(Optimization.mergeByField(group));
//        }

//        splitIter(splited, Relation.globalValues);
    }

    public void extendRelationWhenCallerTypeChange(Set<Relation> extendSet, Type t, Value caller, Set<Unit> invocations) {
        for (Unit invokeUnit: invocations) {
            Value receiver = null;
            InvokeExpr invokeExpr = null;
            if (invokeUnit instanceof JAssignStmt) {
                receiver = ((JAssignStmt) invokeUnit).getLeftOp();
                invokeExpr = (InvokeExpr) ((JAssignStmt) invokeUnit).getRightOp();
            } else if (invokeUnit instanceof JInvokeStmt) {
                invokeExpr = ((JInvokeStmt) invokeUnit).getInvokeExpr();
            }
            String calleeSub = null;
            if (invokeExpr instanceof JSpecialInvokeExpr) {
                JSpecialInvokeExpr specInvokeExpr = (JSpecialInvokeExpr) invokeExpr;

                calleeSub = specInvokeExpr.getMethod().getSubSignature();
            } else if (invokeExpr instanceof JVirtualInvokeExpr) {
                JVirtualInvokeExpr virtualInvokeExpr = (JVirtualInvokeExpr) invokeExpr;
                calleeSub = virtualInvokeExpr.getMethod().getSubSignature();
            } else if (invokeExpr instanceof JStaticInvokeExpr) {
                JStaticInvokeExpr staticInvokeExpr = (JStaticInvokeExpr) invokeExpr;
                calleeSub = staticInvokeExpr.getMethod().getSubSignature();
            } else if (invokeExpr instanceof JInterfaceInvokeExpr) {
                try {
                    JInterfaceInvokeExpr interfaceInvokeExpr = (JInterfaceInvokeExpr) invokeExpr;
                    calleeSub = interfaceInvokeExpr.getMethod().getSubSignature();
                } catch (Exception e) {
                    continue;
                }
            } else if (invokeExpr instanceof JDynamicInvokeExpr) {
                continue;
            }

            String callee = null;
            if(invokeExpr instanceof JSpecialInvokeExpr) {
                callee = invokeExpr.getMethod().getSignature();
            } else {
                callee = SootUtils.getMethodSigByType(t, calleeSub);
            }
            if(callee == null) {
                continue;
            }
            if(methodInfoMap.get(callee) == null) {
                continue;
            }
            List<Value> params = methodInfoMap.get(callee).get("param");
            Value vThis = params.get(0);
            List<Value> returns = methodInfoMap.get(callee).get("return");
//            Set<Relation> calleeRelations = allRelations.get(callee);
            // c --> this(c,m)
            Set<Type> typeTovThis = Relation.typeReachByValue.get(vThis);
            if(typeTovThis == null) {
                typeTovThis = new HashSet<>();
            }
            if(!typeTovThis.contains(t)) {
                extendSet.add(new Relation(null, vThis, t));
                Set<Unit> invokeByVThis = invocationIfCallerTypeChange.get(vThis);
                if(invokeByVThis != null) {
                    extendRelationWhenCallerTypeChange(extendSet, t, vThis, invokeByVThis);
                }
            }

            // x' (- return(c,m)
            if(receiver != null) {
                if(receiver instanceof JArrayRef) {
                    receiver = ((JArrayRef) receiver).getBase();
                }
                for(Value vReturn: returns) {
                    extendSet.add(new Relation(vReturn, receiver));
                }
            }
            int paramIdx = 1;
            for(int i=0; i < invokeExpr.getArgCount(); i++) {
                Value z = invokeExpr.getArg(i);
                if(!(z.getType() instanceof RefType)) {
                    continue;
                }
                Value p = params.get(paramIdx++);      // "this" variable was store in params(0)
                if(z instanceof JArrayRef) {
                    z = ((JArrayRef) z).getBase();
                }
                if(p instanceof JArrayRef) {
                    p = ((JArrayRef) p).getBase();
                }
                extendSet.add(new Relation(z, p));
            }
            extendSet.add(new Relation(caller, vThis));
        }
    }

    private void extendRelationWhenFieldLoadChange(Set<Relation> extendSet, Relation newFieldRelation, Set<JAssignStmt> fieldLoadIfRelationChange) {
        Iterator<JAssignStmt> stmtIter = fieldLoadIfRelationChange.iterator();
        while(stmtIter.hasNext()) {
            JAssignStmt stmt = stmtIter.next();
            Value x = stmt.getLeftOp();
            if (x instanceof JArrayRef) {
                x = ((JArrayRef) x).getBase();
            }
            Value y = stmt.getRightOp();
            if (y instanceof JArrayRef) {
                y = ((JArrayRef) y).getBase();
            }
            Value z = newFieldRelation.right;

            extendSet.add(new Relation(z, x));
        }
    }

    private int calcCallSite() {
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
                        Set<Type> typeReach = Relation.typeReachByValue.get(caller);
                        if(typeReach == null || typeReach.isEmpty()) {
                            callsites++;
                        } else {
                            for(Type t : typeReach) {
                                SootClass c = Scene.v().getSootClass(t.toString());
                                try {
                                    SootMethod sm = c.getMethod(invokeExpr.getMethod().getSubSignature());
                                    if(sm.isConcrete()) {
                                        callsites++;
                                    }
                                } catch (Exception e) {
//                                    e.printStackTrace();
                                }
                            }
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

    private int calcOriginalNodes() {
        int nodes = 0;
        nodes = Relation.globalValues.size();
        System.out.println("Nodes original: " + nodes);
        return nodes;
    }

    private int calcMinNodes() {
        int nodes = 0;
        nodes = splited.size();
        System.out.println("Nodes after min: " + nodes);
        return nodes;
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
                        Set<Type> reachTypes = Relation.typeReachByValue.get(receiver);
                        if(reachTypes == null || reachTypes.isEmpty()) {
                            continue;
                        }
                        for(Type t : reachTypes) {
                            if(t instanceof RefType) {
                                SootClass c = ((RefType) t).getSootClass();
                                writeLine = recordVirtualCallPrefix + SPLITTER + c.getName() + SPLITTER +receiver + SPLITTER + invokeExpr.getMethod().getSubSignature() + SPLITTER + lineNum;
                                data2Write.add(writeLine);
                            } else if (t instanceof AnySubType) {
                                SootClass c = ((AnySubType) t).getBase().getSootClass();
                                writeLine = recordVirtualCallPrefix + SPLITTER + c.getName() + SPLITTER +receiver + SPLITTER + invokeExpr.getMethod().getSubSignature() + SPLITTER + lineNum;
                                data2Write.add(writeLine);
                                writeLine = recordVirtualCallPrefix + SPLITTER + "any_subtype_of" + SPLITTER + c.getName() + SPLITTER +receiver + SPLITTER + invokeExpr.getMethod().getSubSignature() + SPLITTER + lineNum;
                                data2Write.add(writeLine);
                            }
                        }
                    }
                }
                if(!data2Write.isEmpty()) {
                    filenameMap.put(fileIdx, m.getSignature());
                    FileUtil.writeStaticResult(data2Write,  "tfa-result", fileIdx + FILE_SUFFIX);
                    fileIdx++;
                }
            }
            FileUtil.writeMap(filenameMap, "tfa-result", "map.txt");
        }
    }

}
