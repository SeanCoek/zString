package com.zstring.souffle;

import java.util.ArrayList;
import java.util.List;

public class TypeFlow extends Fact {
    public static List<Fact> facts = new ArrayList<>();
    public Type t;
    public Variable v;

    public TypeFlow(Type t, Variable v) {
        this.t = t;
        this.v = v;
        if(!TypeFlow.facts.contains(this)) {
            TypeFlow.facts.add(this);
        }
    }

    @Override
    public String generateFactString() {
        return "TF(" + this.t + "," + this.v + ").";
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        if(obj instanceof TypeFlow) {
            TypeFlow o = (TypeFlow) obj;
            return this.t.equals(o.t) && this.v.equals(o.v);
        }
        return false;
    }
}
