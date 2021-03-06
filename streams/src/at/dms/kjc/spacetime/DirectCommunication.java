package at.dms.kjc.spacetime;

import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.backendSupport.FilterInfo;
import at.dms.kjc.backendSupport.Layout;
import at.dms.kjc.common.CommonUtils;
import at.dms.kjc.iterator.*;
import at.dms.util.Utils;
import java.util.List;
import java.util.ListIterator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Vector;
import java.util.HashSet;
import java.io.*;
import at.dms.compiler.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.slicegraph.FilterSliceNode;
import at.dms.kjc.slicegraph.InputSliceNode;
import at.dms.kjc.slicegraph.SchedulingPhase;

import java.util.Hashtable;
import java.math.BigInteger;
import at.dms.kjc.slicegraph.FilterContent;
import at.dms.util.SIRPrinter;

/**
 * If we can, this class will generate filter code that does not use a 
 * peek buffer, so just read the values from the static network and write them to the 
 * static network.  It will only work if the code does not peek and if it has all pops 
 * before pushes.  
 * 
 * It adheres to the interface defined in 
 * {@link at.dms.kjc.spacetime.RawExecutionCode}.
 * 
 * @author mgordon
 *
 */

public class DirectCommunication extends RawExecutionCode 
{ 
    /**
     * Test if we can generate direct communication code for this filter meaning
     * there are no peeks, that all pops come before all pushes, its not a two 
     * stage filter, and it does not have items remaining on its inputs buffer after the 
     * init stage. 
     * 
     * @param fi The filter.
     * @return True if we can generate direct communication code (no peek buffer) for 
     * the filter.
     */
    public static boolean testDC(FilterInfo fi) 
    {
        boolean dynamicInput = false;
        if (fi.sliceNode.getPrevious().isInputSlice()) {
            if (!IntraSliceBuffer.getBuffer(
                    (InputSliceNode)fi.sliceNode.getPrevious(),
                    fi.sliceNode).isStaticNet())
                dynamicInput = true;
        }
        
        FilterContent filter = fi.filter;
        //runs some tests to see if we can 
        //generate code direct commmunication code
        //  if (KjcOptions.ratematch)
        //    return false;
    
        if (filter.isTwoStage()) {
            CommonUtils.println_debugging(filter + " can't use direct comm: Two Stage");
            return false;
        }
        if (fi.remaining > 0) {
            CommonUtils.println_debugging(filter + " can't use direct comm: Remaining = " +
                                     fi.remaining);
            return false;
        }
        if (fi.peek > fi.pop) {
            CommonUtils.println_debugging(filter + " can't use direct comm: Peeking");
            return false;     
        }
    
        if (PeekFinder.findPeek(filter.getWork())) {
            CommonUtils.println_debugging(filter + " can't use direct comm: Peek Statement");
            return false;
        }
        
        if (!dynamicInput && at.dms.kjc.slicegraph.PeekPopPushInHelper.check(fi.filter))
            return false;
        
        // for a filter with dynamic input we don't care if the pushes and
        // pops are intermixed, because the pops will use the dynamic network
        // and the switch will only be used for the pushes...
        if (!dynamicInput && PushBeforePop.check(filter.getWork())) {
            CommonUtils.println_debugging(filter + " can't use direct comm: Push before pop");
            return false;
        }
    
        //must popping a scalar
        if (filter.getInputType().isClassType() ||
            filter.getInputType().isArrayType()) {
            CommonUtils.println_debugging(filter + " can't use direct comm: Input not scalar");
            return false;
        }
    
        //must be pushing a scalar
        if (filter.getOutputType().isClassType() ||
            filter.getOutputType().isArrayType()) {    
            CommonUtils.println_debugging(filter + " can't use direct comm: Output not scalar");
            return false;
        }
    
        //all tests pass
        return true;
    }

    /**
     * Create a new object that is ready to convert filterInfo's communication 
     * into direct communication over Raw's network.
     * 
     * @param tile The tile filterInfo is mapped to.
     * @param filterInfo The filter to convert.
     * @param layout The layout of the application.
     */
    public DirectCommunication(RawTile tile, FilterInfo filterInfo, Layout layout) 
    {
        super(tile, filterInfo, layout);
        FilterSliceNode node=filterInfo.sliceNode;
        System.out.println(tile +  " Generating code for " + filterInfo.filter + " using Direct Comm.");
    }

