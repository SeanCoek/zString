package com.zstring.structs;

import soot.Type;
import soot.Value;

import java.util.HashSet;
import java.util.Set;

/***
 * Class to Var Relation: c ---> x
 */
public class C2VRelation {
    public Type left;
    public Set<Value> right;

    public C2VRelation(Type left) {
        this.left = left;
        right = new HashSet<Value>();
    }
}
