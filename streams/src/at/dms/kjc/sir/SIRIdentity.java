package at.dms.kjc.sir;

import at.dms.kjc.sir.lowering.Propagator;
import at.dms.kjc.lir.LIRStreamType;
import at.dms.kjc.sir.lowering.LoweringConstants;
import at.dms.kjc.*;
import at.dms.util.*;
import at.dms.compiler.JavaStyleComment; // for debugging

/**
 * This represents a StreaMIT filter that just reads <N> item and sends it along.
 */
public class SIRIdentity extends SIRPredefinedFilter implements Cloneable, Constants {

    private JExpression rate;

    /**
     * No argument constructor, FOR AUTOMATIC CLONING ONLY.
     */
    private SIRIdentity() {
        super();
    }

    public SIRIdentity(JExpression rate, CType type) 
    {
        super(null,
              "Identity",
              /* fields */ JFieldDeclaration.EMPTY(), 
              /* methods */ JMethodDeclaration.EMPTY(),
              rate, rate, rate,
              /* input type */ type,
              /* output type */ type);
        setRate(rate);
    }
    

    public SIRIdentity(CType type) {
        super(null,
              "Identity",
              /* fields */ JFieldDeclaration.EMPTY(), 
              /* methods */ JMethodDeclaration.EMPTY(),
              new JIntLiteral(1), new JIntLiteral(1), new JIntLiteral(1),
              /* input type */ type,
              /* output type */ type);
        setRate(new JIntLiteral(1));
    }

    public void setRate(JExpression r) 
    {
        this.rate = r;
        setType(this.getInputType());
        this.setPeek(r);
        this.setPop(r);
        this.setPush(r);
    }

    public JExpression getRate() 
    {
        return rate;
    }


    public void propagatePredefinedFields(Propagator propagator) {
        JExpression newRate = (JExpression)rate.accept(propagator);
        if (newRate!=null && newRate!=rate) {
            rate = newRate;
        }
    }

    /**
     * Set the input type and output type to t
     * also sets the work function and init function
     */
    public void setType(CType t) {
        this.setInputType(t);
        this.setOutputType(t);
    
        assert rate != null : "Constructing SIRIdentity with null rate";
        
        // work function
        
        JVariableDefinition tmp = new JVariableDefinition(null, 0, t, 
                at.dms.kjc.sir.lowering.ThreeAddressCode.nextTemp(),
                new SIRPopExpression(t));
        JVariableDeclarationStatement declarePopExpr = new JVariableDeclarationStatement(tmp);
        JLocalVariableExpression referencePoppedValue = new JLocalVariableExpression(tmp);

        
        JStatement work1body[];
        if (rate instanceof JIntLiteral && 
            ((JIntLiteral)rate).intValue() == 1) {
            work1body = new JStatement[] {
                    declarePopExpr,
                    new JExpressionStatement(null, 
                            new SIRPushExpression(
                                    referencePoppedValue, t), 
                            null) };
        

        } else {
            JStatement pushPop = 
                new JBlock(new JStatement[]{
                        declarePopExpr,  
                        new JExpressionStatement(null,
                                new SIRPushExpression(referencePoppedValue, t),
                                null)});
            JVariableDefinition induction = 
                new JVariableDefinition(null, 0,
                                        CStdType.Integer,
                                        "i",
                                        new JIntLiteral(0));
            JRelationalExpression cond = new JRelationalExpression(null,
                                                                   OPE_LT,
                                                                   new JLocalVariableExpression(null,
                                                                                                induction),
                                                                   rate);

            JExpressionStatement increment = 
                new JExpressionStatement(null,
                                         new JCompoundAssignmentExpression(null,
                                                                           OPE_PLUS,
                                                                           new JLocalVariableExpression(null,
                                                                                                        induction),
                                                                           new JIntLiteral(1)),
                                         null);
            work1body= new JStatement[] { 
                    new JForStatement(null,
                       new JVariableDeclarationStatement(null, induction, null),
                       cond, increment, pushPop, 
                       new JavaStyleComment[] {
                            new JavaStyleComment("IncreaseFilterMult", true,
                                false, false)})};       
        
        }
    
        JBlock work1block = new JBlock(/* tokref   */ null,
                                       /* body     */ work1body,
                                       /* comments */ null);    
    
        JMethodDeclaration workfn =  new JMethodDeclaration( /* tokref     */ null,
                                                             /* modifiers  */ at.dms.kjc.
                                                             Constants.ACC_PUBLIC,
                                                             /* returntype */ CStdType.Void,
                                                             /* identifier */ "work",
                                                             /* parameters */ JFormalParameter.EMPTY,
                                                             /* exceptions */ CClassType.EMPTY,
                                                             /* body       */ work1block,
                                                             /* javadoc    */ null,
                                                             /* comments   */ null);
        setWork(workfn);

        // init function
        JBlock initblock = new JBlock(/* tokref   */ null,
                                      /* body     */ new JStatement[0],
                                      /* comments */ null);
        setInit(SIRStream.makeEmptyInit());
    }

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    public Object deepClone() {
        at.dms.kjc.sir.SIRIdentity other = new at.dms.kjc.sir.SIRIdentity();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.sir.SIRIdentity other) {
        super.deepCloneInto(other);
        other.rate = (at.dms.kjc.JExpression)at.dms.kjc.AutoCloner.cloneToplevel(this.rate);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}