    /**
     * Return the variables that are generated by this pass and that
     * need to be added to the fields of the tile.
     * 
     * @return The variables that are generated by this pass.
     */
    public JFieldDeclaration[] getVarDecls() 
    {
        Vector<JFieldDeclaration> decls = new Vector<JFieldDeclaration>();
        FilterContent filter = filterInfo.filter;

        for (int i = 0; i < filter.getFields().length; i++) 
            decls.add(filter.getFields()[i]);
    
        //index variable for certain for loops
        JVariableDefinition exeIndexVar = 
            new JVariableDefinition(null, 
                                    0, 
                                    CStdType.Integer,
                                    exeIndex + uniqueID,
                                    null);

        //remember the JVarDef for latter (in the raw main function)
        generatedVariables.exeIndex = exeIndexVar;
        decls.add(new JFieldDeclaration(null, exeIndexVar, null, null));
    
        //index variable for certain for loops
        JVariableDefinition exeIndex1Var = 
            new JVariableDefinition(null, 
                                    0, 
                                    CStdType.Integer,
                                    exeIndex1 + uniqueID,
                                    null);

        generatedVariables.exeIndex1 = exeIndex1Var;
        decls.add(new JFieldDeclaration(null, exeIndex1Var, null, null));

        //all the pop statements in the work function to function calls
        filter.getWork().accept(new DirectConvertCommunication(gdnInput));
        //conver all the push statements into method calls
        ConvertPushesToMethCall.doit(filterInfo, gdnOutput);
        
        return decls.toArray(new JFieldDeclaration[0]);
    }
    
    /**
     * Calculate and return the method that will implement one execution
     * of this filter in the primepump stage.  This method may be called multiple
     * times depending on the number of stages in the primepump stage itself.   
     * 
     * @return The method that implements one stage of the primepump exeuction of this
     * filter. 
     */
    public JMethodDeclaration getPrimePumpMethod() 
    {
        if (primePumpMethod != null)
            return primePumpMethod;
        
        JBlock statements = new JBlock(null, new JStatement[0], null);
        FilterContent filter = filterInfo.filter;
    
        //add the calls to the work function in the prime pump stage
        statements.addStatement(getWorkFunctionBlock(false, filterInfo.steadyMult)); 

        primePumpMethod = new JMethodDeclaration(null, at.dms.kjc.Constants.ACC_PUBLIC,
                                      CStdType.Void,
                                      primePumpStage + uniqueID,
                                      JFormalParameter.EMPTY,
                                      CClassType.EMPTY,
                                      statements,
                                      null,
                                      null);
        return primePumpMethod;
    }
    
    /**
     * Return the code that will call the work work function once.
     * It will either be the entire function inlined or a function call.
     * 
     * @see RawExecutionCode#INLINE_WORK
     * 
     * @param filter The filter content for this filter.
     * 
     * @return The code to execute the work function once.
     */
    private JStatement getWorkFunctionCall(FilterContent filter) 
    {
        if (INLINE_WORK)    
            return (JBlock)ObjectDeepCloner.deepCopy(filter.getWork().getBody());
        else 
            return new JExpressionStatement(null, 
                                            new JMethodCallExpression(null,
                                                                      new JThisExpression(null),
                                                                      filter.getWork().getName(),
                                                                      new JExpression[0]),
                                            null);
    }
    
    /**
     * Calculate and return the method that implements the init stage 
     * computation for this filter.  It should be called only once in the 
     * generated code.
     * <p>
     * This does not include the call to the init function of the filter.
     * That is done in {@link RawComputeCodeStore#addInitFunctionCall}. 
     * 
     * @return The method that implements the init stage for this filter.
     */
    public JMethodDeclaration getInitStageMethod() 
    {
        JBlock statements = new JBlock(null, new JStatement[0], null);
        FilterContent filter = filterInfo.filter;

        //if we have gdn output then we have to set up the gdn packet header for
        //each gdn send
        if (gdnOutput) {
            statements.addStatement(setupGDNStore(SchedulingPhase.INIT));
        }
        
        //add the calls for the work function in the initialization stage
        statements.addStatement(generateInitWorkLoop(filter));
        //add the necessary handling of dram cache alignment over the gdn
        statements.addStatement(gdnCacheAlign(true));
        
        return new JMethodDeclaration(null, at.dms.kjc.Constants.ACC_PUBLIC,
                                      CStdType.Void,
                                      initStage + uniqueID,
                                      JFormalParameter.EMPTY,
                                      CClassType.EMPTY,
                                      statements,
                                      null,
                                      null);
    }
    
