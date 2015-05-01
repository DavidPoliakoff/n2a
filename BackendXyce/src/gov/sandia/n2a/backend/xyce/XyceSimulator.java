/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

public class XyceSimulator implements Simulator {

    @Override
    public String getName() {
        return "Xyce2";
    }

    @Override
    public String[] getCompatibleModelTypes() {
        return new String[] {"n2a"};
    }

    public ParameterDomain getSimulatorParameters () {
        return new XyceSimulation().getAllParameters();
    }

    @Override
    public ParameterDomain getOutputVariables (Object model)
    {
        try
        {
            ModelOrient mo = (ModelOrient) model;
            EquationSet s = new EquationSet (mo.getSource ());
            if (s.name.length () < 1) s.name = "Model";
            s.resolveLHS ();
            return s.getOutputParameters ();
        }
        catch (Exception error)
        {
            return null;
        }
    }

    @Override
    public boolean canHandleRunEnsembleParameter(Object model, Object key,
            ParameterSpecification spec) {
    // disabling this functionality until we resolve issues with xyce .step
        // key should be strings like "model.layers.hh.V"
//        ParameterKeyPath keyPath = (ParameterKeyPath) key;
//        String top = (String) keyPath.get(0);
//        if (!top.equals("Model")) {
//            return false;
//        }
//        String paramName = (String) keyPath.get(3);
//        if (paramName.startsWith("$") || paramName.endsWith("'")) {
//            return false;
//        }
//        // at this point, parameter OK; check the spec
//        if (spec instanceof StepParameterSpecification) {
//            return true;
//        }
        return false;
    }

    @Override
    public Simulation createSimulation() {
        return new XyceSimulation();
    }
}
