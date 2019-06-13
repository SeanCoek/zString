package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.structs.Relation;
import com.zstring.structs.Transition;
import com.zstring.utils.SootUtils;
import soot.*;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.Ref;
import soot.jimple.internal.*;

import java.io.*;
import java.util.*;

public class RelationAnalyzer {
    public static Map<String, Set<Relation>> allRelations = new HashMap<String, Set<Relation>>();
    public static Map<String, Map<String, List<Value>>> methodInfoMap = new HashMap<String, Map<String, List<Value>>>();
    public static Map<Value, Integer> globalValueMap;
    public static Map<Type, Integer> globalTypeMap;
    public static Set<Transition> globalTransition;
    public static Set<Relation> globalRelations;
    public static Map<Type, Set<Relation>> typeRelationHolder = new HashMap<Type, Set<Relation>>();
    public static Map<Value, Set<Relation>> valueRelationHolder = new HashMap<Value, Set<Relation>>();
    public static int callsiteCount = 0;

    public static void main(String[] args) {
        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
        String pp = "/home/sean/bench_compile/";
        pp = "/home/sean/bench_compared/compress/";
        new RelationAnalyzer().analyze(cp, pp);
    }

    public void analyze(String cp, String pp) {
        SootEnvironment.init(cp, pp);
        String dotPath = "/home/sean/IdeaProjects/zString/outputDot/eclipse/";
        long t1 = new Date().getTime();
        generateRelation(dotPath);
        globalize();
        calcFixPoint();
        splitRelationToTypeAndValue();
        minimalization();
        long t2 = new Date().getTime();
        System.out.println("generated " + Relation.count + " relations");
        System.out.println("Call Site Count: " + callsiteCount);
        System.out.println("Total time used: " + (t2-t1) + "ms");
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
                        if (left instanceof JInstanceFieldRef) {
                            SootField field = ((FieldRef) left).getField();
                            if (field.isStatic()) {
                                // TODO: static field
                                continue;
                            }
                            left = ((JInstanceFieldRef) left).getBase();
                            relationSet.add(new Relation(left, right, field));
                        } else if (right instanceof JInstanceFieldRef) {
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
            extendTransitive(relationSet);
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
            JInstanceFieldRef right = (JInstanceFieldRef) stmt.getRightOp();
            Value base = right.getBase();
            Type t = right.getType();
            SootField f = right.getField();

            Iterator<Relation> relationIter = relationSet.iterator();
            Set<Relation> relationToAdd = new HashSet<Relation>();
            while(relationIter.hasNext()) {
                Relation relation = relationIter.next();
                if(relation.relationType.equals(Relation.TYPE_FIELD)
                        && relation.left.equals(base)) {
                    relationToAdd.add(new Relation(relation.right, left));
                }
            }
            relationSet.addAll(relationToAdd);
        }
    }

    public void extendTransitive(Set relationSet) {

        while(true) {
            long t1 = new Date().getTime();
            int compCount = 0;
            Set<Relation> relationToAdd = new HashSet<Relation>();
            Iterator<Relation> relationIter1 = relationSet.iterator();
            while(relationIter1.hasNext()) {
                Relation relation1 = relationIter1.next();
                Iterator<Relation> relationIter2 = relationSet.iterator();
                if(relation1.relationType.equals(Relation.TYPE_CLASS2VAR)) {
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
                    while(relationIter2.hasNext()) {
                        Relation relation2 = relationIter2.next();
                        if(relation2.relationType.equals(Relation.TYPE_VAR2VAR)
                                && relation1.right.equals(relation2.left)) {
                            relationToAdd.add(new Relation(relation1.left, relation2.right));
                        }
                        compCount++;
                    }
                } else if(relation1.relationType.equals(Relation.TYPE_FIELD)) {
                    while(relationIter2.hasNext()) {
                        Relation relation2 = relationIter2.next();
                        if(relation2.relationType.equals(Relation.TYPE_VAR2VAR)
                                && relation1.left.equals(relation2.left)) {
                            relationToAdd.add(new Relation(relation2.right, relation1.right, relation1.field));
                        }
                        compCount++;
                    }
                }
            }
            int sizeBefore = relationSet.size();
            relationSet.addAll(relationToAdd);
            if(relationSet.size() == sizeBefore) {
                break;
            }
            long t2 = new Date().getTime();
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
            JInterfaceInvokeExpr interfaceInvokeExpr = (JInterfaceInvokeExpr) invokeExpr;
            caller = interfaceInvokeExpr.getBase();
            calleeSub = interfaceInvokeExpr.getMethod().getSubSignature();
        } else if(invokeExpr instanceof JDynamicInvokeExpr) {
            return;
        }
        genRelationFromInvoke(invokeExpr, hostMethod, caller, calleeSub, receiver);
    }

