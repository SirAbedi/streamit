package at.dms.kjc.sir.lowering.partition;

import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import java.util.*;

import at.dms.kjc.cluster.CodeEstimate;

/**
 * Provides a means for estimating the amount of work in a stream graph.
 *
 * This is meant to be an IMMUTABLE class.  Once you have an instance,
 * it's estimates will never change.  Thus you can do the estimate
 * once, then pass it around and share it.
 */
public class WorkEstimate {
    /** If true, attempt to fully unroll the filter before we estimate its work */
    public static boolean UNROLL_FOR_WORK_EST = false;
    private HashMap executionCounts;
    
    /**
     * Maps stream constructs to a WorkInfo node.
     */
    private HashMap<SIROperator, Object> workMap;

    private WorkEstimate() {
        this.workMap = new HashMap<SIROperator, Object>();
    }

    /**
     * Returns a work estimate of <pre>str</pre>
     */
    public static WorkEstimate getWorkEstimate(SIRStream str) {
        WorkEstimate result = new WorkEstimate();
        result.doit(str);
        return result;
    }
    
    /**
     * Returns a sorted worklist corresponding to a given work
     * estimate.  The entries will be sorted by increasing values of
     * work.  These entries will map FILTERS to work estimate, for all
     * filters in the graph.
     */
    public WorkList getSortedFilterWork() {
        return buildWorkList(workMap);
    }

    /**
     * Return the execution counts that were used to create this
     * work estimation.
     * 
     * @return the execution counts that were used to create this
     * work estimation.
     */
    public HashMap getExecutionCounts() {
        return executionCounts;   
    }
    
    /**
     * Builds a worklist out of <pre>map</pre>
     */
    private WorkList buildWorkList(final HashMap<SIROperator, Object> map) {
        // first make a treemap to do the sorting for us
        TreeMap<SIROperator, Object> treeMap = new TreeMap<SIROperator, Object>(new Comparator() {
                public int compare(Object o1, Object o2) {
                    // assume these are map.entry's
                    long work1 = ((WorkInfo)map.get(o1)).getTotalWork();
                    long work2 = ((WorkInfo)map.get(o2)).getTotalWork();
                    if(o1==o2)
                        return 0;
                    else if (work1 < work2)
                        return -1;
                    else if (work1 > work2+200) //Within threshold considered the same
                        return 1;
                    else if((o1 instanceof SIRContainer)&&(o2 instanceof SIRContainer)) {
                        int size1=((SIRContainer)o1).size();
                        int size2=((SIRContainer)o2).size();
                        //System.out.println("Comparing:"+size1+" "+size2);
                        if(size1>size2)
                            return -1;
                    }
                    return 1;
                }
            });
        treeMap.putAll(map);
        // result is a work list based on the treemap
        WorkList result = new WorkList(treeMap.entrySet());
        // make sure our sizes our right - was a problem at one point
        // due to improper comparator function (should preserve .equals)
        assert map.size()==treeMap.size():
            "map.size=" + map.size() + "; treemap.size=" + treeMap.size();
        assert map.size()==result.size():
            "map.size=" + map.size() + "; result.size=" + result.size();
        return result;
    }

