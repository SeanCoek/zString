package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.structs.Relation;
import com.zstring.utils.SootUtils;
import soot.*;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.internal.*;

import java.io.*;
import java.util.*;

public class RelationAnalyzer {
    public static Map<String, Set<Relation>> allRelations = new HashMap<String, Set<Relation>>();
    public static Map<String, Map<String, List<Value>>> methodInfoMap = new HashMap<String, Map<String, List<Value>>>();

    public static void main(String[] args) {
        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
        String pp = "/home/sean/bench_compile/";
        new RelationAnalyzer().analyze(cp, pp);
    }

    public void analyze(String cp, String pp) {
        SootEnvironment.init(cp, pp);
        generateRelation();
        System.out.println(Relation.count);
    }

    public void generateRelation() {

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
                if(u instanceof JInvokeStmt || (u instanceof JAssignStmt && ((JAssignStmt) u).getRightOp() instanceof JVirtualInvokeExpr)) {
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
                    if(left instanceof JInstanceFieldRef) {
                        SootField field = ((FieldRef) left).getField();
                        if(field.isStatic()) {
                            // TODO: static field
                            continue;
                        }
                        left = ((JInstanceFieldRef) left).getBase();
                        relationSet.add(new Relation(left, right, field));
                    } else if(right instanceof JInstanceFieldRef) {
                      // TODO: x = y.f
                        fieldLoadToResolve.add((JAssignStmt) u);
                    } else if(right instanceof JNewExpr || right instanceof JNewArrayExpr) {
                        Type t = right.getType();
                        relationSet.add(new Relation(right, left, t));
                    } else if(left instanceof JimpleLocal) {
                        relationSet.add(new Relation(right, left));
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

        Iterator<Map.Entry<String, Set<Relation>>> relationsIter = allRelations.entrySet().iterator();
        while(relationsIter.hasNext()) {
            Map.Entry<String, Set<Relation>> relationsEntry = relationsIter.next();
            drawRelation(relationsEntry.getKey(), relationsEntry.getValue());
        }
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
                    }
                } else if(relation1.relationType.equals(Relation.TYPE_VAR2VAR)) {
                    while(relationIter2.hasNext()) {
                        Relation relation2 = relationIter2.next();
                        if(relation2.relationType.equals(Relation.TYPE_VAR2VAR)
                                && relation1.right.equals(relation2.left)) {
                            relationToAdd.add(new Relation(relation1.left, relation2.right));
                        }
                    }
                } else if(relation1.relationType.equals(Relation.TYPE_FIELD)) {
                    while(relationIter2.hasNext()) {
                        Relation relation2 = relationIter2.next();
                        if(relation2.relationType.equals(Relation.TYPE_VAR2VAR)
                                && relation1.left.equals(relation2.left)) {
                            relationToAdd.add(new Relation(relation2.right, relation1.right, relation1.field));
                        }
                    }
                }
            }
            int sizeBefore = relationSet.size();
            relationSet.addAll(relationToAdd);
            if(relationSet.size() == sizeBefore) {
                break;
            }
            System.out.println("extend " + (relationSet.size()-sizeBefore) + " relations");
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
                if(invokeStmt instanceof JAssignStmt) {
                    JAssignStmt stmt = (JAssignStmt) invokeStmt;
                    dealInvokeInAssign(hostMethod, stmt);

                } else if(invokeStmt instanceof JInvokeStmt) {
                    InvokeExpr invokeExpr = ((JInvokeStmt) invokeStmt).getInvokeExpr();
                    List<Value> args = invokeExpr.getArgs();
                    if(invokeExpr instanceof JSpecialInvokeExpr) {

                    } else if(invokeExpr instanceof JStaticInvokeExpr) {

                    }
                }
            }
        }
    }

    public void dealInvokeInAssign(String hostMethod, JAssignStmt invokeStmt) {
        int count = 0;
        Value left = invokeStmt.getLeftOp();
        JVirtualInvokeExpr invokeExpr = (JVirtualInvokeExpr) (invokeStmt.getRightOp());
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


    public void drawRelation(String methodSig, Set<Relation> relationSet) {
        int nodeNum = 0;
        FileOutputStream outStr = null;
        BufferedOutputStream buff = null;
        if(methodSig.length() > 200) {
            methodSig = methodSig.substring(0, 200);
        }
        String dotName = methodSig + ".dot";
        try {
            outStr = new FileOutputStream(new File("/home/sean/IdeaProjects/zString/relation2/" + dotName));
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



}
