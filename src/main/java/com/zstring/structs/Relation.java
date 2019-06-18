package com.zstring.structs;

import soot.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Relation {
    public static String TYPE_FIELD = "field";
    public static String TYPE_VAR2VAR = "v2v";
    public static String TYPE_CLASS2VAR = "c2v";
    public static Set<Relation> globalRelations = new HashSet<Relation>();
    public static Map<Type, Set<Relation>> typeRelationHolder = new HashMap<Type, Set<Relation>>();
    public static Map<Value, Set<Relation>> valueRelationHolder = new HashMap<Value, Set<Relation>>();
    public static Map<Value, Set<Relation>> fieldRelationHolder = new HashMap<>();
    public static Map<Value, Set<Relation>> partialRelationHolder = new HashMap<>();
    public static int count = 0;
    public static int type_count = 0;
    public static int field_count = 0;
    public static int less_count = 0;

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
            if((left.getType() instanceof RefType || left.getType() instanceof ArrayType)
                    && (right.getType() instanceof RefType || right.getType() instanceof ArrayType)) {
                Set<Relation> valueRelations = null;
                valueRelations = valueRelationHolder.get(left);
                if (valueRelations == null) {
                    valueRelations = new HashSet<Relation>();
                }
                valueRelations.add(this);
                valueRelationHolder.put(left, valueRelations);

                valueRelations = partialRelationHolder.get(left);
                if (valueRelations == null) {
                    valueRelations = new HashSet<Relation>();
                }
                valueRelations.add(this);
                partialRelationHolder.put(left, valueRelations);

                valueRelations = valueRelationHolder.get(right);
                if (valueRelations == null) {
                    valueRelations = new HashSet<Relation>();
                }
                valueRelations.add(this);
                valueRelationHolder.put(right, valueRelations);

                valueRelations = partialRelationHolder.get(right);
                if (valueRelations == null) {
                    valueRelations = new HashSet<Relation>();
                }
                valueRelations.add(this);
                partialRelationHolder.put(left, valueRelations);
            }
            count++;
            less_count++;
        }
    }

    public Relation(Value left, Value right, SootField field) {
        this.left = left;
        this.right = right;
        this.relationType = TYPE_FIELD;
        this.field = field;
        if(globalRelations.add(this)) {
            Set<Relation> valueRelations = null;
            if(!field.isStatic()) {
                if(left.getType() instanceof RefType || left.getType() instanceof ArrayType) {
                    valueRelations = valueRelationHolder.get(left);
                    if (valueRelations == null) {
                        valueRelations = new HashSet<Relation>();
                    }
                    valueRelations.add(this);
                    valueRelationHolder.put(left, valueRelations);

//                    valueRelations = fieldRelationHolder.get(left);
//                    if (valueRelations == null) {
//                        valueRelations = new HashSet<Relation>();
//                    }
//                    valueRelations.add(this);
//                    fieldRelationHolder.put(left, valueRelations);
                }
            }
            if(right.getType() instanceof RefType || right.getType() instanceof ArrayType) {
                valueRelations = valueRelationHolder.get(right);
                if (valueRelations == null) {
                    valueRelations = new HashSet<Relation>();
                }
                valueRelations.add(this);
                valueRelationHolder.put(right, valueRelations);

                valueRelations = fieldRelationHolder.get(right);
                if (valueRelations == null) {
                    valueRelations = new HashSet<Relation>();
                }
                valueRelations.add(this);
                fieldRelationHolder.put(left, valueRelations);
            }
            count++;
            field_count++;
        }
    }

    public Relation(Value left, Value right, Type type) {
        this.left = left;
        this.right = right;
        this.relationType = TYPE_CLASS2VAR;
        this.type = type;
        if(globalRelations.add(this)) {
            if(type instanceof RefType || type instanceof ArrayType) {
                Set<Relation> typeRelations = typeRelationHolder.get(type);
                if (typeRelations == null) {
                    typeRelations = new HashSet<Relation>();
                }
                typeRelations.add(this);
                typeRelationHolder.put(type, typeRelations);
                Set<Relation> valueRelations = valueRelationHolder.get(right);
                if (valueRelations == null) {
                    valueRelations = new HashSet<Relation>();
                }
                valueRelations.add(this);
                valueRelationHolder.put(right, valueRelations);
            }
            count++;
            type_count++;
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
