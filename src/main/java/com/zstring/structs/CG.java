package com.zstring.structs;

import com.zstring.utils.SootUtils;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Sources;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

public class CG {

    public static String outputDir = null;

    private Set<Node> starts;
    private Map<SootMethod, Node> method2Node;
    private Set<Node> nodes;
    public int edges = 0;

    public CG(CallGraph cg, Set<SootMethod> allMethods) {
        starts = new HashSet<Node>();
        method2Node = new HashMap<SootMethod, Node>();
        nodes = new HashSet<Node>();

        Iterator<SootMethod> mIter = allMethods.iterator();
        while(mIter.hasNext()) {
            SootMethod m = mIter.next();
            Node n = new Node(m);
            method2Node.put(m, n);
            nodes.add(n);
        }

        mIter = allMethods.iterator();
        while(mIter.hasNext()) {
            SootMethod m = mIter.next();
            Node n = method2Node.get(m);
            Sources srcMethods = new Sources(cg.edgesInto(m));
            while(srcMethods.hasNext()) {
                SootMethod srcM = (SootMethod) srcMethods.next();
                Node srcN = method2Node.get(srcM);
                n.addParent(srcN);
                srcN.addChild(n);
                edges++;
            }

            if(m.isConcrete()) {
                JimpleBody jb = (JimpleBody) m.retrieveActiveBody();
                Iterator<Unit> uIter = jb.getUnits().iterator();
                while(uIter.hasNext()) {
                    Unit stmt = uIter.next();
                    List<ValueBox> useAndDef = stmt.getUseAndDefBoxes();
                    Collections.reverse(useAndDef);
                    Iterator<ValueBox> udIter = useAndDef.iterator();
                    while(udIter.hasNext()) {
                        Value v = udIter.next().getValue();
                        if(v instanceof InvokeExpr) {
                            n.addCS((InvokeExpr) v);
                        }
                    }

                }
            }
        }

        Iterator<Node> nIter = nodes.iterator();
        while(nIter.hasNext()) {
            Node n = nIter.next();
            if(n.getInDeg() == 0) {
                starts.add(n);
//                travel(n);
                draw(n, outputDir);
            }
        }

    }

    public void travel(Node root) {
        System.out.print(root.getMethod().getSignature());
        System.out.print(" --> ");
        List<InvokeExpr> cs = root.getCS();
        if(!cs.isEmpty()) {
            Iterator<InvokeExpr> uIter = cs.iterator();
            while(uIter.hasNext()) {
                InvokeExpr csite = uIter.next();
                Object[] resolution = SootUtils.resolveInvokeUnit(csite);
                if(resolution != null) {
                    SootMethod callee = (SootMethod) resolution[1];
                    Node nextNode = method2Node.get(callee);
                    if(nextNode != null) {
                        travel(nextNode);
                    }
                }
                System.out.print(csite + ";\t");

            }

        }
        System.out.println();

    }


    public void draw(Node root, String outDir) {
        FileOutputStream outStr = null;
        BufferedOutputStream buff = null;
        String dotName = root.getMethod().getSignature() + ".dot";
        if(dotName.length() > 255) {
            dotName = root.getMethod().getSignature().substring(0, 200);
            dotName = dotName + ".dot";
        }
        Map<Node, Integer> nodeMap = new HashMap<Node, Integer>();
        Map<String, Set<InvokeExpr>> transition = new HashMap<String, Set<InvokeExpr>>();
        Stack<Node> stack = new Stack<Node>();
        stack.push(root);
        int nodeNum = 0;
        // deep first traversal
        while(!stack.isEmpty()) {
            Node n = stack.pop();
            if(!nodeMap.containsKey(n)){
                nodeMap.put(n, nodeNum++);
            }
            List<InvokeExpr> cs = n.getCS();
            Collections.reverse(cs);
            Iterator cIter = cs.iterator();
            while(cIter.hasNext()) {
                InvokeExpr csite = (InvokeExpr) cIter.next();
                SootMethod m = (SootMethod) SootUtils.resolveInvokeUnit(csite)[1];
                if(method2Node.containsKey(m)) {
                    Node mNode = method2Node.get(m);
                    if(!nodeMap.containsKey(mNode)){
                        nodeMap.put(mNode, nodeNum++);
                    }

                    int srcNum = nodeMap.get(n);
                    int dstNum = nodeMap.get(mNode);
                    String key = srcNum + ":" + dstNum;
                    Set<InvokeExpr> csites = transition.get(key);
                    if(csites == null) {
                        csites = new HashSet<InvokeExpr>();
                    }
                    if(csites.contains(csite)) {
                        continue;
                    }
                    csites.add(csite);
                    transition.put(key, csites);
                    stack.push(mNode);
                }
            }
        }

        try{
            outStr = new FileOutputStream(new File(outDir + dotName));
            buff = new BufferedOutputStream(outStr);
            buff.write("digraph g {\n".getBytes());
            // drawing nodes
            Iterator<Map.Entry<Node, Integer>> nodeIter = nodeMap.entrySet().iterator();
            while(nodeIter.hasNext()) {
                Map.Entry<Node, Integer> nodeEntry = nodeIter.next();
                buff.write((nodeEntry.getValue() + "[label=\"" + nodeEntry.getKey().getMethod().getSubSignature() + "\"]\n").getBytes());
            }
            // drawing edges
            Iterator<Map.Entry<String, Set<InvokeExpr>>> transIter = transition.entrySet().iterator();
            while(transIter.hasNext()) {
                Map.Entry<String, Set<InvokeExpr>> trans = transIter.next();
                String key = trans.getKey();
                String[] keySplit = key.split(":");
                String preNode = String.valueOf(keySplit[0]);
                String sucNode = String.valueOf(keySplit[1]);
                Iterator<InvokeExpr> uIter= trans.getValue().iterator();
                while(uIter.hasNext()) {
                    String label = uIter.next().toString();
                    buff.write((preNode + "->" + sucNode + "[label=\"" + label.replace("\"", "'") + "\"]\n").getBytes());
                }
            }
            buff.write("}".getBytes());
            buff.flush();
            buff.close();
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            try{
                buff.close();
                outStr.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}
