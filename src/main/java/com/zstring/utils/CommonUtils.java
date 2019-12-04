package com.zstring.utils;

import com.zstring.structs.Relation;
import com.zstring.structs.Transition;
import soot.Type;
import soot.Value;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class CommonUtils {

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
