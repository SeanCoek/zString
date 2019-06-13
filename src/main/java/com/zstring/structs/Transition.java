package com.zstring.structs;

import soot.SootField;
import soot.Type;

public class Transition {
    public Integer left;
    public Integer right;
    public SootField field;
    public Type type;

    public Transition(Integer left, Integer right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(o instanceof Transition) {
            Transition t = (Transition) o;
            if(this.left.equals(t.left) && this.right.equals(t.right)) {
                if(this.field != null) {
                    return this.field.equals(t.field);
                } else if(this.type != null) {
                    return this.type.equals(t.type);
                }
                return true;
            }
        }
        return false;
    }
}
