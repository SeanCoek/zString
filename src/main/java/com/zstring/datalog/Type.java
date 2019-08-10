package com.zstring.souffle;

public class Type {
    public String name;
    public soot.Type sootType;

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        if(obj instanceof Type) {
            Type o = (Type) obj;
            return o.name.equals(this.name);
        }
        return false;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
