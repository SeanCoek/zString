package com.zstring.structs;

import soot.SootField;
import soot.Type;
import soot.Value;

/***
 * description: A splitter is of a specify kind of Relation
 */
public class Splitter {

    public enum SplitterType {
        TYPE_FIELD, TYPE_CLASS, TYPE_PARTIAL;
    }

    public SplitterType splitType;

    public Type type;               // for type-flow relation
    public SootField field;         // for field-flow relation
    public Value left;              // this is the left-hand-side of the partial order relation,
    public Value right;             // this is the right-hand-side of the relation

    public Splitter(SplitterType splitType, Type type, SootField field, Value left, Value right) {
        this.splitType = splitType;
        this.type = type;
        this.field = field;
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        if(obj instanceof Splitter) {
            Splitter o = (Splitter)obj;
            if(o.splitType == this.splitType) {
                if(o.splitType == SplitterType.TYPE_FIELD) {
                    if(!o.field.isStatic()) {
                        return o.field.equals(this.field) && o.left.equals(this.left) && o.right.equals(this.right);
                    } else {
                        return o.field.equals(this.field) && o.right.equals(this.right);
                    }
                } else if(o.splitType == SplitterType.TYPE_CLASS) {
                    return o.type.equals(this.type) && o.right.equals(this.right);
                } else {
                    return o.left.equals(this.left) && o.right.equals(this.right);
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public int hashCode() {
        if(this.splitType == SplitterType.TYPE_FIELD) {
            if(!this.field.isStatic()) {
                return this.field.hashCode() + this.left.hashCode() + this.right.hashCode();
            } else {
                return this.field.hashCode() + this.right.hashCode();
            }
        } else if(this.splitType == SplitterType.TYPE_CLASS) {
            return this.type.hashCode() + this.right.hashCode();
        } else {
            return this.left.hashCode() + this.right.hashCode();
        }
    }
}
