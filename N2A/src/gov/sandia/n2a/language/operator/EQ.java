/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.language.Comparison;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;

public class EQ extends Comparison
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "==";
            }

            public Operator createInstance ()
            {
                return new EQ ();
            }
        };
    }

    public int precedence ()
    {
        return 7;
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).EQ (operand1.eval (context));
    }

    public String toString ()
    {
        return "==";
    }
}
