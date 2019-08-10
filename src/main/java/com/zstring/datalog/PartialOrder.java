package com.zstring.datalog;

import java.util.ArrayList;
import java.util.List;

public class PartialOrder extends Fact {
    public static List<Fact> facts = new ArrayList<>();
    public Variable left;
    public Variable right;

    public PartialOrder(Variable left, Variable right) {
        this.left = left;
        this.right = right;
        if(!PartialOrder.facts.contains(this)) {
            PartialOrder.facts.add(this);
        }
    }

    @Override
    public String generateFactString() {
        return "PO(" + left.name + "," + right.name + ").";
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        if(obj instanceof PartialOrder) {
            PartialOrder o = (PartialOrder) obj;
            return o.left.equals(this.left) && o.right.equals(this.right);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.left.hashCode() + this.right.hashCode();
    }
}