    /**
     * Return an array of methods for any helper methods that we create
     * or that were present in this filter.  They need to be added to the
     * methods of the entire tile.
     * 
     * @return helper methods that need to be placed in the tile's code.
     */
    public JMethodDeclaration[] getHelperMethods() 
    {
        Vector<JMethodDeclaration> methods = new Vector<JMethodDeclaration>();
        /*
        //add all helper methods, except work function
        for (int i = 0; i < filterInfo.filter.getMethods().length; i++) 
        if (!(filterInfo.filter.getMethods()[i].equals(filterInfo.filter.getWork())))
        methods.add(filterInfo.filter.getMethods()[i]);
        */
        for (int i = 0; i < filterInfo.filter.getMethods().length; i++) {
            //don't generate code for the work function if we are inlining!
            if (INLINE_WORK && 
                    filterInfo.filter.getMethods()[i] == filterInfo.filter.getWork())
                continue;
            methods.add(filterInfo.filter.getMethods()[i]);
        }
    
        return methods.toArray(new JMethodDeclaration[0]);    
    }
    
    /** 
     * Return the block to call the work function in the steady state
     */
    public JBlock getSteadyBlock() 
    {
        return getWorkFunctionBlock(true, filterInfo.steadyMult);
    }
    
    /**
     * Generate code to receive data and call the work function mult times.
     * 
     * @param steady if true, then steady state, if false, then primepump
     * @param mult the work function will be called this many times.
     * 
     * @return code to receive data and call the work function mult times.
     **/
    private JBlock getWorkFunctionBlock(boolean steady, int mult)
    {
        JBlock block = new JBlock(null, new JStatement[0], null);
        FilterContent filter = filterInfo.filter;
        JBlock workBlock = new JBlock(null, new JStatement[0], null);
        
        //if we are compressing the switch code, then send the rates to the switch
        sendRatesToSwitch(filterInfo, workBlock);
        
        workBlock.addStatement(getWorkFunctionCall(filter));
            
        //create the for loop that will execute the work function
        //local variable for the work loop
        JVariableDefinition loopCounter = new JVariableDefinition(null,
                                                                  0,
                                                                  CStdType.Integer,
                                                                  workCounter,
                                                                  null);
        JStatement loop = 
            Utils.makeForLoopLocalIndex(workBlock, loopCounter, new JIntLiteral(mult));
    
        block.addStatement(new JVariableDeclarationStatement(null,
                                                             loopCounter,
                                                             null));
        
        //if we have gdn output then we have to set up the gdn packet header for
        //each gdn send
        if (gdnOutput) {
            if (steady) {
                block.addStatement(setupGDNStore(SchedulingPhase.STEADY));
            } else {
                block.addStatement(setupGDNStore(SchedulingPhase.PRIMEPUMP));
            }
        }
        
        block.addStatement(loop);
        //add the necessary handling of dram cache alignment over the gdn
        block.addStatement(gdnCacheAlign(false));
        /*
          return new JMethodDeclaration(null, at.dms.kjc.Constants.ACC_PUBLIC,
          CStdType.Void,
          steadyStage + uniqueID,
          JFormalParameter.EMPTY,
          CClassType.EMPTY,
          block,
          null,
          null);
        */
        return block;
    }
    
    /**
     * Generate the loop for the work function firings in the 
     * initialization schedule.  This does not include receiving the
     * necessary items for the first firing.  This is handled in  
     * {@link DirectCommunication#getInitStageMethod}.
     * This block will generate code to receive items for all subsequent 
     * calls of the work function in the init stage plus the class themselves. 
     *
     * @param filter The filter 
     * @param generatedVariables The vars to use.
     * 
     * @return The code to fire the work function in the init stage.
     */
    JStatement generateInitWorkLoop(FilterContent filter)
    {
        JBlock block = new JBlock(null, new JStatement[0], null);

        //clone the work function and inline it
        JStatement workBlock = 
            getWorkFunctionCall(filter);
    
        //if we are in debug mode, print out that the filter is firing
        if (SpaceTimeBackend.FILTER_DEBUG_MODE) {
            block.addStatement
                (new SIRPrintStatement(null,
                                       new JStringLiteral(null, filter.getName() + " firing (init)."),
                                       null));
        }
    
        block.addStatement(workBlock);
    
        //return the for loop that executes the block init - 1
        //times
        return Utils.makeForLoopFieldIndex(block, generatedVariables.exeIndex1, 
                           new JIntLiteral(filterInfo.initMult));
    }

