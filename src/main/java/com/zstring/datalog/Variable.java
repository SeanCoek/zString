package com.zstring.datalog;

import soot.Value;

import java.util.ArrayList;
import java.util.List;

public class Variable {
    public static List<Variable> wholeVars = new ArrayList<>();
    public String name;
    public int num;
    public Value sootVar;

    public Variable(Value sootVar) {
        this.sootVar = sootVar;
        this.num = wholeVars.size();
        this.name = "var" + this.num;
        wholeVars.add(this);
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        if(obj instanceof Variable) {
            Variable o = (Variable) obj;
            return o.sootVar.equals(this.sootVar);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.sootVar.hashCode();
    }

    @Override
    public String toString() {
        return this.name;
    }
}
