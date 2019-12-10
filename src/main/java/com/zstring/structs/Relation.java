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
    public static Set<Value> globalValues = new HashSet<>();
    public static Map<Value, Set<Relation>> typeRelationHolder = new HashMap<Value, Set<Relation>>();
    public static Map<Value, Set<Relation>> valueRelationHolder = new HashMap<Value, Set<Relation>>();
    public static Map<Value, Set<Relation>> fieldRelationHolder = new HashMap<>();
    public static Map<Value, Set<Relation>> partialRelationHolder = new HashMap<>();
    // use these map to accelerate splitting process
    public static Map<Value, Set<Value>> partialReachByRight = new HashMap<>();
    public static Map<Value, Set<Value>> partialReachByLeft = new HashMap<>();
    public static Map<Value, Set<Type>> typeReachByValue = new HashMap<>();
    public static Map<Type, Set<Value>> valueReachByType = new HashMap<>();
//    public static Map<Value, SootField> fieldOwnByValue = new HashMap<>();
    public static Map<SootField, Set<Value>> valueLeftReachByField = new HashMap<>();
    public static Map<SootField, Set<Value>> staticFieldReachValues = new HashMap<>();
    public static Map<Value, Map<SootField, Set<Value>>> valueRightReachByLeftAndField = new HashMap<>();
//    public static Map<Value, Map<SootField, Set<Value>>> fieldMapReachByLeft = new HashMap<>();
//    public static Map<SootField, Set<Value>> valueRightReachByField = new HashMap<>();

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

                valueRelations = partialRelationHolder.get(right);
                if (valueRelations == null) {
                    valueRelations = new HashSet<Relation>();
                }
                valueRelations.add(this);
                partialRelationHolder.put(right, valueRelations);

                // update partialReachByLeft mapping
                Set<Value> values = partialReachByLeft.get(left);
                if (values == null) {
                    values = new HashSet<>();
                }
                values.add(right);
                partialReachByLeft.put(left, values);

                // update partialReachByRight mapping
                values = partialReachByRight.get(right);
                if (values == null) {
                    values = new HashSet<>();
                }
                values.add(left);
                partialReachByRight.put(right, values);

                globalValues.add(left);
                globalValues.add(right);
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
//            Map<SootField, Set<Value>> fieldMaps = null;
            if(!field.isStatic()) {
                if((left.getType() instanceof RefType || left.getType() instanceof ArrayType)
                        &&(right.getType() instanceof RefType || right.getType() instanceof ArrayType)) {
                    valueRelations = valueRelationHolder.get(left);
                    if (valueRelations == null) {
                        valueRelations = new HashSet<Relation>();
                    }
                    valueRelations.add(this);
                    valueRelationHolder.put(left, valueRelations);

                    valueRelations = fieldRelationHolder.get(left);
                    if (valueRelations == null) {
                        valueRelations = new HashSet<Relation>();
                    }
                    valueRelations.add(this);
                    fieldRelationHolder.put(left, valueRelations);

                    Set<Value> values = valueLeftReachByField.get(field);
                    if(values == null) {
                        values = new HashSet<>();
                    }
                    values.add(left);
                    valueLeftReachByField.put(field, values);


                    Map<SootField, Set<Value>> fieldMap = valueRightReachByLeftAndField.get(left);
                    if(fieldMap == null) {
                        fieldMap = new HashMap<>();
                    }
                    Set<Value> fieldToRight = fieldMap.get(field);
                    if(fieldToRight == null) {
                        fieldToRight = new HashSet<>();
                    }
                    fieldToRight.add(right);
                    fieldMap.put(field, fieldToRight);
                    valueRightReachByLeftAndField.put(left, fieldMap);
//                    valueRelations = valueRelationHolder.get(right);
//                    if (valueRelations == null) {
//                        valueRelations = new HashSet<Relation>();
//                    }
//                    valueRelations.add(this);
//                    valueRelationHolder.put(right, valueRelations);
                    globalValues.add(left);
                    globalValues.add(right);
                }
            } else {
                if(right.getType() instanceof RefType || right.getType() instanceof ArrayType) {
                    Set<Value> staticReach = staticFieldReachValues.get(field);
                    if (staticReach == null) {
                        staticReach = new HashSet<>();
                    }
                    staticReach.add(right);
                    staticFieldReachValues.put(field, staticReach);

                    valueRelations = valueRelationHolder.get(right);
                    if (valueRelations == null) {
                        valueRelations = new HashSet<Relation>();
                    }
                    valueRelations.add(this);
                    valueRelationHolder.put(right, valueRelations);

                    globalValues.add(right);
                }
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
            if((type instanceof RefType || type instanceof ArrayType || type instanceof AnySubType)
                    && (right.getType() instanceof RefType || right.getType() instanceof ArrayType)) {
                Set<Relation> typeRelations = typeRelationHolder.get(right);
                if (typeRelations == null) {
                    typeRelations = new HashSet<Relation>();
                }
                typeRelations.add(this);
                typeRelationHolder.put(right, typeRelations);
                Set<Relation> valueRelations = valueRelationHolder.get(right);
                if (valueRelations == null) {
                    valueRelations = new HashSet<Relation>();
                }
                valueRelations.add(this);
                valueRelationHolder.put(right, valueRelations);
                // update typeReachByValue mapping
                Set<Type> types = typeReachByValue.get(right);
                if(types == null) {
                    types = new HashSet<>();
                }
                types.add(type);
                typeReachByValue.put(right, types);
                // update valueReachByType mapping
                Set<Value> values = valueReachByType.get(type);
                if(values == null) {
                    values = new HashSet<>();
                }
                values.add(right);
                valueReachByType.put(type, values);

                globalValues.add(right);
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
                    if(!r.field.isStatic() && !this.field.isStatic()) {
                        return r.left.equals(this.left) && r.right.equals(this.right) && r.field.equals(this.field);
                    } else if(r.field.isStatic() && this.field.isStatic()){
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
