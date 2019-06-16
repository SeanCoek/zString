package com.zstring.structs;

import soot.SootField;
import soot.Type;
import soot.Value;

import java.util.HashSet;
import java.util.Set;

public class Relation {
    public static String TYPE_FIELD = "field";
    public static String TYPE_VAR2VAR = "v2v";
    public static String TYPE_CLASS2VAR = "c2v";
    public static Set<Relation> globalRelations = new HashSet<Relation>();
    public static int count = 0;

    public Type type;
    public Value left;
    public Value right;
    public String relationType;
    public SootField field;

    public Relation(Value left, Value right) {
        this.left = left;
        this.right = right;
        this.relationType = TYPE_VAR2VAR;
        if(globalRelations.add(this)) {
            count++;
        }
    }

    public Relation(Value left, Value right, SootField field) {
        this.left = left;
        this.right = right;
        this.relationType = TYPE_FIELD;
        this.field = field;
        if(globalRelations.add(this)) {
            count++;
        }
    }

    public Relation(Value left, Value right, Type type) {
        this.left = left;
        this.right = right;
        this.relationType = TYPE_CLASS2VAR;
        this.type = type;
        if(globalRelations.add(this)) {
            count++;
        }
    }

    @Override
    public boolean equals(Object o) {
        if(o == null) {
            return false;
        }
        if(o instanceof Relation) {
            Relation r = (Relation) o;
            if(r.relationType.equals(this.relationType)) {
                if(r.relationType.equals(Relation.TYPE_CLASS2VAR)) {
                    return r.type.equals(this.type) && r.right.equals(this.right);
                }
                if(r.relationType.equals(TYPE_FIELD)) {
                    if(r.left != null && this.left != null) {
                        return r.left.equals(this.left) && r.right.equals(this.right) && r.field.equals(this.field);
                    } else if(r.left == null && this.left == null){
                        return r.right.equals(this.right) && r.field.equals(this.field);
                    } else {
                        return false;
                    }
                }
                if(r.relationType.equals(TYPE_VAR2VAR)) {
                    return r.left.equals(this.left) && r.right.equals(this.right);
                }
            }

        }
        return false;
    }

    @Override
    public int hashCode() {
        if(this.relationType != null) {
            if(this.relationType.equals(Relation.TYPE_CLASS2VAR)) {
                return this.type.hashCode() + this.right.hashCode();
            }
            if(this.relationType.equals(TYPE_FIELD) && this.left == null) {
                return this.field.hashCode() + this.right.hashCode();
            }
            return this.left.hashCode() + this.right.hashCode() + this.relationType.hashCode();
        }
        return this.left.hashCode() + this.right.hashCode();
    }
}