    /**
     * This class will search for a peek statement and 
     * determine if there are any peek statements in a given method.
     * 
     * @author mgordon
     *
     */
    static class PeekFinder extends SLIREmptyVisitor 
    {
        private static boolean found;

        /**
         * Return true if we find a peek expression in 
         * method.
         * 
         * @param method The method to search.
         * @return return true if we find a peek in method.
         */
        public static boolean findPeek(JMethodDeclaration method) 
        {
            found = false;
            method.accept(new PeekFinder());
            return found;
        }
    
        /**
         * if we find a peek expression set found to true;
         */
        public void visitPeekExpression(SIRPeekExpression self,
                                        CType tapeType,
                                        JExpression arg) {
            found = true;
        }
    }

    /**
     * This class determines if all the pop statements occur
     * before the first push statement in a given method.  
     * 
     * @author mgordon
     *
     */
    static class PushBeforePop extends SLIREmptyVisitor 
    {
        /** true if we have encounted a push statement */
        private static boolean sawPush;
        /** true if we have seen a push before a pop statement 
         * or it might occur.
         */
        private static boolean pushBeforePop;

        /**
         * Return true if we are guaranteed that all pop statements
         * occur before the first push statement.
         * 
         * @param method The method to check.
         * @return true if we are guaranteed that all pop statements
         * occur before the first push statement.
         */
        public static boolean check(JMethodDeclaration method) 
        {
            sawPush = false;
            pushBeforePop = false;
    
            method.accept(new PushBeforePop());
            return pushBeforePop;
        }

        /**
         * Fail, we should not see a peek statement!
         */
        public void visitPeekExpression(SIRPeekExpression self,
                                        CType tapeType,
                                        JExpression arg) {
            Utils.fail("Should not see a peek expression");
        }

        /**
         * If we have encountered a push, then we can have pushes 
         * before a pop, remember this.
         */
        public void visitPopExpression(SIRPopExpression self,
                                       CType tapeType) {
            if (sawPush)
                pushBeforePop = true;
        }

        /**
         * Remember that we have seen a push expression and vist the
         * arg.
         */
        public void visitPushExpression(SIRPushExpression self,
                                        CType tapeType,
                                        JExpression arg) {
            // I guess this should be reversed, but it will flag more
            // problems this way (I don't like to see push(pop())
            // see at.dms.kjc.common.SeparatePushPop
            sawPush = true;
            arg.accept(this);
        }
    
        /**
         * For all loops, visit the cond and body twice to make sure that 
         * if a push statement occurs in the body and 
         * after all the pops, we will flag this as a 
         * case where a push comes before a pop. 
         */
        public void visitWhileStatement(JWhileStatement self,
                JExpression cond,
                JStatement body) {
            cond.accept(this);
            body.accept(this);
            //second pass
            cond.accept(this);
            body.accept(this);
        }
        
        /**
         * For all loops, visit the cond and body twice to make sure that 
         * if a push statement occurs in the body and 
         * after all the pops, we will flag this as a 
         * case where a push comes before a pop. 
         */
        public void visitForStatement(JForStatement self,
                                      JStatement init,
                                      JExpression cond,
                                      JStatement incr,
                                      JStatement body) {
            if (init != null) {
                init.accept(this);
            }
            if (cond != null) {
                cond.accept(this);
            }
            if (incr != null) {
                incr.accept(this);
            }
            body.accept(this);
            //second pass
            if (cond != null) {
                cond.accept(this);
            }
            if (incr != null) {
                incr.accept(this);
            }
            body.accept(this);
        }
        
        /**
         * For all loops, visit the cond and body twice to make sure that 
         * if a push statement occurs in the body and 
         * after all the pops, we will flag this as a 
         * case where a push comes before a pop. 
         */
        public void visitDoStatement(JDoStatement self,
                                     JExpression cond,
                                     JStatement body) {
            body.accept(this);
            cond.accept(this);
            //second pass
            body.accept(this);
            cond.accept(this);
        }
    }
    