    public void genRelationFromInvoke(InvokeExpr invokeExpr, String hostMethod, Value caller, String calleeSub, Value receiver) {
        int newRelationCount = 0;
        Set<Relation> hostRelations = allRelations.get(hostMethod);
        if(caller == null) {    // Static Invoke
            callsiteCount++;
            String callee = invokeExpr.getMethod().getSignature();
            if(methodInfoMap.get(callee) == null) {
                return;
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
                callsiteCount++;
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
        extendTransitive(globalRelations);
    }

    public void globalize() {
        int num = 0;
//        globalValueMap = new HashMap<Value, Integer>();
//        globalTypeMap = new HashMap<Type, Integer>();
//        globalTransition = new HashSet<Transition>();
        globalRelations = new HashSet<Relation>();
        Iterator<Map.Entry<String, Set<Relation>>> relationEntryIter = allRelations.entrySet().iterator();
        while(relationEntryIter.hasNext()) {
            Map.Entry<String, Set<Relation>> relationEntry = relationEntryIter.next();
//            String methodSig = relationEntry.getKey();
            Set<Relation> relations = relationEntry.getValue();
            globalRelations.addAll(relations);
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
        }
    }

    public void splitRelationToTypeAndValue() {
        Iterator<Relation> rIter = globalRelations.iterator();
        while(rIter.hasNext()) {
            Relation r = rIter.next();
            if(r.relationType.equals(Relation.TYPE_CLASS2VAR)) {
                if(r.type instanceof RefType) {
                    Set<Relation> typeRelations = typeRelationHolder.get(r.type);
                    if (typeRelations == null) {
                        typeRelations = new HashSet<Relation>();
                    }
                    typeRelations.add(r);
                    typeRelationHolder.put(r.type, typeRelations);
                    Set<Relation> valueRelations = valueRelationHolder.get(r.right);
                    if (valueRelations == null) {
                        valueRelations = new HashSet<Relation>();
                    }
                    valueRelations.add(r);
                    valueRelationHolder.put(r.right, valueRelations);
                }
            } else {
                if(r.left.getType() instanceof RefType && r.right.getType() instanceof RefType) {
                    Set<Relation> valueRelations = valueRelationHolder.get(r.left);
                    if (valueRelations == null) {
                        valueRelations = new HashSet<Relation>();
                    }
                    valueRelations.add(r);
                    valueRelationHolder.put(r.left, valueRelations);
                    valueRelations = valueRelationHolder.get(r.right);
                    if (valueRelations == null) {
                        valueRelations = new HashSet<Relation>();
                    }
                    valueRelations.add(r);
                    valueRelationHolder.put(r.right, valueRelations);
                }
            }
        }
    }

    public void minimalization() {
        Set allValue = valueRelationHolder.keySet();
        List<Set> splited = new ArrayList<Set>();
        split(splited, allValue, new HashSet());
    }

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
            Set<Relation> vRelations = valueRelationHolder.get(v);
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
            Set<Relation> vRelations = valueRelationHolder.get(v);
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

    public void dealInvokeInAssign(String hostMethod, JAssignStmt assignStmt) {
        int count = 0;
        Value left = assignStmt.getLeftOp();
        JVirtualInvokeExpr invokeExpr = (JVirtualInvokeExpr) (assignStmt.getRightOp());
        Value caller = invokeExpr.getBase();
        String calleeSub = invokeExpr.getMethod().getSubSignature();
        Set<Relation> hostRelations = allRelations.get(hostMethod);
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
            List<Value> params = methodInfoMap.get(callee).get("param");
            Value vThis = params.get(0);
            List<Value> returns = methodInfoMap.get(callee).get("return");
            Set<Relation> calleeRelations = allRelations.get(callee);
            // c --> this(c,m)
            calleeRelations.add(new Relation(null, vThis, t));
            count++;
            // x' (- return(c,m)
            for(Value vReturn: returns) {
                calleeRelations.add(new Relation(vReturn, left));
                count++;
            }
            for(int i=0; i < invokeExpr.getArgCount(); i++) {
                Value z = invokeExpr.getArg(i);
                Value p = params.get(i+1);      // "this" variable was store in params(0)
                calleeRelations.add(new Relation(z, p));
                count++;
            }
            allRelations.put(callee, calleeRelations);
        }
        System.out.println("added " + count + " relations from invocation");
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
