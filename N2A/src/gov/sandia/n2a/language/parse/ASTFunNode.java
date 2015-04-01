/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

/* Generated By:JJTree: Do not edit this line. ASTFunNode.java Version 4.3 */
/*
 * JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,
 * NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true
 */

package gov.sandia.n2a.language.parse;

import gov.sandia.n2a.language.EvaluationContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Type;


public class ASTFunNode extends ASTNodeBase {


    ////////////////////
    // AUTO-GENERATED //
    ////////////////////

    public ASTFunNode(Object value) {
        super(value, ExpressionParserTreeConstants.JJTFUNNODE);
    }

    public ASTFunNode(int id) {
        super(id);
    }

    public ASTFunNode(ExpressionParser p, int id) {
        super(p, id);
    }

    /** Accept the visitor. **/
    @Override
    public Object jjtAccept(ExpressionParserVisitor visitor, Object data) throws ParseException {
        return visitor.visit(this, data);
    }


    ////////////
    // CUSTOM //
    ////////////

    public Function getFunction() {
        return (Function) getValue();
    }

    @Override
    public String toString() {
        return getValue().toString();
    }

    @Override
    public String render(ASTRenderingContext context)
    {
        // Long & Short rendering
        Object value = getValue ();
        String ret = value + "(";
        for(int a = 0; a < getCount(); a++) {
            ret += context.render (getChild(a));
            if(a != getCount() - 1) {
                ret += ", ";
            }
        }
        return ret + ")";
    }


    ////////////////
    // EVALUATION //
    ////////////////

    @Override
    public Type eval(EvaluationContext context) throws EvaluationException
    {
        int count = getCount ();
        Function func = (Function) getValue();
        Type[] params = new Type[count];
        for (int c = 0; c < count; c++) params[c] = getChild (c).eval (context);
        return func.eval (params);
    }
}
/* JavaCC - OriginalChecksum=3864973bbcee2074efabcc2468ad3a8c (do not edit this line) */