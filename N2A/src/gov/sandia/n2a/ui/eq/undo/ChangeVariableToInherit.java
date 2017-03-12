/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeVariableToInherit extends Undoable
{
    protected List<String> path;
    protected MNode  treeBefore;
    protected String valueAfter;

    /**
        @param variable The direct container of the node being changed.
    **/
    public ChangeVariableToInherit (NodeVariable variable, String valueAfter)
    {
        NodeBase parent = (NodeBase) variable.getParent ();
        path = parent.getKeyPath ();
        this.valueAfter = valueAfter;
        treeBefore = new MVolatile (variable.source.key (), "");
        treeBefore.merge (variable.source.getSource ());  // Built from the top-level doc, not the collated tree.
    }

    public void undo ()
    {
        super.undo ();

        NodePart parent = (NodePart) locateNode (path);
        if (parent == null) throw new CannotUndoException ();

        // Update the database
        MPart mparent = parent.source;
        mparent.clear ("$inherit");
        String nameBefore = treeBefore.key ();
        mparent.set ("", nameBefore).merge (treeBefore);

        // Update the GUI

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        parent.build ();
        parent.findConnections ();
        parent.filter (model.filterLevel);
        if (parent.visible (model.filterLevel)) model.nodeStructureChanged (parent);

        mep.panelEquations.updateVisibility (parent.child (nameBefore).getPath ());
    }

    public void redo ()
    {
        super.redo ();

        NodePart parent = (NodePart) locateNode (path);
        if (parent == null) throw new CannotRedoException ();

        // Update database
        MPart mparent = parent.source;
        mparent.clear (treeBefore.key ());
        mparent.set (valueAfter, "$inherit");

        // Update GUI

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        parent.build ();
        parent.findConnections ();
        parent.filter (model.filterLevel);
        model.nodeStructureChanged (parent);

        mep.panelEquations.updateVisibility (parent.child ("$inherit").getPath ());
    }
}
