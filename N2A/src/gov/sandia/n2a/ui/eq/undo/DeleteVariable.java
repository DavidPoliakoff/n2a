/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class DeleteVariable extends Undoable
{
    protected List<String> path;  // to variable node
    protected int          index; // where to insert among siblings
    protected boolean      canceled;
    protected MNode        savedSubtree;
    protected boolean      neutralized;

    public DeleteVariable (NodeVariable node, boolean canceled)
    {
        NodeBase container = (NodeBase) node.getParent ();
        path          = container.getKeyPath ();
        index         = container.getIndex (node);
        this.canceled = canceled;

        savedSubtree = new MVolatile (node.source.key (), "");
        savedSubtree.merge (node.source.getSource ());
    }

    public void undo ()
    {
        super.undo ();
        AddVariable.create (path, index, savedSubtree, false);
    }

    public void redo ()
    {
        super.redo ();
        AddVariable.destroy (path, canceled, savedSubtree.key ());
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddVariable)
        {
            AddVariable av = (AddVariable) edit;
            if (path.equals (av.path)  &&  savedSubtree.key ().equals (av.createSubtree.key ())  &&  av.nameIsGenerated)
            {
                neutralized = true;
                return true;
            }
        }
        return false;
    }

    public boolean anihilate ()
    {
        return neutralized;
    }
}
