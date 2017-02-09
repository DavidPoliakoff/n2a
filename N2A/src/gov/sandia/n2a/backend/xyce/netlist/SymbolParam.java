/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import gov.sandia.n2a.eqset.EquationEntry;

public class SymbolParam extends Symbol 
{
    Number value;

    public SymbolParam (EquationEntry eq)
    {
        super (eq);
    }

    @Override
    public String getDefinition (XyceRenderer renderer) 
    {
        return Xyceisms.param (eq.variable.name, renderer.pi.hashCode (), renderer.change (eq.expression));
    }

    @Override
    public String getReference (XyceRenderer renderer)
    {
        return Xyceisms.referenceVariable (eq.variable.name, renderer.pi.hashCode ());
    }
}