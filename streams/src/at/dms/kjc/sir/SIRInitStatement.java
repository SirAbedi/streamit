package at.dms.kjc.sir;

import at.dms.kjc.*;
import at.dms.compiler.*;

/**
 * Init Statement.
 *
 * This statement represents a call to an init function of a
 * sub-stream.  It should take the place of any add(...) statement in
 * StreaMIT syntax.  The arguments to the constructor of the
 * sub-stream should be the <args> in here.
 */
public class SIRInitStatement extends JStatement {

    /**
     * The arguments to the init function.
     */
    protected JExpression[] args;
    /**
     * The stream structure to initialize.
     */
    private SIRStream target;

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
     * Construct a node in the parsing tree
     */
    public SIRInitStatement(TokenReference where, 
			    JavaStyleComment[] comments, 
			    JExpression[] args, 
			    SIRStream str) {
	super(where, comments);

	this.args = args;
	this.target = str;
    }
    
    /**
     * Construct a node in the parsing tree
     */
    public SIRInitStatement() {
	super(null, null);

	this.args = null;
	this.target = null;
    }
    
    public void setArgs(JExpression[] a) {
	this.args = a;
    }

    public JExpression[] getArgs() {
	return this.args;
    }

    public void setTarget(SIRStream s) {
	this.target = s;
    }

    public SIRStream getTarget() {
	return this.target;
    }

    // ----------------------------------------------------------------------
    // SEMANTIC ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Analyses the statement (semantically) - NOT SUPPORTED YET.
     */
    public void analyse(CBodyContext context) throws PositionedError {
	at.dms.util.Utils.fail("Analysis of SIR nodes not supported yet.");
    }

    // ----------------------------------------------------------------------
    // CODE GENERATION
    // ----------------------------------------------------------------------

    /**
     * Accepts the specified visitor.
     */
    public void accept(KjcVisitor p) {
	if (p instanceof SLIRVisitor) {
	    ((SLIRVisitor)p).visitInitStatement(this, args, target);
	} else {
	    // otherwise, visit children
	    for (int i=0; i<args.length; i++) {
		args[i].accept(p);
	    }
	}
    }

    /**
     * Accepts the specified attribute visitor - just returns this for now.
     */
    public Object accept(AttributeVisitor p) {
	// visit children
	for (int i=0; i<args.length; i++) {
	    JExpression newExp = (JExpression)args[i].accept(p);
	    if (newExp!=null && newExp!=args[i]) {
		args[i] = newExp;
	    }
	}
	return this;
    }

    /**
     * Generates a sequence of bytescodes - NOT SUPPORTED YET.
     */
    public void genCode(CodeSequence code) {
	at.dms.util.Utils.fail("Codegen of SIR nodes not supported yet.");
    }
}





