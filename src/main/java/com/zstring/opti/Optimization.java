package com.zstring.opti;

import com.zstring.structs.Relation;
import com.zstring.structs.Splitter;
import soot.SootField;
import soot.Type;
import soot.Value;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

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


    public void splitIter(List splitted, Set<Value> obj) {
//        int round = 0;

        Queue<Set<Value>> wokerQueue = new ConcurrentLinkedQueue<>();
        Queue<Set<Splitter>> usedSpliiterQueue = new ConcurrentLinkedQueue<>();
        wokerQueue.add(obj);
        usedSpliiterQueue.add(new HashSet<>());
        obj = null;
        List<Set<Value>> currentSplittedGroup = null;
        while(!wokerQueue.isEmpty()) {
//            round++;
            Set<Value> valueToSplit = wokerQueue.poll();
            Set<Splitter> curRoundUsedSplitter = usedSpliiterQueue.poll();
            if(valueToSplit.size() == 1) {
                splitted.add(valueToSplit);
                valueToSplit = null;
                continue;
            }

            // find a splitter
            Splitter splitter = null;
            boolean splitFlag = false;
            Set<Value> include = null;
            Set<Value> exclude = null;
            Iterator<Value> vIter1 = valueToSplit.iterator();
            while (vIter1.hasNext()) {
                Value v = vIter1.next();
                include = new HashSet<>();
                exclude = new HashSet<>();

                Set<Relation> vRelations = Relation.valueRelationHolder.get(v);
                if(vRelations == null) {
                    // this means value v has no one relation out.
                    include = null;
                    exclude = null;
                    continue;
                } else {
                    Iterator<Relation> rIter = vRelations.iterator();
                    while (rIter.hasNext()) {
                        Relation r = rIter.next();
                        if (r.relationType.equals(Relation.TYPE_FIELD)) {
                            splitter = new Splitter(Splitter.SplitterType.TYPE_FIELD, null, r.field, r.left, r.right);
                        } else if (r.relationType.equals(Relation.TYPE_VAR2VAR)) {
                            splitter = new Splitter(Splitter.SplitterType.TYPE_PARTIAL, null, null, r.left, r.right);
                        } else {
                            splitter = new Splitter(Splitter.SplitterType.TYPE_CLASS, r.type, null, null, r.right);
                        }
                        if (!curRoundUsedSplitter.contains(splitter)) {
                            curRoundUsedSplitter.add(splitter);
                            if (split(valueToSplit, splitter, include, exclude, curRoundUsedSplitter)) {
                                splitFlag = true;
                                break;
                            }
                        }
//
                    }
                }
                if(splitFlag) {
//                    if(round > 1000) {
//                        System.out.println(round);
//                    }
                    if(include.size() == 1) {
                        splitted.add(include);
                        wokerQueue.add(exclude);
                        usedSpliiterQueue.add(curRoundUsedSplitter);
                    } else if(exclude.size() == 1) {
                        splitted.add(exclude);
                        wokerQueue.add(include);
                        usedSpliiterQueue.add(curRoundUsedSplitter);
                    } else {
                        Set curRoundUsedSpliiterCopy = new HashSet();
                        curRoundUsedSpliiterCopy.addAll(curRoundUsedSplitter);
                        wokerQueue.add(include);
                        wokerQueue.add(exclude);
                        usedSpliiterQueue.add(curRoundUsedSplitter);
                        usedSpliiterQueue.add(curRoundUsedSpliiterCopy);
                        curRoundUsedSpliiterCopy = null;
                    }
                    valueToSplit = null;
                    curRoundUsedSplitter = null;
                    currentSplittedGroup = null;
                    break;
                }
            }
            if (!splitFlag) {
                // cannot be split anymore
                splitted.add(valueToSplit);
                valueToSplit = null;
                continue;
            }
        }
//        System.out.println("split ended. process " + round + " rounds");

    }

    private boolean split(Set<Value> valueToSplit, List<Set<Value>> currentSplitGroup, Object splitter, Object splitAssist, Set<Value> include, Set<Value> exclude) {
        Iterator<Value> vIter = valueToSplit.iterator();
        int round = 0;
        while (vIter.hasNext()) {
            round++;
            Value v = vIter.next();
            Set<Relation> vRelations = null;
            if(splitter instanceof SootField) {
                vRelations = Relation.fieldRelationHolder.get(v);
            } else if(splitter instanceof Type) {
                vRelations = Relation.typeRelationHolder.get(v);
            } else {
                vRelations = Relation.partialRelationHolder.get(v);
            }
            if(vRelations == null) {
                continue;
            }
//            Set<Relation> vRelations = Relation.valueRelationHolder.get(v);
            Iterator<Relation> rIter = vRelations.iterator();
            while (rIter.hasNext()) {
                Relation r = rIter.next();
                if (splitter instanceof SootField
                        && r.relationType.equals(Relation.TYPE_FIELD)
                        && r.field.equals(splitter)) {
//                        Iterator<Set<Value>> curGroupIter = currentSplitGroup.iterator();
//                        while (curGroupIter.hasNext()) {
//                            Set<Value> group = curGroupIter.next();
//                            // check if the right-hand-side of these two relation in the same group
//                            if(group.contains(r.right) && group.contains(splitAssist)) {
//                                include.add(v);
//                                break;
//                            }
//                        }
                    if(r.right.equals(splitAssist)) {
                        include.add(v);
                        break;
                    }
                } else if(splitter instanceof Type
                        && r.relationType.equals(Relation.TYPE_CLASS2VAR)
                        && r.type.equals(splitter)) {
                    include.add(v);
                    break;
                } else {
                    if (r.relationType.equals(Relation.TYPE_VAR2VAR) && r.right.equals(splitter)) {
//                        Iterator<Set<Value>> curGroupIter = currentSplitGroup.iterator();
//                        while (curGroupIter.hasNext()) {
//                            Set<Value> group = curGroupIter.next();
//                            // check if the right-hand-side of these two relation in the same group
//                            if(group.contains(r.right) && group.contains(splitter)) {
//                                include.add(v);
//                                break;
//                            }
//                        }
                        include.add(v);
                        break;
                    }
                }
            }
        }
        System.out.println(valueToSplit.size() + " ?= " + round);
        if(include.size() == valueToSplit.size() || include.size() == 0) {
            return false;
        }
        exclude.addAll(valueToSplit);
        exclude.removeAll(include);
        return true;
    }

    private boolean split(Set<Value> valueToSplit, Splitter splitter, Set<Value> include, Set<Value> exclude, Set<Splitter> curUsedSplitter) {
        if(splitter.splitType == Splitter.SplitterType.TYPE_FIELD) {
            if(splitter.field.isStatic()) {
                Set<Value> staticReach = Relation.staticFieldReachValues.get(splitter.field);
                assert(staticReach != null);
                staticReach.retainAll(valueToSplit);
                assert(staticReach.contains(splitter.right));
                include.addAll(staticReach);

                // update used splitter
                if(staticReach.size() != 1) {
                    Iterator<Value> vIter = staticReach.iterator();
                    while (vIter.hasNext()) {
                        Value reach = vIter.next();
                        Splitter asscSplitter = new Splitter(Splitter.SplitterType.TYPE_FIELD, null, splitter.field, null, reach);
                        curUsedSplitter.add(asscSplitter);
                    }
                }
            } else {
                Set<Value> valueReachLeft = Relation.valueLeftReachByField.get(splitter.field);
                assert (valueReachLeft != null);
                valueReachLeft.retainAll(valueToSplit);
                assert (valueReachLeft.contains(splitter.left));
                if (valueReachLeft.size() == 1) {
                    include.addAll(valueReachLeft);
                } else {
                    Iterator<Value> vIter = valueReachLeft.iterator();
                    while (vIter.hasNext()) {
                        Value left = vIter.next();
                        Set<Relation> vRelations = Relation.fieldRelationHolder.get(left);
                        if (vRelations != null) {
                            Iterator<Relation> rIter = vRelations.iterator();
                            while (rIter.hasNext()) {
                                Relation r = rIter.next();
                                if (r.field.equals(splitter.field) && r.right.equals(splitter.right)) {
                                    include.add(left);
                                    Splitter asscSplitter = new Splitter(Splitter.SplitterType.TYPE_FIELD, null, splitter.field, r.left, r.right);
                                    curUsedSplitter.add(asscSplitter);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

        } else if(splitter.splitType == Splitter.SplitterType.TYPE_CLASS) {
            Set<Value> reachValues = Relation.valueReachByType.get(splitter.type);
            if (reachValues != null) {
                reachValues.retainAll(valueToSplit);
                include.addAll(reachValues);
                if(reachValues.size() != 1) {
                    Iterator<Value> vIter = reachValues.iterator();
                    while (vIter.hasNext()) {
                        Value reach = vIter.next();
                        Splitter asscSplitter = new Splitter(Splitter.SplitterType.TYPE_CLASS, splitter.type, null, null, reach);
                        curUsedSplitter.add(asscSplitter);
                    }
                }
            }
        } else {
            // in this case the splitter would be the right-hand-side of a partial order relations
            Set<Value> reachValues = Relation.partialReachByRight.get(splitter.right);
            if(reachValues != null) {
                reachValues.retainAll(valueToSplit);
                include.addAll(reachValues);
                if(reachValues.size() != 1) {
                    Iterator<Value> vIter = reachValues.iterator();
                    while (vIter.hasNext()) {
                        Value reach = vIter.next();
                        Splitter asscSplitter = new Splitter(Splitter.SplitterType.TYPE_PARTIAL, null, null, reach, splitter.right);
                        curUsedSplitter.add(asscSplitter);
                    }
                }
            }
        }
        assert(include.size() != 0);
        if(include.size() == valueToSplit.size()) {
            return false;
        }
        exclude.addAll(valueToSplit);
        exclude.removeAll(include);
        return true;
    }
}
