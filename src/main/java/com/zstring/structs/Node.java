package com.zstring.structs;

import soot.SootMethod;
import soot.jimple.InvokeExpr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Node {

    private Set<Node> parents;
    private Set<Node> children;
    private List<InvokeExpr> cs;
    private SootMethod m;

    public Node(SootMethod m) {
        this.m = m;
        this.parents = new HashSet<Node>();
        this.children = new HashSet<Node>();
        this.cs = new ArrayList<InvokeExpr>();
    }

    public Set<Node> getParents() {
        return this.parents;
    }

    public Set<Node> getChildren() {
        return this.children;
    }

    public SootMethod getMethod() {
        return this.m;
    }

    public void addParent(Node parent) {
        this.parents.add(parent);
    }

    public void addChild(Node child) {
        this.children.add(child);
    }

    public void addCS(InvokeExpr cs) {
        this.cs.add(cs);
    }

    public int getInDeg() {
        return this.parents.size();
    }

    public int getOutDeg() {
        return this.children.size();
    }

    public List<InvokeExpr> getCS() {
        return this.cs;
    }

}
