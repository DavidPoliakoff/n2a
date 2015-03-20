/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

/* Generated By:JJTree: Do not edit this line. ASTUnitNode.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package gov.sandia.n2a.language.parse;

import gov.sandia.n2a.language.EvaluationContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;

import javax.measure.unit.Unit;

import org.jscience.physics.amount.Amount;

public class ASTUnitNode extends ASTNodeBase {


    ///////////
    // NOTES //
    ///////////

    // Optional unit information (provided by JScience) can
    // be present on any node.  This unit information is only
    // relevant on certain nodes.  Specifically, nodes that
    // are meant to evaluate to a single real number or vector
    // of real numbers.  This information may be present on
    // these nodes:
    //     ASTConstant (Number, not String/Boolean)
    //     ASTFunNode
    //     ASTListNode
    //     ASTOpNode (+, -, *, /, ^, =, +=, -=, *=, /=)
    //     ASTVarNode
    // If unit information is present on a node it represents
    // something different depending on the context.  They are
    // currently only actively used during tree *evaluation*.
    // During evaluation this is the effect they have:
    //     Numerical Constants: describes what units the number
    //         is in, and more or less what quantity is being
    //         measured (e.g. m/s implies Velocity, 1/s can
    //         imply Frequency, but could imply other quantities
    //         as well).  Example syntax:
    //             -47.5 {m/s^2}, 100 {kg}
    //     Variables, Functions, Lists, & Operators:
    //         After a node of this type has been evaluated to
    //         a single value two things happen:
    //             1) a consistency check is performed by verifying
    //                that the units of the result value are
    //                compatible with the units specified on the
    //                node.  For example, the following expression
    //                would not pass this check (example syntax):
    //                    (4 {kg} + 10 {kg}) {m/s}
    //                    x = 4 {N} ; y = x {C} * 10
    //                    weight = calcWeight(1, 2, 3) {m/s}
    //                    [1 {cm}, 2 {cm}, 3 {cm}] {s}  (NOT IMPL)
    //             2) if the units are compatible, the result value
    //                is converted properly to the node's desired
    //                units.  Here is an example:
    //                    (4 {cm} + 7 {cm}) {m}
    //                    x = 4 {mA} ; y = x {nA} * 10
    //                    weight = calcWeight(1, 2, 3) {kg}
    //                    [1 {cm}, 2 {cm}, 3 {cm}] {km}  (NOT IMPL)


    ////////////////////
    // AUTO-GENERATED //
    ////////////////////

    public ASTUnitNode(int id) {
        super(id);
    }

    public ASTUnitNode(ExpressionParser p, int id) {
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

    @Override
    public String toString() {
        return getValue().toString();
    }

    @Override
    public String render(ASTRenderingContext context)
    {
        // Long & Short rendering
        // TODO: this is probably not done... since this is essentially a post-fix operator...
        // Maybe even use the ASTOpNode class.....
        return context.render (getChild (0)) + " {" + getValue () + "}";
    }

    @Override
    public Type eval (EvaluationContext context) throws EvaluationException
    {
        throw new EvaluationException ("The current (incomplete) implementation of units must be rewritten.");
    }
}
/* JavaCC - OriginalChecksum=4155bb5987a157303897ebe3631b23cd (do not edit this line) */
