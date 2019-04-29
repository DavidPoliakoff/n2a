/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import gov.sandia.n2a.ui.eq.undo.ChangeOrder;
import gov.sandia.n2a.ui.eq.undo.Outsource;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

@SuppressWarnings("serial")
public class PanelEquationTree extends JScrollPane
{
    // Tree
    public    JTree                    tree;
    public    EquationTreeCellRenderer renderer;
    public    FilteredTreeModel        model;
    public    TransferHandler          transferHandler;
    protected PanelEquations           container;
    protected Map<MNode,StoredPath>    focusCache = new HashMap<MNode,StoredPath> ();
    protected boolean                  needsFullRepaint;

    public PanelEquationTree (PanelEquations container)
    {
        this.container = container;

        model    = new FilteredTreeModel (null);
        renderer = new EquationTreeCellRenderer ();
        tree     = new JTree (model)
        {
            public String convertValueToText (Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
            {
                if (value == null) return "";
                return ((NodeBase) value).getText (expanded, false);
            }

            public String getToolTipText (MouseEvent e)
            {
                TreePath path = getPathForLocation (e.getX (), e.getY ());
                if (path == null) return null;
                NodeBase node = (NodeBase) path.getLastPathComponent ();
                if (! (node instanceof NodeVariable)) return null;

                MPart source = node.source;
                String notes = source.get ("$metadata", "notes");
                if (notes.isEmpty ()) notes = source.get ("$metadata", "note");
                if (notes.isEmpty ()) return null;

                int paneWidth = PanelEquationTree.this.getWidth ();
                FontMetrics fm = getFontMetrics (getFont ());
                int notesWidth = fm.stringWidth (notes);
                if (notesWidth < paneWidth) return notes;

                paneWidth = Math.max (300, paneWidth);
                notes = notes.replace ("\n", "<br>");
                return "<html><p  width=\"" + paneWidth + "\">" + notes + "</p></html>";
            }

            public void updateUI ()
            {
                // We need to reset the renderer font first, before the rest of JTree's updateUI() procedure,
                // because JTree does not call renderer.updateUI() until after it has polled for cell sizes.
                renderer.earlyUpdateUI ();
                if (container.root != null) container.root.filter (model.filterLevel);  // Force update to tab stops, in case font has changed.

                super.updateUI ();
            }
        };

        tree.setExpandsSelectedPaths (true);
        tree.setScrollsOnExpand (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.SINGLE_TREE_SELECTION);  // No multiple selection. It only makes deletes and moves more complicated.
        tree.setEditable (true);
        tree.setInvokesStopCellEditing (true);  // auto-save current edits, as much as possible
        tree.setDragEnabled (true);
        tree.setToggleClickCount (0);  // Disable expand/collapse on double-click
        ToolTipManager.sharedInstance ().registerComponent (tree);
        tree.setCellRenderer (renderer);

        final EquationTreeCellEditor editor = new EquationTreeCellEditor (tree, renderer);
        editor.addCellEditorListener (new CellEditorListener ()
        {
            @Override
            public void editingStopped (ChangeEvent e)
            {
                NodeBase node = editor.editingNode;
                editor.editingNode = null;
                node.applyEdit (tree);
            }

            @Override
            public void editingCanceled (ChangeEvent e)
            {
                NodeBase node = editor.editingNode;
                editor.editingNode = null;

                // We only get back an empty string if we explicitly set it before editing starts.
                // Certain types of nodes do this when inserting a new instance into the tree, via NodeBase.add()
                // We desire in this case that escape cause the new node to evaporate.
                Object o = node.getUserObject ();
                if (! (o instanceof String)) return;

                NodeBase parent = (NodeBase) node.getParent ();
                if (((String) o).isEmpty ())
                {
                    node.delete (tree, true);
                }
                else  // The text has been restored to the original value set in node's user object just before edit. However, that has column alignment removed, so re-establish it.
                {
                    if (parent != null)
                    {
                        parent.updateTabStops (node.getFontMetrics (tree));
                        parent.allNodesChanged (model);
                    }
                }
            }
        });
        tree.setCellEditor (editor);

        InputMap inputMap = tree.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("shift UP"),   "moveUp");
        inputMap.put (KeyStroke.getKeyStroke ("shift DOWN"), "moveDown");
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),     "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),     "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"), "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),      "startEditing"); 
        inputMap.put (KeyStroke.getKeyStroke ("ctrl ENTER"), "startEditing");

        ActionMap actionMap = tree.getActionMap ();
        actionMap.put ("moveUp", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                moveSelected (-1);
            }
        });
        actionMap.put ("moveDown", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                moveSelected (1);
            }
        });
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                addAtSelected ("");
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                deleteSelected ();
            }
        });
        actionMap.put ("startEditing", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                TreePath path = tree.getSelectionPath ();
                if (path != null  &&  ! container.locked)
                {
                    boolean isControlDown = (e.getModifiers () & ActionEvent.CTRL_MASK) != 0;
                    if (isControlDown  &&  ! (path.getLastPathComponent () instanceof NodePart)) editor.multiLineRequested = true;
                    tree.startEditingAtPath (path);
                }
            }
        });

        tree.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                if (! container.locked  &&  SwingUtilities.isLeftMouseButton (e)  &&  e.getClickCount () == 2)
                {
                    int x = e.getX ();
                    int y = e.getY ();
                    TreePath path = tree.getClosestPathForLocation (x, y);
                    if (path != null)
                    {
                        Rectangle r = tree.getPathBounds (path);
                        if (r.contains (x, y))
                        {
                            tree.setSelectionPath (path);
                            tree.startEditingAtPath (path);
                        }
                    }
                }
                else if (SwingUtilities.isRightMouseButton (e)  &&   e.getClickCount () == 1)
                {
                    TreePath path = tree.getPathForLocation (e.getX (), e.getY ());
                    if (path != null)
                    {
                        tree.setSelectionPath (path);
                        container.menuPopup.show (tree, e.getX (), e.getY ());
                    }
                }
            }
        });

        // Hack for slow Swing repaint when clicking to select new node
        tree.addTreeSelectionListener (new TreeSelectionListener ()
        {
            NodeBase oldSelection;
            Rectangle oldBounds;

            public void valueChanged (TreeSelectionEvent e)
            {
                if (! e.isAddedPath ()) return;
                TreePath path = e.getPath ();
                NodeBase newSelection = (NodeBase) path.getLastPathComponent ();
                if (newSelection == oldSelection) return;

                if (oldBounds != null) tree.paintImmediately (oldBounds);
                Rectangle newBounds = tree.getPathBounds (path);
                if (newBounds != null) tree.paintImmediately (newBounds);
                oldSelection = newSelection;
                oldBounds    = newBounds;
            }
        });

        tree.addTreeWillExpandListener (new TreeWillExpandListener ()
        {
            @Override
            public void treeWillExpand (TreeExpansionEvent event) throws ExpandVetoException
            {
            }

            @Override
            public void treeWillCollapse (TreeExpansionEvent event) throws ExpandVetoException
            {
                TreePath path = event.getPath ();
                if (((NodeBase) path.getLastPathComponent ()).isRoot ()) throw new ExpandVetoException (event);
            }
        });

        tree.addTreeExpansionListener (new TreeExpansionListener ()
        {
            public void treeExpanded (TreeExpansionEvent event)
            {
                repaintSouth (event.getPath ());
            }

            public void treeCollapsed (TreeExpansionEvent event)
            {
                repaintSouth (event.getPath ());
            }
        });

        transferHandler = new TransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                return ! container.locked  &&  xfer.isDataFlavorSupported (DataFlavor.stringFlavor);
            }

            public boolean importData (TransferSupport xfer)
            {
                if (container.locked) return false;

                MNode data = new MVolatile ();
                Schema schema;
                TransferableNode xferNode = null;  // used only to detect if the source is ourselves (equation tree)
                try
                {
                    Transferable xferable = xfer.getTransferable ();
                    StringReader reader = new StringReader ((String) xferable.getTransferData (DataFlavor.stringFlavor));
                    schema = Schema.readAll (data, reader);
                    if (xferable.isDataFlavorSupported (TransferableNode.nodeFlavor)) xferNode = (TransferableNode) xferable.getTransferData (TransferableNode.nodeFlavor);
                }
                catch (IOException | UnsupportedFlavorException e)
                {
                    return false;
                }

                // Determine paste/drop target.
                TreePath path = tree.getSelectionPath ();  // default
                DropLocation dl = xfer.getDropLocation ();
                if (dl instanceof JTree.DropLocation  &&  xfer.isDrop ()) path = ((JTree.DropLocation) dl).getPath ();

                // Handle internal DnD as a node reordering.
                PanelModel pm = PanelModel.instance;
                if (xferNode != null  &&  xfer.isDrop ()  &&  path != null)  // DnD operation is internal to the tree. (Could also be DnD between N2A windows. For now, reject that case.)
                {
                    NodeBase target = (NodeBase) path.getLastPathComponent ();
                    NodeBase targetParent = (NodeBase) target.getParent ();
                    if (targetParent == null) return false;  // If target is root node

                    NodeBase source = xferNode.getSource ();
                    if (source == null) return false;  // Probably can only happen in a DnD between N2A instances.
                    NodeBase sourceParent = (NodeBase) source.getParent ();

                    if (targetParent != sourceParent) return false;  // Don't drag node outside its containing part.
                    if (! (targetParent instanceof NodePart)) return false;  // Only rearrange children of parts (not of variables or metadata).

                    NodePart parent = (NodePart) targetParent;
                    int indexBefore = parent.getIndex (source);
                    int indexAfter  = parent.getIndex (target);
                    pm.undoManager.add (new ChangeOrder (parent, indexBefore, indexAfter));
                    return true;
                }

                // Create target tree, if needed.
                pm.undoManager.addEdit (new CompoundEdit ());
                if (path == null)
                {
                    if (container.root == null) pm.undoManager.add (new AddDoc ());
                    tree.setSelectionRow (0);
                    path = tree.getSelectionPath ();
                }
                if (xfer.isDrop ()) tree.setSelectionPath (path);
                NodeBase target = (NodeBase) path.getLastPathComponent ();

                Point location = null;
                PanelEquations pe = pm.panelEquations;
                if (xfer.getComponent () == pe.panelEquationGraph.graphPanel) location = dl.getDropPoint ();

                // An import can either be a new node in the tree, or a link (via inheritance) to an existing part.
                // In the case of a link, the part may need to be fully imported if it does not already exist in the db.
                boolean result = false;
                if (schema.type.startsWith ("Clip"))
                {
                    result = true;
                    for (MNode child : data)
                    {
                        NodeBase added = target.add (schema.type.substring (4), tree, child, location);
                        if (added == null)
                        {
                            result = false;
                            break;
                        }
                    }
                }
                else if (schema.type.equals ("Part"))
                {
                    result = true;
                    for (MNode child : data)  // There could be multiple parts.
                    {
                        // Ensure the part is in our db
                        String key = child.key ();
                        if (AppData.models.child (key) == null) pm.undoManager.add (new AddDoc (key, child));

                        // Create an include-style part
                        MNode include = new MVolatile ();  // Note the empty key. This enables AddPart to generate a name.
                        include.merge (child);  // TODO: What if this brings in a $inherit line, and that line does not match the $inherit line in the source part? One possibility is to add the new values to the end of the $inherit line created below.
                        include.clear ("$inherit");  // get rid of IDs from included part, so they won't override the new $inherit line ...
                        include.set ("\"" + key + "\"", "$inherit");
                        NodeBase added = target.add ("Part", tree, include, location);
                        if (added == null)
                        {
                            result = false;
                            break;
                        }
                    }
                }
                if (! xfer.isDrop ()  ||  xfer.getDropAction () != MOVE  ||  xferNode == null) pm.undoManager.endCompoundEdit ();  // By not closing the compound edit on a DnD move, we allow the sending side to include any changes in it when exportDone() is called.
                return result;
            }

            public int getSourceActions (JComponent comp)
            {
                return COPY_OR_MOVE;
            }

            boolean dragInitiated;  // This is a horrible hack, but the simplest way to override the default MOVE action chosen internally by Swing.
            public void exportAsDrag (JComponent comp, InputEvent e, int action)
            {
                dragInitiated = true;
                super.exportAsDrag (comp, e, action);
            }

            protected Transferable createTransferable (JComponent comp)
            {
                boolean drag = dragInitiated;
                dragInitiated = false;

                NodeBase node = getSelected ();
                if (node == null) return null;
                MVolatile copy = new MVolatile ();
                node.copy (copy);
                if (node == container.root) copy.set ("", node.source.key ());  // Remove file information from root node, if that is what we are sending.

                Schema schema = Schema.latest ();
                schema.type = "Clip" + node.getTypeName ();
                StringWriter writer = new StringWriter ();
                try
                {
                    schema.write (writer);
                    for (MNode c : copy) schema.write (c, writer);
                    writer.close ();

                    return new TransferableNode (writer.toString (), node, drag);
                }
                catch (IOException e)
                {
                }

                return null;
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                TransferableNode tn = (TransferableNode) data;
                if (action == MOVE  &&  ! container.locked)
                {
                    // It is possible for the node to be removed from the tree before we get to it.
                    // For example, a local drop of an $inherit node will cause the tree to rebuild.
                    NodeBase node = ((TransferableNode) data).getSource ();
                    if (node != null)
                    {
                        if (tn.drag)
                        {
                            if (tn.newPartName != null  &&  node != container.root  &&  node.source.isFromTopDocument ())
                            {
                                // Change this node into an include of the newly-created part.
                                PanelModel.instance.undoManager.add (new Outsource ((NodePart) node, tn.newPartName));
                            }
                        }
                        else
                        {
                            node.delete (tree, false);
                        }
                    }
                }
                PanelModel.instance.undoManager.endCompoundEdit ();  // This is safe, even if there is no compound edit in progress.
            }
        };
        tree.setTransferHandler (transferHandler);


        tree.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                if (tree.getSelectionCount () < 1)
                {
                    StoredPath sp = focusCache.get (container.record);
                    if (sp == null) tree.setSelectionRow (0);
                    else            sp.restore (tree);
                }
            }

            public void focusLost (FocusEvent e)
            {
                // The shift to the editing component appears as a loss of focus.
                // The shift to a popup menu appears as a "temporary" loss of focus.
                if (! e.isTemporary ()  &&  ! tree.isEditing ()) yieldFocus ();
            }
        });

        setViewportView (tree);
    }

    public void saveFocus (MNode record)
    {
        // Save tree state for current record, but only if it's better than the previously-saved state.
        if (focusCache.get (record) == null  ||  tree.getSelectionPath () != null) focusCache.put (record, new StoredPath (tree));
    }

    public void load ()
    {
        model.setRoot (container.root);  // triggers repaint, but may be too slow
        needsFullRepaint = true;  // next call to repaintSouth() will repaint everything
        AppData.state.set (container.record.key (), "PanelModel", "lastUsed");

        StoredPath sp = focusCache.get (container.record);
        if (sp == null)
        {
            tree.expandRow (0);
            tree.setSelectionRow (0);
        }
        else
        {
            sp.restore (tree);
        }
    }

    public void recordDeleted (MNode oldRecord)
    {
        focusCache.remove (oldRecord);
        model.setRoot (null);
        tree.paintImmediately (getViewport ().getViewRect ());
    }

    public void yieldFocus ()
    {
        if (tree.getSelectionCount () > 0)
        {
            focusCache.put (container.record, new StoredPath (tree));
            tree.clearSelection ();
        }
    }

    public NodeBase getSelected ()
    {
        NodeBase result = null;
        TreePath path = tree.getSelectionPath ();
        if (path != null) result = (NodeBase) path.getLastPathComponent ();
        if (result == null) return container.root;
        return result;
    }

    public void addAtSelected (String type)
    {
        if (container.locked) return;
        NodeBase selected = getSelected ();
        if (selected == null)  // only happens when root is null
        {
            PanelModel.instance.undoManager.add (new AddDoc ());
            if (type.equals ("Part")) return;  // Since root is itself a Part, don't create another one. For anything else, fall through and add it to the newly-created model.
            selected = container.root;
        }

        NodeBase editMe = selected.add (type, tree, null, null);
        if (editMe != null)
        {
            TreePath path = new TreePath (editMe.getPath ());
            tree.scrollPathToVisible (path);
            tree.setSelectionPath (path);
            tree.startEditingAtPath (path);
        }
    }

    public void deleteSelected ()
    {
        if (container.locked) return;
        NodeBase selected = getSelected ();
        if (selected != null) selected.delete (tree, false);
    }

    public void moveSelected (int direction)
    {
        if (container.locked) return;
        TreePath path = tree.getSelectionPath ();
        if (path == null) return;

        NodeBase nodeBefore = (NodeBase) path.getLastPathComponent ();
        NodeBase parent     = (NodeBase) nodeBefore.getParent ();
        if (parent instanceof NodePart)  // Only parts support $metadata.gui.order
        {
            // First check if we can move in the filtered (visible) list.
            int indexBefore = model.getIndexOfChild (parent, nodeBefore);
            int indexAfter  = indexBefore + direction;
            if (indexAfter >= 0  &&  indexAfter < model.getChildCount (parent))
            {
                // Then convert to unfiltered indices.
                NodeBase nodeAfter = (NodeBase) model.getChild (parent, indexAfter);
                indexBefore = parent.getIndex (nodeBefore);
                indexAfter  = parent.getIndex (nodeAfter);
                PanelModel.instance.undoManager.add (new ChangeOrder ((NodePart) parent, indexBefore, indexAfter));
            }
        }
    }

    public void updateVisibility (TreeNode path[])
    {
        if (path.length < 2)
        {
            updateVisibility (path, -1);
        }
        else
        {
            NodeBase c = (NodeBase) path[path.length - 1];
            NodeBase p = (NodeBase) path[path.length - 2];
            int index = p.getIndexFiltered (c);
            updateVisibility (path, index);
        }
    }

    public void updateVisibility (TreeNode path[], int index)
    {
        updateVisibility (path, index, true);
    }

    /**
        Ensure that the tree down to the changed node is displayed with correct visibility and override coloring.
        @param path Every node from root to changed node, including changed node itself.
        The trailing nodes are allowed to be disconnected from root in the filtered view of the model,
        and they are allowed to be deleted nodes. Note: deleted nodes will have null parents.
        Deleted nodes should already be removed from tree by the caller, with proper notification.
        @param index Position of the last node in its parent node. Only used if the last node has been deleted.
        A value less than 0 causes selection to shift up to the parent.
    **/
    public void updateVisibility (TreeNode path[], int index, boolean setSelection)
    {
        // Prepare list of indices for final selection
        int[] selectionIndices = new int[path.length];
        for (int i = 1; i < path.length; i++)
        {
            NodeBase p = (NodeBase) path[i-1];
            NodeBase c = (NodeBase) path[i];
            selectionIndices[i] = model.getIndexOfChild (p, c);  // Could be -1, if c has already been deleted.
        }

        // Adjust visibility
        int inserted = path.length;
        int removed  = path.length;
        int removedIndex = -1;
        for (int i = path.length - 1; i > 0; i--)
        {
            NodeBase p = (NodeBase) path[i-1];
            NodeBase c = (NodeBase) path[i];
            if (c.getParent () == null) continue;  // skip deleted nodes
            int filteredIndex = model.getIndexOfChild (p, c);
            boolean filteredOut = filteredIndex < 0;
            if (c.visible (model.filterLevel))
            {
                if (filteredOut)
                {
                    p.unhide (c, model, false);  // silently adjust the filtering
                    inserted = i; // promise to notify model
                }
            }
            else
            {
                if (! filteredOut)
                {
                    p.hide (c, model, false);
                    removed = i;
                    removedIndex = filteredIndex;
                }
            }
        }

        // update color to indicate override state
        int lastChange = Math.min (inserted, removed);
        for (int i = 1; i < lastChange; i++)
        {
            // Since it is hard to measure current color, just assume everything needs updating.
            NodeBase c = (NodeBase) path[i];
            if (c.getParent () == null) continue;
            model.nodeChanged (c);
            Rectangle bounds = tree.getPathBounds (new TreePath (c.getPath ()));
            if (bounds != null) tree.paintImmediately (bounds);
        }

        if (lastChange < path.length)
        {
            NodeBase p = (NodeBase) path[lastChange-1];
            NodeBase c = (NodeBase) path[lastChange];
            int[] childIndices = new int[1];
            if (inserted < removed)
            {
                childIndices[0] = p.getIndexFiltered (c);
                model.nodesWereInserted (p, childIndices);
            }
            else
            {
                childIndices[0] = removedIndex;
                Object[] childObjects = new Object[1];
                childObjects[0] = c;
                model.nodesWereRemoved (p, childIndices, childObjects);
            }
            repaintSouth (new TreePath (p.getPath ()));
        }

        // select last visible node
        int i = 1;
        for (; i < path.length; i++)
        {
            NodeBase c = (NodeBase) path[i];
            if (c.getParent () == null) break;
            if (! c.visible (model.filterLevel)) break;
        }
        i--;  // Choose the last good node
        NodeBase c = (NodeBase) path[i];
        if (i == path.length - 2)
        {
            index = Math.min (index, model.getChildCount (c) - 1);
            if (index >= 0) c = (NodeBase) model.getChild (c, index);
        }
        else if (i < path.length - 2)
        {
            int childIndex = Math.min (selectionIndices[i+1], model.getChildCount (c) - 1);
            if (childIndex >= 0) c = (NodeBase) model.getChild (c, childIndex);
        }
        TreePath selectedPath = new TreePath (c.getPath ());
        if (setSelection)
        {
            tree.scrollPathToVisible (selectedPath);
            tree.setSelectionPath (selectedPath);
        }
        if (lastChange >= path.length)
        {
            boolean expanded = tree.isExpanded (selectedPath);
            model.nodeStructureChanged (c);  // Should this be more targeted?
            if (expanded) tree.expandPath (selectedPath);
            repaintSouth (selectedPath);
        }
    }

    /**
        Records the current order of nodes in "gui.order", provided that metadata field exists.
        Otherwise, we assume the user doesn't care.
        @param path To the node that changed (added, deleted, moved). In general, this node's
        parent will be the part that is tracking the order of its children.
    **/
    public void updateOrder (TreeNode path[])
    {
        NodePart parent = null;
        for (int i = path.length - 2; i >= 0; i--)
        {
            if (path[i] instanceof NodePart)
            {
                parent = (NodePart) path[i];
                break;
            }
        }
        if (parent == null) return;  // This should never happen, because root of tree is a NodePart.

        // Find $metadata/gui.order for the currently selected node. If it exists, update it.
        // Note that this is a modified version of moveSelected() which does not actually move
        // anything, and which only modifies an existing $metadata/gui.order, not create a new one.
        NodeAnnotations metadataNode = null;
        String order = null;
        Enumeration<?> i = parent.children ();
        while (i.hasMoreElements ())
        {
            NodeBase c = (NodeBase) i.nextElement ();
            String key = c.source.key ();
            if (order == null) order = key;
            else               order = order + "," + key;
            if (key.equals ("$metadata")) metadataNode = (NodeAnnotations) c;
        }
        if (metadataNode == null) return;

        NodeBase a = AddAnnotation.resolve (metadataNode, "gui.order");
        if (a != metadataNode)
        {
            MNode m = ((NodeAnnotation) a).folded;
            if (m.key ().equals ("order")  &&  m.parent ().key ().equals ("gui"))  // This check is necessary to avoid overwriting a pre-existing node folded under "gui" (for example, gui.bounds).
            {
                m.set (order);  // Shouldn't require change to tab stops, which should already be set.
                NodeBase ap = (NodeBase) a.getParent ();
                FontMetrics fm = a.getFontMetrics (tree);
                ap.updateTabStops (fm);  // Cause node to update it's text.
                model.nodeChanged (a);
            }
        }
    }

    public void repaintSouth (TreePath path)
    {
        Rectangle node    = tree.getPathBounds (path);
        Rectangle visible = getViewport ().getViewRect ();
        if (! needsFullRepaint  &&  node != null)
        {
            visible.height -= node.y - visible.y;
            visible.y       = node.y;
        }
        needsFullRepaint = false;
        tree.paintImmediately (visible);
    }
}
