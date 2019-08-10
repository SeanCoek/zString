package com.zstring.datalog;

import java.util.ArrayList;
import java.util.List;

public class FieldAccess extends Fact {
    public static List<Fact> facts = new ArrayList<>();
    public Variable owner;
    public Field f;
    public Variable reach;

    public FieldAccess(Variable owner, Field f, Variable reach) {
        this.owner = owner;
        this.f = f;
        this.reach = reach;
    }

    @Override
    public String generateFactString() {
        return "FA(" + this.owner + "," + this.f + "," + this.reach + ").";
    }

}