    /**
     * This class will convert all pop expressions into 
     * reads from either the gdn or the static network, operates
     * in place.  Right now we replace the pop expression with a method 
     * call that will indicate that it is a network read.
     * 
     * @author mgordon
     *
     */
    static class DirectConvertCommunication extends SLIRReplacingVisitor {
        private boolean dynamic;

        /**
         * Construct a new converter ready to convert all pop expressions into
         * reads from the gdn or the static net.
         * 
         * @param dynamicInput if true, read from the gdn, otherwise static net.
         */
        public DirectConvertCommunication(boolean dynamicInput) {
            dynamic = dynamicInput;
        }

        /**
         * Visit assignment expressions if this is a pop of a structure then
         * call the an optimized method for performing the assignment.  Otherwise
         * continue the visit as normal. 
         * 
         * @param oldself The assignment expression.
         * @param oldleft The left
         * @param oldright the right
         * 
         * @return The new expression.
         */
        public Object visitAssignmentExpression(JAssignmentExpression oldself,
                                                JExpression oldleft, JExpression oldright) {
            // a little optimization, use the pointer version of the
            // structure's pop in struct.h to avoid copying
            if (oldright instanceof JCastExpression
                && (((JCastExpression) oldright).getExpr() instanceof SIRPopExpression)) {
                SIRPopExpression pop = (SIRPopExpression) ((JCastExpression) oldright)
                    .getExpr();

                if (pop.getType().isClassType()) {
                    assert false : "structs over tapes is probably broken!";
                    JExpression left = (JExpression) oldleft.accept(this);

                    JExpression[] arg = { left };

                    JMethodCallExpression receive = new JMethodCallExpression(
                                                                              null, new JThisExpression(null),
                                                                              RawExecutionCode.structReceivePrefix
                                                                              + (dynamic ? "Dynamic" : "Static")
                                                                              + pop.getType(), arg);
                    receive.setTapeType(pop.getType());
                    return receive;
                }
                if (pop.getType().isArrayType()) {
                    return null;
                }
            }

            // otherwise do the normal thing
            JExpression self = 
                (JExpression) super.visitAssignmentExpression(oldself, oldleft, oldright);
            return self;
        }

        
        /**
         * Visit a pop expression and convert the pop expression into 
         * a method call that will later be converted into a read from the 
         * desired network.
         * 
         * @param oldSelf The pop expression.
         * @param oldTapeType The type of the pop expression.
         * @return The method call.
         */
        public Object visitPopExpression(SIRPopExpression oldSelf,
                                         CType oldTapeType) {

            // do the super
            SIRPopExpression self = (SIRPopExpression) super
                .visitPopExpression(oldSelf, oldTapeType);

            // if this is a struct, use the struct's pop method, generated in
            // struct.h
            if (self.getType().isClassType()) {
                assert false : "Structs over tapes unimplemented!";
                return new JMethodCallExpression(null,
                                                 new JThisExpression(null), "pop" + self.getType(),
                                                 new JExpression[0]);
            } else if (self.getType().isArrayType()) {
                return null;
            } else {
                JMethodCallExpression receive = 
                    new JMethodCallExpression(null,
                            new JThisExpression(null),
                            (dynamic ? RawExecutionCode.gdnReceiveMethod : 
                                RawExecutionCode.staticReceiveMethod),
                            new JExpression[0]);
                receive.setTapeType(self.getType());
                return receive;
            }
            /*
             * else { if (self.getType().isFloatingPoint()) return new
             * JLocalVariableExpression (null, new JGeneratedLocalVariable(null,
             * 0, CStdType.Float, dynamic ? Util.CGNIFPVAR : Util.CSTIFPVAR,
             * null)); else return new JLocalVariableExpression (null, new
             * JGeneratedLocalVariable(null, 0, CStdType.Integer, dynamic ?
             * Util.CGNIINTVAR : Util.CSTIINTVAR, null)); }
             */
        }

        /**
         * We should not see a peek expression, so fail!
         */
        public Object visitPeekExpression(SIRPeekExpression oldSelf,
                                          CType oldTapeType, JExpression oldArg) {
            Utils.fail("Should not see a peek expression when generating "
                       + "direct communication");
            return null;
        }
    }
}