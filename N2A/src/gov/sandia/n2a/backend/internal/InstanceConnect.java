/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class InstanceConnect extends InstanceTemporaries
{
    public InstanceConnect (Instance wrapped, Simulator simulator)
    {
        super (wrapped, simulator);
    }

    public Type get (Variable v)
    {
        if (v == bed.connect) return new Scalar (1);
        if (v == bed.live   ) return new Scalar (0);
        if (v == bed.dt     ) return new Scalar (((Part) wrapped.container).event.dt);  // Refer to container, because during connect phase, part has not yet been added to event.
        return super.get (v);
    }
}
