package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;
import com.zstring.structs.Relation;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.FieldRef;
import soot.jimple.JimpleBody;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JimpleLocal;

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
                    relationSet.add(new Relation(left, right));
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
                        relationSet.add(new Relation(left, right));
                    }
                }
            }
            allRelations.put(m.getSignature(), relationSet);
            drawRelation(m.getSignature(), relationSet);
            System.out.println("here");
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
            outStr = new FileOutputStream(new File("/home/sean/IdeaProjects/zString/relation/" + dotName));
            buff = new BufferedOutputStream(outStr);
            buff.write("digraph g {\n".getBytes());
            Map<Value, Integer> nodeMap = new HashMap<Value, Integer>();

            Iterator<Relation> rIter = relationSet.iterator();
            while(rIter.hasNext()) {
                Relation r = rIter.next();
                if(!nodeMap.containsKey(r.left)) {
                    nodeMap.put(r.left, nodeNum++);
                    buff.write((nodeMap.get(r.left) + "[label=\"" + r.left.toString().replace("\"", "'") + "\"]\n").getBytes());
                }
                if(!nodeMap.containsKey(r.right)) {
                    nodeMap.put(r.right, nodeNum++);
                    buff.write((nodeMap.get(r.right) + "[label=\"" + r.right.toString().replace("\"", "'") + "\"]\n").getBytes());
                }
                String label = r.relationType;
                if(label == null) {
                    label = "";
                } else if(label.equals(Relation.TYPE_FIELD)) {
                    label = r.field.getName();
                } else {
                    label = r.method.getSubSignature();
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
