package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.opti.Optimization;
import com.zstring.structs.Relation;
import com.zstring.structs.Splitter;
import com.zstring.structs.Transition;
import com.zstring.utils.FileUtil;
import com.zstring.utils.SootUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RelationAnalyzer {
    public static Map<String, Set<Relation>> allRelations = new HashMap<String, Set<Relation>>();
    public static Map<String, Map<String, List<Value>>> methodInfoMap = new HashMap<String, Map<String, List<Value>>>();
    public static Set<Relation> globalRelations;
    public static List<Set<Value>> splited = new ArrayList<>();
    public static Map<Value, Set<Unit>> invocationIfCallerTypeChange = new HashMap<>();
    public static boolean isSplit = false;
    public static String outputTxt;

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
            pp = "/home/sean/bench_compared/bootstrap.jar";
        }
        if(outputTxt == null) {
            outputTxt = "default.txt";
        }
        new RelationAnalyzer().analyze(cp, pp);
    }

    public void analyze(String cp, String pp) {
        // R(type)[0], R(less)[1], R(field)[2], T(r)[3], T(m)[4], N(o)[5], N(m)[6]
        String[] dataOutput = new String[7];
        SootEnvironment.init(cp, pp);
        String dotPath = "/home/sean/IdeaProjects/zString/outputDot/eclipse/";
        long t1 = new Date().getTime();
        generateRelation(dotPath);
        globalize();
        calcFixPoint();
        long t2 = new Date().getTime();
        System.out.println("generated " + Relation.count + " relations");
        System.out.println("Relation Resolve time used: " + (t2-t1)/1000.0 + "s");


        dataOutput[3] = String.valueOf((t2-t1)/1000.0);
        calcCallSite();
        dataOutput[5] = String.valueOf(calcOriginalNodes());
        if(isSplit) {
            t1 = new Date().getTime();
            minimalization();
            t2 = new Date().getTime();
            System.out.println("minimalization time used: " + (t2-t1)/1000.0 + "s");
            dataOutput[4] = String.valueOf((t2-t1)/1000.0);
        }
        dataOutput[6] = String.valueOf(calcMinNodes());
        dataOutput[0] = String.valueOf(Relation.type_count);
        dataOutput[1] = String.valueOf(Relation.less_count);
        dataOutput[2] = String.valueOf(Relation.field_count);

        FileUtil.writeResult(dataOutput, outputTxt);

//        System.out.println("Total time used: " + (t2-t1) + "ms");
//        drawRelation(globalValueMap, globalTypeMap, globalTransition, dotPath);
    }

    public void generateRelation(String dotPath) {

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
                    relationSet.add(new Relation(right, left));
                    params.add(left);
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
                        Type t = right.getType();
                        relationSet.add(new Relation(right, left, t));
                    } else {
                        if ((left.getType() instanceof RefType || left.getType() instanceof ArrayType)
                                && (right.getType() instanceof RefType || right.getType() instanceof ArrayType)) {
                            if (left instanceof FieldRef) {
                                SootField field = ((FieldRef) left).getField();
                                if (field.isStatic()) {
                                    // TODO: static field
                                    relationSet.add(new Relation(null, right, field));
                                    relationSet.add(new Relation(null, right, field.getType()));
                                } else {
                                    left = ((JInstanceFieldRef) left).getBase();
                                    relationSet.add(new Relation(left, right, field));
                                }
                            } else if (right instanceof FieldRef) {
                                // x = y.f
                                fieldLoadToResolve.add((JAssignStmt) u);
                            } else if (right instanceof JNewExpr) {
                                Type t = right.getType();
                                relationSet.add(new Relation(right, left, t));
                            } else if (left instanceof JimpleLocal) {
                                relationSet.add(new Relation(right, left));
                            }
                        }
                    }
                }
            }
            returnOrParam.put("return", returns);
            returnOrParam.put("param", params);
            methodInfoMap.put(m.getSignature(), returnOrParam);
            if(fieldLoadToResolve.size() > 0) {
                resolveFieldLoad(relationSet, fieldLoadToResolve);
            }
