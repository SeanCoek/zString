package com.zstring.datalog;

import soot.SootField;

public class Field {
    public String name;
    public SootField sootField;

    public Field(String name, SootField sootField) {
        this.name = name;
        this.sootField = sootField;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        if(obj instanceof Field) {
            Field o = (Field) obj;
            return o.name.equals(this.name);
        }
        return false;
    }
}
