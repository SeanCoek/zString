package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.structs.Relation;
import com.zstring.structs.Transition;
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
    public static Map<Value, Integer> globalValueMap;
    public static Map<Type, Integer> globalTypeMap;
    public static Set<Transition> globalTransition;
    public static Set<Relation> globalRelations;
//    public static Map<Type, Set<Relation>> typeRelationHolder = new HashMap<Type, Set<Relation>>();
//    public static Map<Value, Set<Relation>> valueRelationHolder = new HashMap<Value, Set<Relation>>();
    public static ArrayList<Set> splited = new ArrayList<Set>();
    public static boolean isSplit = false;

    public static void main(String[] args) {
        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
        String pp = "/home/sean/bench_compile/";
        pp = "/home/sean/bench_compared/bootstrap.jar";
        new RelationAnalyzer().analyze(cp, pp);
    }

    public void analyze(String cp, String pp) {
        SootEnvironment.init(cp, pp);
        String dotPath = "/home/sean/IdeaProjects/zString/outputDot/eclipse/";
        long t1 = new Date().getTime();
        generateRelation(dotPath);
        globalize();
        calcFixPoint();
        long t2 = new Date().getTime();
        System.out.println("generated " + Relation.count + " relations");
        System.out.println("Relation Resolve time used: " + (t2-t1)/1000.0 + "s");
        calcCallSite();
        calcOriginalNodes();
        if(isSplit) {
            t1 = new Date().getTime();
            minimalization();
            t2 = new Date().getTime();
            System.out.println("minimalization time used: " + (t2-t1)/1000.0 + "s");
        }
        calcMinNodes();


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
                    returns.add(returnValue);
                }
                if(u instanceof JIdentityStmt) {
                    // Parameters & @this
                    Value left = ((JIdentityStmt) u).getLeftOp();
                    Value right = ((JIdentityStmt) u).getRightOp();
                    relationSet.add(new Relation(right, left));
                    params.add(left);
                } else if(u instanceof JAssignStmt) {
                    Value left = ((JAssignStmt) u).getLeftOp();
                    Value right = ((JAssignStmt) u).getRightOp();
                    if(left.getType() instanceof RefType && right.getType() instanceof RefType) {
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
                        } else if (right instanceof JNewExpr || right instanceof JNewArrayExpr) {
                            Type t = right.getType();
                            relationSet.add(new Relation(right, left, t));
                        } else if (left instanceof JimpleLocal) {
                            relationSet.add(new Relation(right, left));
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
            extendRelation(relationSet);
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
            FieldRef right = (FieldRef) stmt.getRightOp();
//            Value base = right.getBase();
//            Type t = right.getType();
//            SootField f = right.getField();

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
//            long t1 = new Date().getTime();
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
                            relationToAdd.add(new Relation(null, relation2.right, t));
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
                        Set<Relation> relatedRelation = Relation.valueRelationHolder.get(relation1.left);
                        if(relatedRelation == null) {
                            continue;
                        }
                        valueToRelation.addAll(relatedRelation);
                        relationIter2 = valueToRelation.iterator();
                        while (relationIter2.hasNext()) {
                            Relation relation2 = relationIter2.next();
                            if (relation2.relationType.equals(Relation.TYPE_VAR2VAR)
                                    && relation1.left.equals(relation2.left)) {
                                relationToAdd.add(new Relation(relation2.right, relation1.right, relation1.field));
                            }
                            compCount++;
                        }
                    }
//                    } else {
//                        while(relationIter2.hasNext()) {
//                            Relation relation2 = relationIter2.next();
//                            if(relation2.relationType.equals(Relation.TYPE_VAR2VAR)
//                                    && relation1.left.equals(relation2.left)) {
//                                relationToAdd.add(new Relation(relation2.right, relation1.right, relation1.field));
//                            }
//                            compCount++;
//                        }
//                    }

                }
            }
            int sizeBefore = relationSet.size();
            relationSet.addAll(relationToAdd);
            if(relationSet.size() == sizeBefore) {
                break;
            }
