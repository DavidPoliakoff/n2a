/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Type;

public class Multiply extends Function
{
    public Multiply ()
    {
        name          = "*";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 4;
    }

    public Type eval (Type[] args)
    {
        return args[0].multiply (args[1]);
    }
}
