package com.zstring.transform;
import soot.*;
import soot.jimple.*;
import soot.util.Chain;

import java.util.Iterator;
import java.util.Map;

public class Tiny extends BodyTransformer
{

    public static void main(String[] args) {
        PackManager.v().getPack("jtp").add(new Transform("jtp.tiny", new Tiny()));
        soot.Main.main(args);
    }
    private static Tiny instance = new Tiny();
    private Tiny() {}

    public static Tiny v() { return instance; }

    private boolean addedFieldToMainClassAndLoadedPrintStream = false;
    private SootClass javaIoPrintStream;

    private Local addTmpRef(Body body)
    {
        Local tmpRef = Jimple.v().newLocal("tmpRef", RefType.v("java.io.PrintStream"));
        body.getLocals().add(tmpRef);
        return tmpRef;
    }

    private Local addTmpLong(Body body)
    {
        Local tmpLong = Jimple.v().newLocal("tmpLong", LongType.v());
        body.getLocals().add(tmpLong);
        return tmpLong;
    }

    private void addStmtsToBefore(Chain units, Stmt s, SootField gotoCounter, Local tmpRef, Local tmpLong)
    {
        // insert "tmpRef = java.lang.System.out;" 
        units.insertBefore(Jimple.v().newAssignStmt(
                tmpRef, Jimple.v().newStaticFieldRef(
                        Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), s);

        // insert "tmpLong = gotoCounter;" 
        units.insertBefore(Jimple.v().newAssignStmt(tmpLong,
                Jimple.v().newStaticFieldRef(gotoCounter.makeRef())), s);

        // insert "tmpRef.println(tmpLong);" 
        SootMethod toCall = javaIoPrintStream.getMethod("void println(long)");
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(tmpRef, toCall.makeRef(), tmpLong)), s);
    }

    protected void internalTransform(Body body, String phaseName, Map options)
    {
        SootClass sClass = body.getMethod().getDeclaringClass();
        SootField gotoCounter = null;
        boolean addedLocals = false;
        Local tmpRef = null, tmpLong = null;
        Chain units = body.getUnits();

        // Add code at the end of the main method to print out the 
        // gotoCounter (this only works in simple cases, because you may have multiple returns or System.exit()'s )
        synchronized(this)
        {
            if (!Scene.v().getMainClass().
                    declaresMethod("void main(java.lang.String[])"))
                throw new RuntimeException("couldn't find main() in mainClass");

            if (addedFieldToMainClassAndLoadedPrintStream)
                gotoCounter = Scene.v().getMainClass().getFieldByName("gotoCount");
            else
            {
                // Add gotoCounter field
                gotoCounter = new SootField("gotoCount", LongType.v(),
                        Modifier.STATIC);
                Scene.v().getMainClass().addField(gotoCounter);

                javaIoPrintStream = Scene.v().getSootClass("java.io.PrintStream");

                addedFieldToMainClassAndLoadedPrintStream = true;
            }
        }

        // Add code to increase goto counter each time a goto is encountered
        {
            boolean isMainMethod = body.getMethod().getSubSignature().equals("void main(java.lang.String[])");

            Local tmpLocal = Jimple.v().newLocal("tmp", LongType.v());
            body.getLocals().add(tmpLocal);

            Iterator stmtIt = units.snapshotIterator();

            while(stmtIt.hasNext())
            {
                Stmt s = (Stmt) stmtIt.next();

                if(s instanceof GotoStmt)
                {
                    AssignStmt toAdd1 = Jimple.v().newAssignStmt(tmpLocal,
                            Jimple.v().newStaticFieldRef(gotoCounter.makeRef()));
                    AssignStmt toAdd2 = Jimple.v().newAssignStmt(tmpLocal,
                            Jimple.v().newAddExpr(tmpLocal, LongConstant.v(1L)));
                    AssignStmt toAdd3 = Jimple.v().newAssignStmt(Jimple.v().newStaticFieldRef(gotoCounter.makeRef()),
                            tmpLocal);

                    // insert "tmpLocal = gotoCounter;"
                    units.insertBefore(toAdd1, s);

                    // insert "tmpLocal = tmpLocal + 1L;" 
                    units.insertBefore(toAdd2, s);

                    // insert "gotoCounter = tmpLocal;" 
                    units.insertBefore(toAdd3, s);
                }
                else if (s instanceof InvokeStmt)
                {
                    InvokeExpr iexpr = (InvokeExpr) ((InvokeStmt)s).getInvokeExpr();
                    if (iexpr instanceof StaticInvokeExpr)
                    {
                        SootMethod target = ((StaticInvokeExpr)iexpr).getMethod();

                        if (target.getSignature().equals("<java.lang.System: void exit(int)>"))
                        {
                            if (!addedLocals)
                            {
                                tmpRef = addTmpRef(body); tmpLong = addTmpLong(body);
                                addedLocals = true;
                            }
                            addStmtsToBefore(units, s, gotoCounter, tmpRef, tmpLong);
                        }
                    }
                }
                else if (isMainMethod && (s instanceof ReturnStmt || s instanceof ReturnVoidStmt))
                {
                    if (!addedLocals)
                    {
                        tmpRef = addTmpRef(body); tmpLong = addTmpLong(body);
                        addedLocals = true;
                    }
                    addStmtsToBefore(units, s, gotoCounter, tmpRef, tmpLong);
                }
            }
        }
    }
}