package com.zstring.opti;

import com.zstring.structs.Relation;
import soot.SootField;
import soot.Type;
import soot.Value;

import java.util.*;

public class Optimization {

    public static List<Set<Value>> mergeByType(Set<Value> groupToMerge, Set<Value> groupUnMerge, boolean isSCCMerge) {
        List<Set<Value>> result = new ArrayList<>();
        groupUnMerge.addAll(groupToMerge);
        if(groupToMerge.size() == 1) {
            result.add(groupToMerge);
            return result;
        }
        Set<Value> valueUsed = new HashSet<>();
        for(Value firstItem : groupToMerge) {
            if(valueUsed.contains(firstItem)) {
                continue;
            }
            Set<Type> reachTypeByFirstItem = Relation.typeReachByValue.get(firstItem);
            Set<Value> newGroup = new HashSet<>();
            if(reachTypeByFirstItem != null) {
                for(Value secondItem : groupToMerge) {
                    if(!secondItem.equals(firstItem) && !valueUsed.contains(secondItem)) {
                        Set<Type> reachTypeBySecondItem = Relation.typeReachByValue.get(secondItem);
                        if(reachTypeBySecondItem != null
                                && reachTypeByFirstItem.containsAll(reachTypeBySecondItem)
                                && reachTypeByFirstItem.size() == reachTypeBySecondItem.size()) {
                            // this means two var has the exactly same type
                            newGroup.add(secondItem);
                            valueUsed.add(secondItem);
                        }
                    }
                }
            }
            else {
                if(isSCCMerge) {
                    for (Value secondItem : groupToMerge) {
                        if (!secondItem.equals(firstItem) && !valueUsed.contains(secondItem)) {
                            Set<Type> reachTypeBySecondItem = Relation.typeReachByValue.get(secondItem);
                            if (reachTypeBySecondItem == null) {
                                // this means both two var have no one type
                                newGroup.add(secondItem);
                                valueUsed.add(secondItem);
                            }
                        }
                    }
                }
            }
            newGroup.add(firstItem);
            valueUsed.add(firstItem);
            if(newGroup.size() > 1) {
                result.add(newGroup);
                groupUnMerge.removeAll(newGroup);
            }
            assert (newGroup.size() != 0);

        }
        groupToMerge = null;
        return result;
    }

    public static List<Set<Value>> mergeByField(Set<Value> groupToMerge, Set<Value> groupUnMerge, boolean isTypeMerge) {
        List<Set<Value>> result = new LinkedList<>();
        groupUnMerge.addAll(groupToMerge);
        if(groupToMerge.size() == 1) {
            result.add(groupToMerge);
            return result;
        }
        Set<Value> valueUsed = new HashSet<>();
        for(Value firstItem : groupToMerge) {
            if(valueUsed.contains(firstItem)) {
                continue;
            }
            Map<SootField, Set<Value>> fieldMapByFirstItem = Relation.valueRightReachByLeftAndField.get(firstItem);

            Set<Value> newGroup = new HashSet<>();
            if(fieldMapByFirstItem == null) {
                if(isTypeMerge) {
                    for (Value secondItem : groupToMerge) {
                        if (valueUsed.contains(secondItem) || secondItem.equals(firstItem)) {
                            continue;
                        }
                        Map<SootField, Set<Value>> fieldMapBySecondItem = Relation.valueRightReachByLeftAndField.get(secondItem);
                        if (fieldMapBySecondItem == null) {
                            newGroup.add(secondItem);
                            valueUsed.add(secondItem);
                        }
                    }
                }

            } else {
                Set<SootField> fieldReachByFirstItem = fieldMapByFirstItem.keySet();

                for(Value secondItem : groupToMerge) {
                    if(valueUsed.contains(secondItem) || secondItem.equals(firstItem)) {
                        continue;
                    }
                    Map<SootField, Set<Value>> fieldMapBySecondItem = Relation.valueRightReachByLeftAndField.get(secondItem);
                    if(fieldMapBySecondItem != null) {
                        Set<SootField> fieldReachBySecondItem = fieldMapBySecondItem.keySet();
                        if(fieldReachByFirstItem.size() == fieldMapBySecondItem.size()
                                && fieldReachByFirstItem.containsAll(fieldReachBySecondItem)) {
                            // this means both two vars have exactly same field access
                            // we would check if the var accessed by the same field is SCC or not
                            boolean exist = true;
                            for (SootField f : fieldReachByFirstItem) {
                                Set<Value> accessedByFirstItem = fieldMapByFirstItem.get(f);
                                Set<Value> accessedBySecondItem = fieldMapBySecondItem.get(f);
                                if (!isSCCZone(accessedByFirstItem, accessedBySecondItem)) {
                                    exist = false;
                                }
                            }
                            if (exist) {
                                newGroup.add(secondItem);
                                valueUsed.add(secondItem);
                            }
                        }
                    }
                }
            }
            newGroup.add(firstItem);
            valueUsed.add(firstItem);
            if(newGroup.size() > 1) {
                result.add(newGroup);
                groupUnMerge.removeAll(newGroup);
            }
            assert (newGroup.size() != 0);
//            result.add(newGroup);
            newGroup = null;
        }
        groupToMerge = null;
        return result;
    }

    public static boolean isSCCZone(Set<Value> firstSet, Set<Value> secondSet) {
        if(firstSet == null && secondSet == null) {
            return true;
        } else if(firstSet != null && secondSet != null) {
            Set<Value> valueUsedInFirst = new HashSet<>();
            Set<Value> valueUsedInSecond = new HashSet<>();
            for(Value first : firstSet) {
                for(Value second : secondSet) {
                    if(isSCC(first, second)) {
                        valueUsedInFirst.add(first);
                        valueUsedInSecond.add(second);
                    }
                }
            }
            if(valueUsedInFirst.containsAll(firstSet) && valueUsedInSecond.containsAll(secondSet)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSCC(Value first, Value second) {
        if(first.equals(second)) {
            return true;
        }
        Set<Value> partialReachByFirstItem = Relation.partialReachByLeft.get(first);
        Set<Value> partialReachBySecondItem = Relation.partialReachByLeft.get(second);
        if(partialReachByFirstItem != null && partialReachBySecondItem != null) {
            return partialReachByFirstItem.contains(second) && partialReachBySecondItem.contains(first);
        }
        return false;
    }

    public static List<Set<Value>> mergeSCC(Set<Value> groupToMerge, Set<Value> groupUnMerge) {
        List<Set<Value>> result = new ArrayList<>();
        Set<Value> valueUsed = new HashSet<>();
        groupUnMerge.addAll(groupToMerge);
        for (Value firstItem : groupToMerge) {
            if(!valueUsed.contains(firstItem)) {
                Set<Value> newGroup = new HashSet<>();
                if (Relation.partialReachByLeft.get(firstItem) != null) {
                    for (Value secondItem : groupToMerge) {
                        if (!firstItem.equals(secondItem) && !valueUsed.contains(secondItem) && isSCC(firstItem, secondItem)) {
                            newGroup.add(secondItem);
                            valueUsed.add(secondItem);
                        }
                    }
                }
                newGroup.add(firstItem);
                valueUsed.add(firstItem);
                if(newGroup.size() > 1) {
                    result.add(newGroup);
                    groupUnMerge.removeAll(newGroup);
                }
//                result.add(newGroup);
                newGroup = null;
            }
        }
        groupToMerge = null;
        return result;
    }
}
