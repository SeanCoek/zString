package com.zstring.structs;

import soot.SootField;
import soot.SootMethod;
import soot.Value;

public class Relation {
    public static String TYPE_FIELD = "field";
    public static String TYPE_RETURN = "return";
    public static int count = 0;

    public Value left;
    public Value right;
    public String relationType;
    public SootField field;
    public SootMethod method;

    public Relation(Value left, Value right) {
        this.left = left;
        this.right = right;
        count++;
    }

    public Relation(Value left, Value right, SootField field) {
        this.left = left;
        this.right = right;
        this.relationType = TYPE_FIELD;
        this.field = field;
        count++;
    }

    public Relation(Value left, Value right, SootMethod method) {
        this.left = left;
        this.right = right;
        this.relationType = TYPE_RETURN;
        this.method = method;
        count++;
    }

    @Override
    public boolean equals(Object o) {
        if(o == null) {
            return false;
        }
        if(o instanceof Relation) {
            Relation r = (Relation) o;
            if(r.left.equals(this.left) && r.right.equals(this.right)) {
                if(r.relationType == null && this.relationType == null) {
                    return true;
                } else if(r.relationType != null) {
                    return r.relationType.equals(this.relationType);
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        if(this.relationType != null) {
            return this.left.hashCode() + this.right.hashCode() + this.relationType.hashCode();
        }
        return this.left.hashCode() + this.right.hashCode();
    }
}