//            extendRelation(relationSet);
            invokeStmtMap.put(m.getSignature(), invokeStmtSet);
            allRelations.put(m.getSignature(), relationSet);
        }

        resolveMethodCall(invokeStmtMap);

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

    public void extendRelation(Set relationSet) {

        while(true) {
            int compCount = 0;
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
                        compCount++;
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
                        compCount++;
                    }
                } else if(relation1.relationType.equals(Relation.TYPE_FIELD)) {
                    if(relation1.left != null) {
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
                                        allRight.add(z.right);
                                    }
                                }
                                allRight.remove(relation2.right);
                                if(allRight.size() != 0) {
                                    Iterator<Value> rightIter = allRight.iterator();
                                    while(rightIter.hasNext()) {
                                        relationToAdd.add(new Relation(rightIter.next(), relation1.right, relation1.field));
                                    }
                                }

                            }
                            compCount++;
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
        genRelationFromInvoke(invokeExpr, hostMethod, caller, calleeSub, receiver);

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
                String callee = SootUtils.getMethodSigByType(t, calleeSub);
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
//        System.out.println("here");

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


            String callee = SootUtils.getMethodSigByType(t, calleeSub);
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
            if(typeTovThis == null || !typeTovThis.contains(t)) {
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

            for(int i=0; i < invokeExpr.getArgCount(); i++) {
                Value z = invokeExpr.getArg(i);
                Value p = params.get(i+1);      // "this" variable was store in params(0)
                if(z instanceof JArrayRef) {
                    z = ((JArrayRef) z).getBase();
                }
                if(p instanceof JArrayRef) {
                    p = ((JArrayRef) p).getBase();
                }
                extendSet.add(new Relation(z, p));
            }
        }
    }

    public void splitIter(List splitted, Set<Value> obj) {
//        int round = 0;

        Queue<Set<Value>> wokerQueue = new ConcurrentLinkedQueue<>();
        Queue<Set<Splitter>> usedSpliiterQueue = new ConcurrentLinkedQueue<>();
        wokerQueue.add(obj);
        usedSpliiterQueue.add(new HashSet<>());
        obj = null;
        List<Set<Value>> currentSplittedGroup = null;
        while(!wokerQueue.isEmpty()) {
//            round++;
            Set<Value> valueToSplit = wokerQueue.poll();
            Set<Splitter> curRoundUsedSplitter = usedSpliiterQueue.poll();
            if(valueToSplit.size() == 1) {
                splitted.add(valueToSplit);
                valueToSplit = null;
                continue;
            }

            // find a splitter
            Splitter splitter = null;
            boolean splitFlag = false;
            Set<Value> include = null;
            Set<Value> exclude = null;
            Iterator<Value> vIter1 = valueToSplit.iterator();
            while (vIter1.hasNext()) {
                Value v = vIter1.next();
                include = new HashSet<>();
                exclude = new HashSet<>();

                Set<Relation> vRelations = Relation.valueRelationHolder.get(v);
                if(vRelations == null) {
                    // this means value v has no one relation out.
                    include = null;
                    exclude = null;
                    continue;
                } else {
                    Iterator<Relation> rIter = vRelations.iterator();
                    while (rIter.hasNext()) {
                        Relation r = rIter.next();
                        if (r.relationType.equals(Relation.TYPE_FIELD)) {
                            splitter = new Splitter(Splitter.SplitterType.TYPE_FIELD, null, r.field, r.left, r.right);
                        } else if (r.relationType.equals(Relation.TYPE_VAR2VAR)) {
                            splitter = new Splitter(Splitter.SplitterType.TYPE_PARTIAL, null, null, r.left, r.right);
                        } else {
                            splitter = new Splitter(Splitter.SplitterType.TYPE_CLASS, r.type, null, null, r.right);
                        }
                        if (!curRoundUsedSplitter.contains(splitter)) {
                            curRoundUsedSplitter.add(splitter);
                            if (split(valueToSplit, splitter, include, exclude, curRoundUsedSplitter)) {
                                splitFlag = true;
                                break;
                            }
                        }
//
                    }
                }
                if(splitFlag) {
//                    if(round > 1000) {
//                        System.out.println(round);
//                    }
                    if(include.size() == 1) {
                        splitted.add(include);
                        wokerQueue.add(exclude);
                        usedSpliiterQueue.add(curRoundUsedSplitter);
                    } else if(exclude.size() == 1) {
                        splitted.add(exclude);
                        wokerQueue.add(include);
                        usedSpliiterQueue.add(curRoundUsedSplitter);
                    } else {
                        Set curRoundUsedSpliiterCopy = new HashSet();
                        curRoundUsedSpliiterCopy.addAll(curRoundUsedSplitter);
                        wokerQueue.add(include);
                        wokerQueue.add(exclude);
                        usedSpliiterQueue.add(curRoundUsedSplitter);
                        usedSpliiterQueue.add(curRoundUsedSpliiterCopy);
                        curRoundUsedSpliiterCopy = null;
                    }
                    valueToSplit = null;
                    curRoundUsedSplitter = null;
                    currentSplittedGroup = null;
                    break;
                }
            }
            if (!splitFlag) {
                // cannot be split anymore
                splitted.add(valueToSplit);
                valueToSplit = null;
                continue;
            }
        }
//        System.out.println("split ended. process " + round + " rounds");

    }

    private boolean split(Set<Value> valueToSplit, List<Set<Value>> currentSplitGroup, Object splitter, Object splitAssist, Set<Value> include, Set<Value> exclude) {
        Iterator<Value> vIter = valueToSplit.iterator();
        int round = 0;
        while (vIter.hasNext()) {
            round++;
            Value v = vIter.next();
            Set<Relation> vRelations = null;
            if(splitter instanceof SootField) {
                vRelations = Relation.fieldRelationHolder.get(v);
            } else if(splitter instanceof Type) {
                vRelations = Relation.typeRelationHolder.get(v);
            } else {
                vRelations = Relation.partialRelationHolder.get(v);
            }
            if(vRelations == null) {
                continue;
            }
//            Set<Relation> vRelations = Relation.valueRelationHolder.get(v);
            Iterator<Relation> rIter = vRelations.iterator();
            while (rIter.hasNext()) {
                Relation r = rIter.next();
                if (splitter instanceof SootField
                        && r.relationType.equals(Relation.TYPE_FIELD)
                        && r.field.equals(splitter)) {
//                        Iterator<Set<Value>> curGroupIter = currentSplitGroup.iterator();
//                        while (curGroupIter.hasNext()) {
//                            Set<Value> group = curGroupIter.next();
//                            // check if the right-hand-side of these two relation in the same group
//                            if(group.contains(r.right) && group.contains(splitAssist)) {
//                                include.add(v);
//                                break;
//                            }
//                        }
                    if(r.right.equals(splitAssist)) {
                        include.add(v);
                        break;
                    }
                } else if(splitter instanceof Type
                            && r.relationType.equals(Relation.TYPE_CLASS2VAR)
                            && r.type.equals(splitter)) {
                        include.add(v);
                        break;
                } else {
                    if (r.relationType.equals(Relation.TYPE_VAR2VAR) && r.right.equals(splitter)) {
//                        Iterator<Set<Value>> curGroupIter = currentSplitGroup.iterator();
//                        while (curGroupIter.hasNext()) {
//                            Set<Value> group = curGroupIter.next();
//                            // check if the right-hand-side of these two relation in the same group
//                            if(group.contains(r.right) && group.contains(splitter)) {
//                                include.add(v);
//                                break;
//                            }
//                        }
                        include.add(v);
                        break;
                    }
                }
            }
        }
        System.out.println(valueToSplit.size() + " ?= " + round);
        if(include.size() == valueToSplit.size() || include.size() == 0) {
            return false;
        }
        exclude.addAll(valueToSplit);
        exclude.removeAll(include);
        return true;
    }

    private boolean split(Set<Value> valueToSplit, Splitter splitter, Set<Value> include, Set<Value> exclude, Set<Splitter> curUsedSplitter) {
//        int round = 1;
        int increSplitterNum = 0;
        if(splitter.splitType == Splitter.SplitterType.TYPE_FIELD) {
            if(splitter.field.isStatic()) {
                Set<Value> staticReach = Relation.staticFieldReachValues.get(splitter.field);
                assert(staticReach != null);
                staticReach.retainAll(valueToSplit);
                assert(staticReach.contains(splitter.right));
                include.addAll(staticReach);

                // update used splitter
                if(staticReach.size() != 1) {
                    Iterator<Value> vIter = staticReach.iterator();
                    while (vIter.hasNext()) {
                        Value reach = vIter.next();
                        Splitter asscSplitter = new Splitter(Splitter.SplitterType.TYPE_FIELD, null, splitter.field, null, reach);
                        curUsedSplitter.add(asscSplitter);
//                        increSplitterNum++;
                    }
                }
            } else {
                Set<Value> valueReachLeft = Relation.valueLeftReachByField.get(splitter.field);
                assert (valueReachLeft != null);
                valueReachLeft.retainAll(valueToSplit);
                assert (valueReachLeft.contains(splitter.left));
                if (valueReachLeft.size() == 1) {
                    include.addAll(valueReachLeft);
                } else {
                    Iterator<Value> vIter = valueReachLeft.iterator();
                    while (vIter.hasNext()) {
                        Value left = vIter.next();
//                    round++;
                        Set<Relation> vRelations = Relation.fieldRelationHolder.get(left);
                        if (vRelations != null) {
                            Iterator<Relation> rIter = vRelations.iterator();
                            while (rIter.hasNext()) {
                                Relation r = rIter.next();
                                if (r.field.equals(splitter.field) && r.right.equals(splitter.right)) {
                                    include.add(left);
                                    Splitter asscSplitter = new Splitter(Splitter.SplitterType.TYPE_FIELD, null, splitter.field, r.left, r.right);
                                    curUsedSplitter.add(asscSplitter);
//                                    increSplitterNum++;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

        } else if(splitter.splitType == Splitter.SplitterType.TYPE_CLASS) {
            Set<Value> reachValues = Relation.valueReachByType.get(splitter.type);
            if (reachValues != null) {
                reachValues.retainAll(valueToSplit);
                include.addAll(reachValues);
                if(reachValues.size() != 1) {
                    Iterator<Value> vIter = reachValues.iterator();
                    while (vIter.hasNext()) {
                        Value reach = vIter.next();
                        Splitter asscSplitter = new Splitter(Splitter.SplitterType.TYPE_CLASS, splitter.type, null, null, reach);
                        curUsedSplitter.add(asscSplitter);
//                        increSplitterNum++;
                    }
                }
            }
        } else {
            // in this case the splitter would be the right-hand-side of a partial order relations
            Set<Value> reachValues = Relation.partialReachByRight.get(splitter.right);
            if(reachValues != null) {
                reachValues.retainAll(valueToSplit);
                include.addAll(reachValues);
                if(reachValues.size() != 1) {
                    Iterator<Value> vIter = reachValues.iterator();
                    while (vIter.hasNext()) {
                        Value reach = vIter.next();
                        Splitter asscSplitter = new Splitter(Splitter.SplitterType.TYPE_PARTIAL, null, null, reach, splitter.right);
                        curUsedSplitter.add(asscSplitter);
//                        increSplitterNum++;
                    }
                }
            }
        }
//        System.out.println("increSplitterNum = " + increSplitterNum);
//        System.out.println(valueToSplit.size() + " ?= " + round);
        assert(include.size() != 0);
        if(include.size() == valueToSplit.size()) {
            return false;
        }
        exclude.addAll(valueToSplit);
        exclude.removeAll(include);
        return true;
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
                        Set<Relation> callerRelations = Relation.valueRelationHolder.get(caller);
                        if(callerRelations != null) {
                            Iterator<Relation> rIter = callerRelations.iterator();
                            while(rIter.hasNext()) {
                                Relation r = rIter.next();
                                if(r.relationType.equals(Relation.TYPE_CLASS2VAR)) {
                                    SootClass c = Scene.v().getSootClass(r.type.toString());
                                    try {
                                        SootMethod sm = c.getMethod(invokeExpr.getMethod().getSubSignature());
                                        if(sm.isConcrete()) {
                                            callsites++;
                                        }
                                    } catch (Exception e) {

                                    }
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


    public void drawRelation(String methodSig, Set<Relation> relationSet, String dotPath) {
        int nodeNum = 0;
        FileOutputStream outStr = null;
        BufferedOutputStream buff = null;
        if(methodSig.length() > 200) {
            methodSig = methodSig.substring(0, 200);
        }
        String dotName = methodSig + ".dot";
        try {
            outStr = new FileOutputStream(new File(dotPath + dotName));
            buff = new BufferedOutputStream(outStr);
            buff.write("digraph g {\n".getBytes());
            Map<Value, Integer> nodeMap = new HashMap<Value, Integer>();
            Map<Type, Integer> typeNodeMap = new HashMap<Type, Integer>();

            Iterator<Relation> rIter = relationSet.iterator();
            while(rIter.hasNext()) {
                Relation r = rIter.next();
                if(r.relationType.equals(Relation.TYPE_CLASS2VAR)) {
                    if(!typeNodeMap.containsKey(r.type)) {
                        typeNodeMap.put(r.type, nodeNum++);
                        buff.write((typeNodeMap.get(r.type) + "[label=\"" + r.type.toString().replace("\"", "'") + "\"]\n").getBytes());
                    }
                    if(!nodeMap.containsKey(r.right)) {
                        nodeMap.put(r.right, nodeNum++);
                        buff.write((nodeMap.get(r.right) + "[label=\"" + r.right.toString().replace("\"", "'") + "\"]\n").getBytes());
                    }
                    String label = "type";
                    buff.write((typeNodeMap.get(r.type) + "->" + nodeMap.get(r.right) + "[label=\"" + label.replace("\"", "'") + "\"]\n").getBytes());
                    continue;
                }
                if(!nodeMap.containsKey(r.left)) {
                    nodeMap.put(r.left, nodeNum++);
                    buff.write((nodeMap.get(r.left) + "[label=\"" + r.left.toString().replace("\"", "'") + "\"]\n").getBytes());
                }
                if(!nodeMap.containsKey(r.right)) {
                    nodeMap.put(r.right, nodeNum++);
                    buff.write((nodeMap.get(r.right) + "[label=\"" + r.right.toString().replace("\"", "'") + "\"]\n").getBytes());
                }
                String label = r.relationType;
                if(label.equals(Relation.TYPE_VAR2VAR)) {
                    label = "";
                } else if(label.equals(Relation.TYPE_FIELD)) {
                    label = "field: " + r.field.getName();
                }
                buff.write((nodeMap.get(r.left) + "->" + nodeMap.get(r.right) + "[label=\"" + label.replace("\"", "'") + "\"]\n").getBytes());
            }
            buff.write("}".getBytes());
            buff.flush();
            buff.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void drawRelation(Map<Value,Integer> globalValueMap, Map<Type, Integer> globalTypeMap, Set<Transition> globalTransition, String dotPath) {
        FileOutputStream outStr = null;
        BufferedOutputStream buff = null;
        String dotName = "global.dot";
        try {
            outStr = new FileOutputStream(new File(dotPath + dotName));
            buff = new BufferedOutputStream(outStr);
            buff.write("digraph g {\n".getBytes());

            // draw node
            Iterator<Map.Entry<Value, Integer>> valueEntryIter = globalValueMap.entrySet().iterator();
            while(valueEntryIter.hasNext()) {
                Map.Entry<Value, Integer> valueEntry = valueEntryIter.next();
                buff.write((valueEntry.getValue() + "[label=\"" + valueEntry.getKey().toString().replace("\"", "'") + "\"]\n").getBytes());
            }

            Iterator<Map.Entry<Type, Integer>> typeEntryIter = globalTypeMap.entrySet().iterator();
            while(typeEntryIter.hasNext()) {
                Map.Entry<Type, Integer> typeEntry = typeEntryIter.next();
                buff.write((typeEntry.getValue() + "[label=\"" + typeEntry.getKey().toString().replace("\"", "'") + "\"]\n").getBytes());
            }

            // draw edge
            Iterator<Transition> tranIter = globalTransition.iterator();
            while(tranIter.hasNext()) {
                Transition t = tranIter.next();
                String label = "";
                if(t.type != null) {
                    label = t.type.toString();
                } else if(t.field != null) {
                    label = t.field.getName();
                }
                buff.write((t.left + "->" + t.right + "[label=\"" + label.replace("\"", "'") + "\"]\n").getBytes());
            }
            buff.write("}".getBytes());
            buff.flush();
            buff.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
