/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class ChangeDoc extends Undoable
{
    protected String before;
    protected String after;

    public ChangeDoc (String before, String after)
    {
        this.before = before;
        this.after  = after;
    }

    public void undo ()
    {
        super.undo ();
        rename (after, before);
    }

    public void redo ()
    {
        super.redo ();
        rename (before, after);
    }

    public boolean anihilate ()
    {
        return before.equals (after);
    }

    public static void rename (String A, String B)
    {
        AppData.models.move (A, B);
        ModelEditPanel mep = ModelEditPanel.instance;
        MNode doc = AppData.models.child (B);
        mep.panelEquations.loadRootFromDB (doc);  // lazy; only loads if not already loaded
        NodePart root = mep.panelEquations.root;
        root.setUserObject ();
        mep.panelEquations.tree.requestFocusInWindow ();  // likewise, focus only moves if it is not already on equation tree
        mep.panelEquations.tree.setSelectionRow (0);
        mep.panelEquations.model.nodeChanged (root);
        mep.panelSearch.list.repaint ();  // Because the change in document name does not directly notify the list model.
    }
}
