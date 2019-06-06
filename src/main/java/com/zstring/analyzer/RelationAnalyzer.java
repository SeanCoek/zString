package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.structs.Relation;
import soot.*;
import soot.jimple.FieldRef;
import soot.jimple.JimpleBody;
import soot.jimple.internal.*;

import java.io.*;
import java.util.*;

public class RelationAnalyzer {
    public static Map<String, Set<Relation>> allRelations = new HashMap<String, Set<Relation>>();

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
        Iterator<SootMethod> mIter = SootEnvironment.allMethods.iterator();

        while(mIter.hasNext()) {
            SootMethod m = mIter.next();
            if(!m.isConcrete()) {
                continue;
            }
            Set<Relation> relationSet = new HashSet<Relation>();
            JimpleBody jb = (JimpleBody) m.retrieveActiveBody();
            Iterator<Unit> uIter = jb.getUnits().iterator();
            while(uIter.hasNext()) {
                Unit u = uIter.next();
                if(u instanceof JIdentityStmt) {
                    Value left = ((JIdentityStmt) u).getLeftOp();
                    Value right = ((JIdentityStmt) u).getRightOp();
                    relationSet.add(new Relation(right, left));
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
                    } else if(left instanceof JimpleLocal) {
                        relationSet.add(new Relation(right, left));
                    }
                    if(right instanceof JNewExpr || right instanceof JNewArrayExpr) {
                        Type t = right.getType();
                        relationSet.add(new Relation(right, left, t));
                    }
                }
            }
            allRelations.put(m.getSignature(), relationSet);
            drawRelation(m.getSignature(), relationSet);
        }
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