    /**
     * Returns a sorted worklist in increasing order of work for all
     * containers in this graph that contain at least a single filter.
     * The caller might then try fusing the first elements of this
     * list.
     */
    public WorkList getSortedContainerWork() {
        // make a hashmap mapping containers to work they contain
        HashMap<SIROperator, Object> containerMap = new HashMap<SIROperator, Object>();
        // fill up the map with the filter's work
        for (Iterator it = workMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry)it.next();
            // get filter
            SIRFilter filter = (SIRFilter)entry.getKey();
            // get its parent
            SIRContainer parent = filter.getParent();
            // get work
            long work = ((WorkInfo)entry.getValue()).getTotalWork();
            if(parent instanceof SIRSplitJoin) {
                // Account for using semiFuse of SplitJoins.  For the
                // greedy partitioner, the real work cost of the
                // container is the increment in work to the next
                // coarser version of the container.  In this case we
                // can semi-fuse, each child with its neighbor,
                // thereby raising the work by this factor.
                work/=parent.size()/2; 
            }
            // if it doesn't contain parent, add parent
            if (!containerMap.containsKey(parent)) {
                containerMap.put(parent, WorkInfo.create(parent, work));
            } else {
                // otherwise, just increment the work
                ((WorkInfo)containerMap.get(parent)).incrementWork(work);
            }
        }
        // return sorted list
        return buildWorkList(containerMap);
    }

    /**
     * Returns the work estimate for filter <pre>obj</pre>.  Requires that
     * <pre>obj</pre> was present in the original graph used to construct this.
     */
    public long getWork(SIRFilter obj) {
        assert workMap.containsKey(obj): "Don't have work for " + obj;
        return ((WorkInfo)workMap.get(obj)).getTotalWork();
    }

    /**
     * Returns estimate of instruction code for <pre>filter</pre>
     * <br/>
     * This is cluster backend specific
     */
    public int getICodeSize(SIRFilter filter) {

        /* -- Uncomment this code for beamformer, and it will
           demonstrate that the "Magnitude" filters are not fused
           with anyone because their icode is too big.  
           if (filter.getIdent().startsWith("Mag")) {
           return 16001;
           }
           return 1;
        */

        return CodeEstimate.estimateCode(filter);
    }

    /**
     * Returns the number of times that filter <pre>obj</pre> executes in this
     * estimate.  Requires that <pre>obj</pre> was present in the original
     * graph used to construct this.
     */
    public int getReps(SIRFilter obj) {
        assert workMap.containsKey(obj): "Don't have work for " + obj;
        return ((WorkInfo)workMap.get(obj)).getReps();
    }

    /**
     * prints work of all functions to system.err.
     */
    public void printWork() {
        WorkList sorted = getSortedFilterWork();
        System.err.println("  Work Estimates:");
        // first sum the total work
        long totalWork = 0;
        for (int i=sorted.size()-1; i>=0; i--) {
            totalWork += sorted.getWork(i);
        }
        for (int i=sorted.size()-1; i>=0; i--) {
            SIRFilter obj = sorted.getFilter(i);
            String shortIdent = obj.getShortIdent(30);
            int length = shortIdent.length();
            System.err.print("    ");
            System.err.print(shortIdent);
            for (int j=length; j<35; j++) {
                System.err.print(" ");
            }
            float percentageWork = 100.0f * ((float)sorted.getWork(i))/((float)totalWork);
            System.err.println("\t" + sorted.getWork(i) + "\t" + "(" + ((int)percentageWork) + "%)");
        }
    }

    /**
     * Prints dot graph of work estimates for <pre>str</pre> to <pre>filename</pre>.
     */
    public void printGraph(SIRStream str, String filename) {
        PartitionDot.printWorkGraph(str, filename, workMap);
    }

    /**
     * Estimates the work in <pre>str</pre>
     */
    private void doit(SIRStream str) {
        // get execution counts for filters in <pre>str</pre>
        executionCounts = SIRScheduler.getApproxExecutionCounts(str)[1];

        // for each filter, build a work count
        for (Iterator it = executionCounts.keySet().iterator();
             it.hasNext(); ){
            SIROperator obj = (SIROperator)it.next();
            if (obj instanceof SIRFilter) {
                int reps = ((int[])executionCounts.get(obj))[0];
                //long work = RawWorkEstimator.estimateWork((SIRFilter)obj);
                long workEstimate;
                
                if (UNROLL_FOR_WORK_EST) {
                    SIRFilter newFilter = (SIRFilter)ObjectDeepCloner.deepCopy((SIRStream)obj); 
                    Unroller.unrollFilter(newFilter, 256);
                    workEstimate = WorkVisitor.getWork(newFilter);
                }
                else {
                    workEstimate = WorkVisitor.getWork((SIRFilter)obj);
                }
            
                WorkInfo wi = WorkInfo.create((SIRFilter)obj,reps,workEstimate);
                workMap.put(obj, wi);
            }
        }
    }

    public static class WorkVisitor extends SLIREmptyVisitor implements WorkConstants {
        private Set<JMethodDeclaration> methodsBeingProcessed;
        
        /**
         * An estimate of the amount of work found by this filter.
         */
        private long work;

        /**
         * The filter we're looking at.
         */
        private SIRFilter theFilter;
        
        private WorkVisitor(SIRFilter theFilter) {
            this.work = 0;
            this.theFilter = theFilter;
            methodsBeingProcessed = new HashSet<JMethodDeclaration>();
        }

        private WorkVisitor(SIRFilter theFilter, Set<JMethodDeclaration> methodsBeingProcessed) {
            this.work = 0;
            this.theFilter = theFilter;
            this.methodsBeingProcessed = methodsBeingProcessed;
        }
        
        /**
         * Returns estimate of work function in <pre>filter</pre>
         */
        public static long getWork(SIRFilter filter) {
            // if no work function (e.g., identity filters?) return 0
            if (!filter.needsWork () && !(filter instanceof SIRIdentity)) {
                return 0;
            } else if (filter.getWork()==null) {
                //System.err.println("this filter has null work function: " + filter);
                return 0;
            } else {
                return getWork(filter, filter.getWork());
            }
        }

        /**
         * Returns estimate of work in <pre>node</pre> of <pre>filter</pre>, use on first call only.
         */
        public static long getWork(SIRFilter filter, JPhylum node) {
            WorkVisitor visitor = new WorkVisitor(filter);
            node.accept(visitor);
            return visitor.work;
        }
        
        /**
         * Returns estimate of work in <pre>node</pre> of <pre>filter</pre>, use on internal calls.
         */
        private static long getWork(SIRFilter filter, JPhylum node, Set<JMethodDeclaration> methodsBeingProcessed ) {
            WorkVisitor visitor = new WorkVisitor(filter, methodsBeingProcessed);
            node.accept(visitor);
            return visitor.work;
        }
        
        private JMethodDeclaration findMethod(String name) 
        {
            JMethodDeclaration[] methods = theFilter.getMethods();
            for (int i = 0; i < methods.length; i++)
                {
                    JMethodDeclaration method = methods[i];
                    if (method.getName().equals(name))
                        return method;
                }
            return null;
        }

        /* still need to handle:
           public void visitCastExpression(JCastExpression self,
           JExpression expr,
           CType type) {}

           public void visitUnaryPromoteExpression(JUnaryPromote self,
           JExpression expr,
           CType type) {}
        */

        /**
         * SIR NODES.
         */

        /**
         * Visits a peek expression.
         */
        public void visitPeekExpression(SIRPeekExpression self,
                                        CType tapeType,
                                        JExpression arg) {
            super.visitPeekExpression(self, tapeType, arg);
            work += PEEK;
        }

        /**
         * Visits a pop expression.
         */
        public void visitPopExpression(SIRPopExpression self,
                                       CType tapeType) {
            super.visitPopExpression(self, tapeType);
            work += POP;
        }

        /**
         * Visits a print statement.
         */
        public void visitPrintStatement(SIRPrintStatement self,
                                        JExpression arg) {
            super.visitPrintStatement(self, arg);
            work += PRINT;
        }

        /**
         * Visits a push expression.
         */
        public void visitPushExpression(SIRPushExpression self,
                                        CType tapeType,
                                        JExpression arg) {
            super.visitPushExpression(self, tapeType, arg);
            work += PUSH;
        }

        /*

        /**
        * KJC NODES.
        */

        /**
         * prints a while statement
         */
        public void visitWhileStatement(JWhileStatement self,
                                        JExpression cond,
                                        JStatement body) {
            System.err.println("WARNING:  Estimating work in while loop, assume N=" + LOOP_COUNT + " (in " + theFilter.getIdent() + ")");
            long oldWork = work;
            super.visitWhileStatement(self, cond, body);
            long newWork = work;
            work = oldWork + LOOP_COUNT * (newWork - oldWork);
        }

        /**
         * prints a switch statement
         */
        public void visitSwitchStatement(JSwitchStatement self,
                                         JExpression expr,
                                         JSwitchGroup[] body) {
            super.visitSwitchStatement(self, expr, body);
            work += SWITCH;
        }

        /**
         * prints a return statement
         */
        public void visitReturnStatement(JReturnStatement self,
                                         JExpression expr) {
            super.visitReturnStatement(self, expr);
            // overhead of returns is folded into method call overhead
        }

        /**
         * prints a if statement
         */
        public void visitIfStatement(JIfStatement self,
                                     JExpression cond,
                                     JStatement thenClause,
                                     JStatement elseClause) {

            // always count the work in the conditional
            cond.accept(this);

            // get the work in the then and else clauses and average
            // them...
            long thenWork = WorkVisitor.getWork(theFilter, thenClause, methodsBeingProcessed);
            long elseWork;
            if (elseClause != null) {
                elseWork = WorkVisitor.getWork(theFilter, elseClause, methodsBeingProcessed);
            } else {
                elseWork = 0;
            }

            work += IF + (thenWork + elseWork) / 2;
        }

        /**
         * prints a for statement
         */
        public void visitForStatement(JForStatement self,
                                      JStatement init,
                                      JExpression cond,
                                      JStatement incr,
                                      JStatement body) {
            if (init != null) {
                init.accept(this);
            }
            // try to determine how many times the loop executes
            int loopCount = Unroller.getNumExecutions(init, cond, incr, body);
            if (loopCount==-1) {
                System.err.println("WARNING:  Estimating work in for loop, assume N=" + LOOP_COUNT + " (in filter " + theFilter.getIdent() + ")");
                loopCount = LOOP_COUNT;
            }
            long oldWork = work;
            if (cond != null) {
                cond.accept(this);
            }
            if (incr != null) {
                incr.accept(this);
            }
            body.accept(this);
            long newWork = work;
            work = oldWork + loopCount * (newWork - oldWork);
        }

        /**
         * prints a do statement
         */
        public void visitDoStatement(JDoStatement self,
                                     JExpression cond,
                                     JStatement body) {
            System.err.println("WARNING:  Estimating work in do loop, assume N=" + LOOP_COUNT + " (in filter " + theFilter.getIdent() + ")");
            long oldWork = work;
            super.visitDoStatement(self, cond, body);
            long newWork = work;
            work = oldWork + LOOP_COUNT * (newWork - oldWork);
        }

        /**
         * prints a continue statement
         */
        public void visitContinueStatement(JContinueStatement self,
                                           String label) {
            super.visitContinueStatement(self, label);
            work += CONTINUE;
        }

        /**
         * prints a break statement
         */
        public void visitBreakStatement(JBreakStatement self,
                                        String label) {
            super.visitBreakStatement(self, label);
            work += BREAK;
        }

        // ----------------------------------------------------------------------
        // EXPRESSION
        // ----------------------------------------------------------------------

        /**
         * Adds to work estimate an amount for an arithmetic op of type
         * expr.  Assumes <pre>expr</pre> is integral unless the type is explicitly
         * float or double.
         */
        private void countArithOp(JExpression expr) {
            if (expr.getType()==CStdType.Float ||
                expr.getType()==CStdType.Double) {
                int add=FLOAT_ARITH_OP;
                if(expr instanceof JDivideExpression)
                    add*=16;
                work += add;
            } else {
                work += INT_ARITH_OP;
            }
        }

        /**
         * prints an unary plus expression
         */
        public void visitUnaryPlusExpression(JUnaryExpression self,
                                             JExpression expr) {
            super.visitUnaryPlusExpression(self, expr);
            countArithOp(self);
        }

        /**
         * prints an unary minus expression
         */
        public void visitUnaryMinusExpression(JUnaryExpression self,
                                              JExpression expr) {
            super.visitUnaryMinusExpression(self, expr);
            countArithOp(self);
        }

        /**
         * prints a bitwise complement expression
         */
        public void visitBitwiseComplementExpression(JUnaryExpression self,
                                                     JExpression expr)
        {
            super.visitBitwiseComplementExpression(self, expr);
            countArithOp(self);
        }

        /**
         * prints a logical complement expression
         */
        public void visitLogicalComplementExpression(JUnaryExpression self,
                                                     JExpression expr)
        {
            super.visitLogicalComplementExpression(self, expr);
            countArithOp(self);
        }

        /**
         * prints a shift expression
         */
        public void visitShiftExpression(JShiftExpression self,
                                         int oper,
                                         JExpression left,
                                         JExpression right) {
            super.visitShiftExpression(self, oper, left, right);
            countArithOp(self);
        }

        /**
         * prints a shift expressiona
         */
        public void visitRelationalExpression(JRelationalExpression self,
                                              int oper,
                                              JExpression left,
                                              JExpression right) {
            super.visitRelationalExpression(self, oper, left, right);
            countArithOp(self);
        }

        /**
         * prints a prefix expression
         */
        public void visitPrefixExpression(JPrefixExpression self,
                                          int oper,
                                          JExpression expr) {
            super.visitPrefixExpression(self, oper, expr);
            countArithOp(self);
        }

        /**
         * prints a postfix expression
         */
        public void visitPostfixExpression(JPostfixExpression self,
                                           int oper,
                                           JExpression expr) {
            super.visitPostfixExpression(self, oper, expr);
            countArithOp(self);
        }

        /**
         * prints a name expression
         */
        public void visitNameExpression(JNameExpression self,
                                        JExpression prefix,
                                        String ident) {
            super.visitNameExpression(self, prefix, ident);
            work += MEMORY_OP;
        }

        /**
         * prints an array allocator expression
         */
        public void visitBinaryExpression(JBinaryExpression self,
                                          String oper,
                                          JExpression left,
                                          JExpression right) {
            super.visitBinaryExpression(self, oper, left, right);
            countArithOp(self);
        }

        /**
         * prints a method call expression
         */
        public void visitMethodCallExpression(JMethodCallExpression self,
                                              JExpression prefix,
                                              String ident,
                                              JExpression[] args) {
            super.visitMethodCallExpression(self, prefix, ident, args);
            // Known values from profiling RAW code:
            if (ident.equals("acos")) work += 515;
            else if (ident.equals("acosh")) work += 665;
            else if (ident.equals("acosh")) work += 665;
            else if (ident.equals("asin")) work += 536;
            else if (ident.equals("asinh")) work += 578;
            else if (ident.equals("atan")) work += 195;
            else if (ident.equals("atan2")) work += 272;
            else if (ident.equals("atanh")) work += 304;
            else if (ident.equals("ceil")) work += 47;
            else if (ident.equals("cos")) work += 120;
            else if (ident.equals("cosh")) work += 368;
            else if (ident.equals("exp")) work += 162;
            else if (ident.equals("expm1")) work += 220;
            else if (ident.equals("floor")) work += 58;
            else if (ident.equals("fmod")) work += 147;
            else if (ident.equals("frexp")) work += 60;
            else if (ident.equals("log")) work += 146;
            else if (ident.equals("log10")) work += 212;
            else if (ident.equals("log1p")) work += 233;
            else if (ident.equals("modf")) work += 41;
            else if (ident.equals("pow")) work += 554;
            else if (ident.equals("sin")) work += 97;
            else if (ident.equals("sinh")) work += 303;
            else if (ident.equals("sqrt")) work += 297;
            else if (ident.equals("tan")) work += 224;
            else if (ident.equals("tanh")) work += 288;
            // not from profiling: round(x) is currently macro for floor((x)+0.5)
            else if (ident.equals("round")) work += (58 + FLOAT_ARITH_OP);
            // not from profiling: just stuck in here to keep compilation of gmti 
            // from spewing warnings.
            else if (ident.equals("abs")) work += 60;
            else if (ident.equals("max")) work += 60;
            else if (ident.equals("min")) work += 60;
            else
                {
                    JMethodDeclaration target = findMethod(ident);
                    if (target != null) {
                        target.accept(this);
                    } else {
                        System.err.println("Warning:  Work estimator couldn't find target method \"" + ident + "\"" + "\n" + 
                                           "   Will assume constant work overhead of " + WorkConstants.UNKNOWN_METHOD_CALL);
                        work += UNKNOWN_METHOD_CALL;
                    }
                }
            work += METHOD_CALL_OVERHEAD;
        }

        /**
         * only exists here to abort recursive calls.
         */
        @Override
        public void visitMethodDeclaration(JMethodDeclaration self,
                int modifiers,
                CType returnType,
                String ident,
                JFormalParameter[] parameters,
                CClassType[] exceptions,
                JBlock body) {
            if (methodsBeingProcessed.contains(self)) {
                System.err.println("Work estimator may underestimate for recursive call to " + self);
            } else {
                methodsBeingProcessed.add(self);
                super.visitMethodDeclaration(self, modifiers, returnType, ident, parameters, exceptions, body);
                methodsBeingProcessed.remove(self);
            }
        }
        
        /**
         * prints an equality expression
         */
        public void visitEqualityExpression(JEqualityExpression self,
                                            boolean equal,
                                            JExpression left,
                                            JExpression right) {
            super.visitEqualityExpression(self, equal, left, right);
            countArithOp(self);
        }

        /**
         * prints a conditional expression
         */
        public void visitConditionalExpression(JConditionalExpression self,
                                               JExpression cond,
                                               JExpression left,
                                               JExpression right) {
            super.visitConditionalExpression(self, cond, left, right);
            work += IF;
        }

        /**
         * prints a compound expression
         */
        public void visitCompoundAssignmentExpression(JCompoundAssignmentExpression self,
                                                      int oper,
                                                      JExpression left,
                                                      JExpression right) {
            super.visitCompoundAssignmentExpression(self, oper, left, right);
            // no work count for assignments, as most arith ops, memory
            // ops, etc., have a destination that takes care of the assign
        }

        /**
         * prints a field expression
         */
        public void visitFieldExpression(JFieldAccessExpression self,
                                         JExpression left,
                                         String ident) {
            super.visitFieldExpression(self, left, ident);
            work += MEMORY_OP;
        }

        /**
         * prints a compound assignment expression
         */
        public void visitBitwiseExpression(JBitwiseExpression self,
                                           int oper,
                                           JExpression left,
                                           JExpression right) {
            super.visitBitwiseExpression(self, oper, left, right);
            countArithOp(self);
        }

        /**
         * prints an assignment expression
         */
        public void visitAssignmentExpression(JAssignmentExpression self,
                                              JExpression left,
                                              JExpression right) {
            // try to leave out const prop remnants
            if (!(left instanceof JLocalVariableExpression &&
                  ((JLocalVariableExpression)left).getVariable().getIdent().indexOf(Propagator.TEMP_VARIABLE_BASE)!=-1)) {
                super.visitAssignmentExpression(self, left, right);
            }
            // no work count for assignments, as most arith ops, memory
            // ops, etc., have a destination that takes care of the assign
        }

        /**
         * prints an array length expression
         */
        public void visitArrayAccessExpression(JArrayAccessExpression self,
                                               JExpression prefix,
                                               JExpression accessor) {
            super.visitArrayAccessExpression(self, prefix, accessor);
            // the work estimate gets worse (e.g. for beamformer 4x4) if
            // we include array expressions, oddly enough.
            work += 0;
        }

    }
}