//            long t2 = new Date().getTime();
//            System.out.println("extend " + (relationSet.size()-sizeBefore) + " relations");
//            System.out.println("used " + (t2-t1) + "ms, compared counts: " + compCount);
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
                            returns.add(returnValue);
                        }
                        if (u instanceof JIdentityStmt) {
                            // Parameters & @this
                            Value left = ((JIdentityStmt) u).getLeftOp();
                            Value right = ((JIdentityStmt) u).getRightOp();
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
                for(Value vReturn: returns) {
                    calleeRelations.add(new Relation(vReturn, receiver));
                    newRelationCount++;
                }
            }

            for(int i=0; i < invokeExpr.getArgCount(); i++) {
                Value z = invokeExpr.getArg(i);
                Value p = params.get(i);      // static method don't hold the "this" variable, so we start the index from 0
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
                    for(Value vReturn: returns) {
                        calleeRelations.add(new Relation(vReturn, receiver));
                        newRelationCount++;
                    }
                }

                for(int i=0; i < invokeExpr.getArgCount(); i++) {
                    Value z = invokeExpr.getArg(i);
                    Value p = params.get(i+1);      // "this" variable was store in params(0)
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
        int num = 0;
//        globalValueMap = new HashMap<Value, Integer>();
//        globalTypeMap = new HashMap<Type, Integer>();
//        globalTransition = new HashSet<Transition>();
        globalRelations = new HashSet<Relation>();
        globalRelations.addAll(Relation.globalRelations);
//        Iterator<Map.Entry<String, Set<Relation>>> relationEntryIter = allRelations.entrySet().iterator();
//        while(relationEntryIter.hasNext()) {
//            Map.Entry<String, Set<Relation>> relationEntry = relationEntryIter.next();
////            String methodSig = relationEntry.getKey();
//            Set<Relation> relations = relationEntry.getValue();
//            globalRelations.addAll(relations);
//            Iterator<Relation> rIter = relations.iterator();
//            while(rIter.hasNext()) {
//                Relation r = rIter.next();
//                if(r.relationType.equals(Relation.TYPE_CLASS2VAR)) {
//                    if(!globalTypeMap.containsKey(r.type)) {
//                        globalTypeMap.put(r.type, num++);
//                    }
//                    if(!globalValueMap.containsKey(r.right)) {
//                        globalValueMap.put(r.right, num++);
//                    }
//                    Transition t = new Transition(globalTypeMap.get(r.type), globalValueMap.get(r.right));
//                    t.type = r.type;
//                    if(!globalTransition.contains(t)) {
//                        globalTransition.add(t);
//                    }
//                } else if(r.relationType.equals(Relation.TYPE_VAR2VAR)) {
//                    if(!globalValueMap.containsKey(r.left)) {
//                        globalValueMap.put(r.left, num++);
//                    }
//                    if(!globalValueMap.containsKey(r.right)) {
//                        globalValueMap.put(r.right, num++);
//                    }
//                    Transition t = new Transition(globalValueMap.get(r.left), globalValueMap.get(r.right));
//                    if(!globalTransition.contains(t)) {
//                        globalTransition.add(t);
//                    }
//                } else if(r.relationType.equals(Relation.TYPE_FIELD)) {
//                    if(!globalValueMap.containsKey(r.left)) {
//                        globalValueMap.put(r.left, num++);
//                    }
//                    if(!globalValueMap.containsKey(r.right)) {
//                        globalValueMap.put(r.right, num++);
//                    }
//                    Transition t = new Transition(globalValueMap.get(r.left), globalValueMap.get(r.right));
//                    t.field = r.field;
//                    if(!globalTransition.contains(t)) {
//                        globalTransition.add(t);
//                    }
//                }
//            }
//        }
    }

    public void minimalization() {
        Set allValue = Relation.valueRelationHolder.keySet();
        splitIter(splited, allValue);
    }

    public void splitIter(List<Set> splitted, Set<Value> obj) {
        int round = 0;

        Queue<Set<Value>> currentSplittedGroup = new ConcurrentLinkedQueue<Set<Value>>();
        currentSplittedGroup.add(obj);
        Queue<Set<Value>> wokerQueue = new ConcurrentLinkedQueue<Set<Value>>();
        wokerQueue.add(obj);
        while(!wokerQueue.isEmpty()) {
            Set<Value> valueToSplit = wokerQueue.poll();
            if(valueToSplit.size() == 1) {
                splitted.add(valueToSplit);
                continue;
            }
            Set usedSplitter = new HashSet();

            // find a splitter
            Object splitter = null;
            boolean splitFlag = false;
            Set<Value> include = null;
            Set<Value> exclude = null;
            Iterator<Value> vIter1 = valueToSplit.iterator();
            while (vIter1.hasNext()) {
                Value v = vIter1.next();
                Set<Relation> vRelations = Relation.valueRelationHolder.get(v);
                Iterator<Relation> rIter = vRelations.iterator();
                include = new HashSet<Value>();
                exclude = new HashSet<Value>();
                while (rIter.hasNext()) {
                    Relation r = rIter.next();
                    if (r.relationType.equals(Relation.TYPE_FIELD)) {
                        if (!usedSplitter.contains(r.field)) {
                            splitter = r.field;
                            usedSplitter.add(splitter);
                            if(split(valueToSplit, currentSplittedGroup, splitter, r.right, include, exclude)) {
                                splitFlag = true;
                                break;
                            }
                        }
                    } else if (r.relationType.equals(Relation.TYPE_VAR2VAR)) {
                        if (!usedSplitter.contains(r.right)) {
                            splitter = r.right;
                            usedSplitter.add(splitter);
                            if(split(valueToSplit, currentSplittedGroup, splitter, null, include, exclude)) {
                                splitFlag = true;
                                break;
                            }
                        }
                    } else {
                        if(!usedSplitter.contains(r.type)) {
                            splitter = r.type;
                            usedSplitter.add(splitter);
                            if(split(valueToSplit, currentSplittedGroup, splitter, null, include, exclude)) {
                                splitFlag = true;
                                break;
                            }
                        }
                    }
                }
                if(splitFlag) {
                    wokerQueue.add(include);
                    wokerQueue.add(exclude);
                    Set groupSplited = currentSplittedGroup.poll();
                    groupSplited = null;
                    valueToSplit = null;
                    currentSplittedGroup.add(include);
                    currentSplittedGroup.add(exclude);
//                    System.out.println("split round: " + (++round));
                    break;
                }
            }
            if (!splitFlag) {
                // cannot be split anymore
                splitted.add(valueToSplit);
                continue;
            }
        }
        System.out.println("split ended.");

    }

    private boolean split(Set<Value> valueToSplit, Queue<Set<Value>> currentSplitGroup, Object splitter, Object splitAssist, Set<Value> include, Set<Value> exclude) {
        Iterator<Value> vIter = valueToSplit.iterator();
        while (vIter.hasNext()) {
            Value v = vIter.next();
            Set<Relation> vRelations = Relation.valueRelationHolder.get(v);
            Iterator<Relation> rIter = vRelations.iterator();
            while (rIter.hasNext()) {
                Relation r = rIter.next();
                if (splitter instanceof SootField) {
                    if (r.relationType.equals(Relation.TYPE_FIELD) && r.field.equals(splitter)) {
                        Iterator<Set<Value>> curGroupIter = currentSplitGroup.iterator();
                        while (curGroupIter.hasNext()) {
                            Set<Value> group = curGroupIter.next();
                            if(group.contains(r.right) && group.contains(splitAssist)) {
                                include.add(v);
                                break;
                            }
                        }

                    }
                } else if(splitter instanceof Type) {
                    if(r.relationType.equals(Relation.TYPE_CLASS2VAR) && r.type.equals(splitter)) {
                        include.add(v);
                        break;
                    }
                } else {
                    if (r.relationType.equals(Relation.TYPE_VAR2VAR)) {
                        if(r.right.equals(splitter)) {
                            include.add(v);
                            break;
                        }
                    }
                }
            }
        }
        if(include.size() == valueToSplit.size() || include.size() == 0) {
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
        nodes = Relation.valueRelationHolder.keySet().size();
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
