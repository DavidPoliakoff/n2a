/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.ViewportLayout;
import javax.swing.event.MouseInputAdapter;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.ui.eq.GraphEdge.Vector2;
import gov.sandia.n2a.ui.eq.PanelEquations.FocusCacheEntry;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.n2a.ui.eq.undo.ChangeVariable;

@SuppressWarnings("serial")
public class PanelEquationGraph extends JScrollPane
{
    protected PanelEquations container;
    protected GraphPanel     graphPanel;

    // Convenience references
    protected JViewport  vp;
    protected JScrollBar vsb;

    protected static Color background = new Color (0xF0F0F0);  // light gray

    public PanelEquationGraph (PanelEquations container)
    {
        this.container = container;
        graphPanel = new GraphPanel ();
        setViewportView (graphPanel);
        setBorder (BorderFactory.createLineBorder (Color.black));

        setTransferHandler (new GraphTransferHandler ());

        vp  = getViewport ();
        vsb = getVerticalScrollBar ();
    }

    public void loadPart ()
    {
        graphPanel.clear ();
        graphPanel.load ();
    }

    public void clear ()
    {
        container.active = null;  // on the presumption that container.panelEquationGraph was most recently on display. This function is only called in that case.
        graphPanel.clear ();
    }

    public void updateLock ()
    {
        graphPanel.updateLock ();
    }

    public Point saveFocus ()
    {
        Point focus = vp.getViewPosition ();
        focus.x -= graphPanel.offset.x;
        focus.y -= graphPanel.offset.y;
        return focus;
    }

    public void takeFocus (FocusCacheEntry fce)
    {
        Point focus = new Point ();  // (0,0)
        GraphNode gn = null;
        if (fce != null)
        {
            if (fce.position != null)
            {
                focus = new Point (fce.position);
                focus.x += graphPanel.offset.x;
                focus.y += graphPanel.offset.y;
                focus.x = Math.max (0, focus.x);
                focus.y = Math.max (0, focus.y);
                Dimension extent = vp.getExtentSize ();
                focus.x = Math.min (focus.x, Math.max (0, graphPanel.layout.bounds.width  - extent.width));
                focus.y = Math.min (focus.y, Math.max (0, graphPanel.layout.bounds.height - extent.height));
            }
            if (fce.subpart != null)
            {
                gn = graphPanel.findNode (fce.subpart);
            }
        }

        // Restore the viewport position, but only if the viewport has changed content.
        // As a heuristic to detect whether content has changed, we assume that focus has not yet
        // shifted to any tree in the graph. But if focus is already on one of our trees, then
        // the previous view was the same as this one.
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager ().getFocusOwner ();
        if (! isAncestorOf (focusOwner)) vp.setViewPosition (focus);

        if (gn == null  &&  graphPanel.getComponentCount () > 0)
        {
            Component c = PanelModel.instance.getFocusTraversalPolicy ().getFirstComponent (graphPanel);
            c = c.getParent ();
            if (! (c instanceof GraphNode)) c = c.getParent ();
            if (   c instanceof GraphNode ) gn = (GraphNode) c;
        }
        if (gn != null)
        {
            gn.takeFocus ();  // possibly change the viewport position set above
        }
    }

    public void updateFilterLevel ()
    {
        graphPanel.updateFilterLevel ();
    }

    public void addPart (NodePart node)
    {
        if (node.getParent () == container.part) graphPanel.addPart (node);
    }

    public void removePart (NodePart node)
    {
        if (node.graph == null) return;
        PanelEquationTree tree = node.getTree ();
        if (container.active == tree) container.active = null;  // In case this graph panel loses focus completely.

        GraphNode nextGraph = null;
        PanelModel pm = PanelModel.instance;
        FocusTraversalPolicy ftp = pm.getFocusTraversalPolicy ();
        Component c = ftp.getComponentAfter (pm, node.graph.title);
        while (c != null  &&  ! (c instanceof GraphNode)) c = c.getParent ();
        if (c == null)  // next focus is not in a graph node, so outside of this equation graph
        {
            c = ftp.getComponentBefore (pm, node.graph.title);
            while (c != null  &&  ! (c instanceof GraphNode)) c = c.getParent ();
            if (c != null) nextGraph = (GraphNode) c;
        }

        // If another node exists in this graph, one of the following lines will shift focus to it.
        // Otherwise, focus will follow the cycle out of this graph, probably arriving in the search box on the left. 
        if (nextGraph != null) nextGraph.takeFocus ();  // Only if we need to override the default focus cycle.
        graphPanel.removePart (node);  // If node still has focus, then default focus cycle applies.
    }

    public void deleteSelected ()
    {
        // TODO
    }

    /**
        Called by ChangePart to apply name change to an existing graph node.
        Note that underride implies several other cases besides simple name change.
        Those cases are handled by addPart() and removePart().
    **/
    public void updatePart (NodePart node)
    {
        if (node.graph != null) node.graph.updateTitle ();
    }

    public void reconnect ()
    {
        graphPanel.rebuildEdges ();
    }

    public boolean isEmpty ()
    {
        return graphPanel.getComponentCount () == 0;
    }

    public void updateUI ()
    {
        super.updateUI ();
        GraphNode.RoundedBorder.updateUI ();
        background = UIManager.getColor ("ScrollPane.background");
    }

    public JViewport createViewport ()
    {
        return new JViewport ()
        {
            public LayoutManager createLayoutManager ()
            {
                return new ViewportLayout ()
                {
                    public void layoutContainer (Container parent)
                    {
                        // The original version of this code in OpenJDK moves the view if it is smaller than the viewport extent.
                        // Moving the viewport would disrupt user interaction, so we only set its size.

                        Dimension size   = graphPanel.getPreferredSize ();
                        Point     p      = vp.getViewPosition ();
                        Dimension extent = vp.getExtentSize ();
                        int visibleWidth  = size.width  - p.x;
                        int visibleHeight = size.height - p.y;
                        size.width  += Math.max (0, extent.width  - visibleWidth);
                        size.height += Math.max (0, extent.height - visibleHeight);
                        vp.setViewSize (size);
                    }
                };
            }
        };
    }

    public int getHorizontalScrollBarPolicy ()
    {
        if (vp != null)
        {
            Point p = vp.getViewPosition ();
            if (p.x > 0) return HORIZONTAL_SCROLLBAR_ALWAYS;
        }
        return HORIZONTAL_SCROLLBAR_AS_NEEDED;
    }

    public int getVerticalScrollBarPolicy ()
    {
        if (vp != null)
        {
            Point p = vp.getViewPosition ();
            if (p.y > 0) return VERTICAL_SCROLLBAR_ALWAYS;
        }
        return VERTICAL_SCROLLBAR_AS_NEEDED;
    }

    public class GraphPanel extends JPanel
    {
        protected GraphLayout     layout;  // For ease of access, to avoid calling getLayout() all the time.
        protected List<GraphEdge> edges    = new ArrayList<GraphEdge> (); // Note that GraphNodes are stored directly as Swing components.
        public    Point           offset   = new Point ();  // Offset from persistent coordinates to viewport coordinates. Add this to a stored (x,y) value to get non-negative coordinates that can be painted.
        protected List<GraphNode> selected = new ArrayList<GraphNode> ();  // TODO: implement selection, with operations: move, resize, delete

        public GraphPanel ()
        {
            super (new GraphLayout ());
            layout = (GraphLayout) getLayout ();

            MouseAdapter mouseListener = new GraphMouseListener ();
            addMouseListener (mouseListener);
            addMouseMotionListener (mouseListener);
            addMouseWheelListener (mouseListener);
        }

        public boolean isOptimizedDrawingEnabled ()
        {
            // Because parts can overlap, we must return false.
            return false;
        }

        public void clear ()
        {
            // Disconnect graph nodes from tree nodes
            for (Component c : getComponents ())
            {
                GraphNode gn = (GraphNode) c;
                gn.panelEquations.clear ();
                gn.node.graph = null;
                gn.node.fakeRoot (false);
            }

            // Flush all data
            removeAll ();
            edges.clear ();
            layout.bounds = new Rectangle ();
            offset = new Point ();
            vp.setViewPosition (new Point ());
        }

        public void load ()
        {
            Enumeration<?> children = container.part.children ();
            boolean newLayout = children.hasMoreElements ();
            while (children.hasMoreElements ())
            {
                Object c = children.nextElement ();
                if (c instanceof NodePart)
                {
                    GraphNode gn = new GraphNode (this, (NodePart) c);
                    if (gn.open) add (gn, 0);  // Put open nodes at top of z order
                    else         add (gn);
                    if (gn.getX () != 0  ||  gn.getY () != 0) newLayout = false;
                }
            }

            if (newLayout)
            {
                // TODO: use potential-field method, such as "Drawing Graphs Nicely Using Simulated Annealing" by Davidson & Harel (1996).

                // For now, a very simple layout. Arrange in a grid with some space between nodes.
                Component[] components = getComponents ();
                int columns = (int) Math.sqrt (components.length);  // Truncate, so more rows than columns.
                int gap = 100;
                int x = 0;
                int y = 0;
                for (int i = 0; i < components.length; i++)
                {
                    Component c = components[i];
                    if (i % columns == 0)
                    {
                        x = gap;
                        y += gap;
                    }
                    c.setLocation (x, y);
                    layout.bounds = layout.bounds.union (c.getBounds ());
                    // Don't save bounds in metadata. Only touch part if user manually adjusts layout.
                    x += c.getWidth () + gap;
                }

                // TODO: the equation tree should be rebuilt, so that new metadata nodes become visible.
            }

            buildEdges ();
            validate ();  // Runs layout, so negative focus locations can work, or so that origin (0,0) is meaningful.
        }

        /**
            Scans children to set up connections.
            Assumes that all edge collections are empty.
        **/
        public void buildEdges ()
        {
            for (Component c : getComponents ())
            {
                GraphNode gn = (GraphNode) c;
                if (gn.node.connectionBindings == null) continue;

                for (Entry<String,NodePart> e : gn.node.connectionBindings.entrySet ())
                {
                    GraphNode endpoint = null;
                    NodePart np = e.getValue ();
                    if (np != null) endpoint = np.graph;

                    GraphEdge ge = new GraphEdge (gn, endpoint, e.getKey ());
                    edges.add (ge);
                    gn.edgesOut.add (ge);
                    if (endpoint != null) endpoint.edgesIn.add (ge);
                }
                if (gn.edgesOut.size () == 2)
                {
                    GraphEdge A = gn.edgesOut.get (0);  // Not necessarily same as endpoint variable named "A" in part.
                    GraphEdge B = gn.edgesOut.get (1);
                    A.edgeOther = B;
                    B.edgeOther = A;
                }
                for (GraphEdge ge : gn.edgesOut)
                {
                    ge.updateShape (false);
                    if (ge.bounds != null) layout.bounds = layout.bounds.union (ge.bounds);
                }
            }
            revalidate ();
        }

        public void rebuildEdges ()
        {
            for (Component c : getComponents ())
            {
                GraphNode gn = (GraphNode) c;
                gn.edgesIn.clear ();
                gn.edgesOut.clear ();
            }
            edges.clear ();
            buildEdges ();
        }

        /**
            Add a node to an existing graph.
            Must always be followed by a call to rebuildEdges() to update connections.
            These functions are separated to simplify code in undo objects.
        **/
        public void addPart (NodePart node)
        {
            GraphNode gn = new GraphNode (this, node);
            add (gn, 0);  // put at top of z-order, so user can find it easily
            if (gn.getX () == 0  &&  gn.getY () == 0)  // Need layout
            {
                // Just put it in the center of the viewport
                Point     location = vp.getViewPosition ();
                Dimension extent   = vp.getExtentSize ();
                Dimension size     = gn.getSize ();
                location.x += (extent.width  - size.width)  / 2;
                location.y += (extent.height - size.height) / 2;
                location.x = Math.max (0, location.x);
                location.y = Math.max (0, location.y);
                gn.setLocation (location);

                // TODO: Instead of saving location, do a fresh auto-layout of all parts that don't have fixed bounds.
                // This will allow a user to build a graph entirely with auto-layout, without ever setting any bounds.
                // That will allow parts that don't otherwise need to be overridden to remain untouched.
                MNode bounds = gn.node.source.childOrCreate ("$metadata", "gui", "bounds");
                bounds.set (location.x - offset.x, "x");
                bounds.set (location.y - offset.y, "y");
            }
            layout.bounds = layout.bounds.union (gn.getBounds ());
            revalidate ();
        }

        /**
            Remove node from an existing graph.
            Must always be followed by a call to rebuildEdges() to update connections.
            These functions are separated to simplify code in undo objects.
        **/
        public void removePart (NodePart node)
        {
            Rectangle bounds = node.graph.getBounds ();
            remove (node.graph);
            node.graph = null;
            revalidate ();
            repaint (bounds);
        }

        public void updateLock ()
        {
            for (Component c : getComponents ())
            {
                ((GraphNode) c).panelEquations.updateLock ();
            }
        }

        public void updateFilterLevel ()
        {
            for (Component c : getComponents ())
            {
                ((GraphNode) c).panelEquations.updateFilterLevel ();
            }
        }

        public GraphEdge findTipAt (Point p)
        {
            Vector2 p2 = new Vector2 (p.x, p.y);
            for (GraphEdge e : edges)
            {
                if (e.tip != null  &&  e.tip.distance (p2) < GraphEdge.arrowheadLength) return e;
            }
            return null;
        }

        public GraphNode findNodeAt (Point p)
        {
            for (Component c : getComponents ())
            {
                // p is relative to the container, whereas Component.contains() is relative to the component itself.
                if (c.contains (p.x - c.getX (), p.y - c.getY ())) return (GraphNode) c;
            }
            return null;
        }

        public GraphNode findNode (String name)
        {
            for (Component c : getComponents ())
            {
                GraphNode gn = (GraphNode) c;
                if (gn.node.source.key ().equals (name)) return gn;
            }
            return null;
        }

        public void paintComponent (Graphics g)
        {
            // This basically does nothing, since ui is (usually) null. Despite being opaque, our background comes from our container.
            super.paintComponent (g);

            // Fill background
            Graphics2D g2 = (Graphics2D) g.create ();
            g2.setColor (background);
            Rectangle clip = g2.getClipBounds ();
            g2.fillRect (clip.x, clip.y, clip.width, clip.height);

            // Draw connection edges
            g2.setStroke (new BasicStroke (GraphEdge.strokeThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (GraphEdge e : edges)
            {
                if (e.bounds.intersects (clip)) e.paintComponent (g2);
            }

            g2.dispose ();
        }
    }

    public class GraphLayout implements LayoutManager2
    {
        public Rectangle bounds = new Rectangle ();

        public void addLayoutComponent (String name, Component comp)
        {
            addLayoutComponent (comp, name);
        }

        public void addLayoutComponent (Component comp, Object constraints)
        {
            Dimension d = comp.getPreferredSize ();
            comp.setSize (d);
            Point p = comp.getLocation ();
            bounds = bounds.union (new Rectangle (p, d));
        }

        public void removeLayoutComponent (Component comp)
        {
        }

        public Dimension preferredLayoutSize (Container target)
        {
            return bounds.getSize ();
        }

        public Dimension minimumLayoutSize (Container target)
        {
            return preferredLayoutSize (target);
        }

        public Dimension maximumLayoutSize (Container target)
        {
            return preferredLayoutSize (target);
        }

        public float getLayoutAlignmentX (Container target)
        {
            return 0;
        }

        public float getLayoutAlignmentY (Container target)
        {
            return 0;
        }

        public void invalidateLayout (Container target)
        {
        }

        public void layoutContainer (Container target)
        {
            // TODO: shiftViewport() does not work well when dragging edge endpoints. Work on collapsing these two functions.

            // Only change layout if a component has moved into negative space.
            if (bounds.x >= 0  &&  bounds.y >= 0) return;

            GraphPanel gp = (GraphPanel) target;
            JViewport  vp = (JViewport) gp.getParent ();
            int dx = Math.max (-bounds.x, 0);
            int dy = Math.max (-bounds.y, 0);
            bounds.translate (dx, dy);
            gp.offset.translate (dx, dy);
            Point p = vp.getViewPosition ();
            p.translate (dx, dy);
            vp.setViewPosition (p);

            // None of the following code is allowed to call componentMoved().
            for (Component c : target.getComponents ())
            {
                p = c.getLocation ();
                p.translate (dx, dy);
                c.setLocation (p);
            }
            for (GraphEdge ge : gp.edges)
            {
                ge.updateShape (false);
            }
        }

        /**
            Moves viewport so that the given point is in the upper-left corner of the visible area.
            Does a tight fit around existing components to minimize size of scrolled region.
            This could result in a shift of components, even if the requested position is same as current position.
            @param n New position of viewport, in terms of current viewport layout.
            @return Amount to shift any external components to keep position relative to internal components.
            This value should be added to their coordinates.
        **/
        public Point shiftViewport (Point n)
        {
            // Compute tight bounds which include new position
            bounds = new Rectangle (n.x, n.y, 1, 1);
            for (Component c : graphPanel.getComponents ())
            {
                bounds = bounds.union (c.getBounds ());
            }
            for (GraphEdge ge : graphPanel.edges)
            {
                bounds = bounds.union (ge.bounds);
            }

            // Shift components so bounds start at origin
            Point d = new Point (-bounds.x, -bounds.y);
            if (d.x != 0  ||  d.y != 0)
            {
                bounds.translate (d.x, d.y);
                graphPanel.offset.translate (d.x, d.y);
                n.translate (d.x, d.y);

                // None of the following code is allowed to call componentMoved().
                for (Component c : graphPanel.getComponents ())
                {
                    Point p = c.getLocation ();
                    p.translate (d.x, d.y);
                    c.setLocation (p);
                }
                for (GraphEdge ge : graphPanel.edges)
                {
                    ge.updateShape (false);
                }
            }

            vp.setViewPosition (n);  // Doesn't do anything if n is same as current position.
            return d;
        }

        public void componentMoved (Component comp)
        {
            componentMoved (comp.getBounds ());
        }

        public void componentMoved (Rectangle next)
        {
            Rectangle old = bounds;
            bounds = bounds.union (next);
            if (! bounds.equals (old)) graphPanel.revalidate ();
        }
    }

    public class GraphTransferHandler extends TransferHandler
    {
        public boolean canImport (TransferSupport xfer)
        {
            return container.transferHandler.canImport (xfer);
        }

        public boolean importData (TransferSupport xfer)
        {
            return container.transferHandler.importData (xfer);
        }

        public void exportDone (JComponent source, Transferable data, int action)
        {
            PanelModel.instance.undoManager.endCompoundEdit ();
        }
    }

    public class GraphMouseListener extends MouseInputAdapter implements ActionListener
    {
        Point      startPan = null;
        GraphEdge  edge     = null;
        MouseEvent lastEvent;
        Timer      timer    = new Timer (100, this);

        public void mouseClicked (MouseEvent me)
        {
            if (SwingUtilities.isLeftMouseButton (me)  &&  me.getClickCount () == 2)
            {
                container.drillUp ();
            }
        }

        public void mouseWheelMoved (MouseWheelEvent e)
        {
            if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL)
            {
                // Should really get scaling from scrollbar, but since we configure it to do pixel increments,
                // we can hard code the multiplier in terms of how many pixels the scroll wheel should move.
                if (vsb.isVisible ()) vsb.setValue (vsb.getValue () + e.getUnitsToScroll () * 15);
            }
        }

        public void mouseMoved (MouseEvent me)
        {
            if (container.locked) return;
            GraphEdge e = graphPanel.findTipAt (me.getPoint ());
            if (e == null) setCursor (Cursor.getDefaultCursor ());
            else           setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
        }

        public void mousePressed (MouseEvent me)
        {
            if (SwingUtilities.isLeftMouseButton (me))
            {
                if (container.locked) return;
                Point p = me.getPoint ();
                edge = graphPanel.findTipAt (p);
                if (edge != null)
                {
                    setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
                    edge.animate (p);
                }
            }
            else if (SwingUtilities.isMiddleMouseButton (me))
            {
                startPan = me.getPoint ();
                setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
            }
        }

        public void mouseDragged (MouseEvent me)
        {
            Point here = me.getPoint ();  // relative to origin of viewport, which may not be visible

            Point pp = vp.getLocationOnScreen ();
            Point pm = me.getLocationOnScreen ();
            pm.x -= pp.x;  // relative to upper-left corner of visible region
            pm.y -= pp.y;
            Dimension extent = vp.getExtentSize ();

            if (edge != null)
            {
                boolean auto =  me == lastEvent;
                if (pm.x < 0  ||  pm.x > extent.width  ||  pm.y < 0  ||  pm.y > extent.height)  // out of bounds
                {
                    if (auto)
                    {
                        int dx = pm.x < 0 ? pm.x : (pm.x > extent.width  ? pm.x - extent.width  : 0);
                        int dy = pm.y < 0 ? pm.y : (pm.y > extent.height ? pm.y - extent.height : 0);

                        me.translatePoint (dx, dy);  // Makes permanent change to lastEvent
                        Point p = vp.getViewPosition ();
                        p.translate (dx, dy);
                        vp.setViewPosition (p);
                    }
                    else  // A regular drag
                    {
                        lastEvent = me;  // Let the user adjust speed.
                        timer.start ();
                        return;  // Don't otherwise process it.
                    }
                }
                else  // in bounds
                {
                    timer.stop ();
                    lastEvent = null;
                    if (auto) return;
                }

                edge.animate (here);
            }
            else if (startPan != null)
            {
                Point p = vp.getViewPosition ();  // should be exactly same as current scrollbar values
                p.x -= here.x - startPan.x;
                p.y -= here.y - startPan.y;
                Point d = graphPanel.layout.shiftViewport (p);
                startPan.x += d.x;
                startPan.y += d.y;
                graphPanel.revalidate ();  // necessary to get scrollbars when components go past right or bottom
                graphPanel.repaint ();
            }
        }

        public void mouseReleased (MouseEvent me)
        {
            startPan = null;
            lastEvent = null;
            timer.stop ();
            setCursor (Cursor.getPredefinedCursor (Cursor.DEFAULT_CURSOR));

            if (edge != null)  // Finish assigning endpoint
            {
                edge.tipDrag = false;

                PanelModel mep = PanelModel.instance;
                GraphNode nodeFrom = edge.nodeFrom;
                NodePart partFrom = nodeFrom.node;
                NodeVariable variable = (NodeVariable) partFrom.child (edge.alias);  // There should always be a variable with the alias as its name.

                GraphNode nodeTo = graphPanel.findNodeAt (me.getPoint ());
                if (nodeTo == null  ||  nodeTo == nodeFrom)  // Disconnect the edge
                {
                    String value = "connect()";
                    String original = variable.source.getOriginal ().get ();
                    if (Operator.containsConnect (original)) value = original;
                    mep.undoManager.add (new ChangeVariable (variable, edge.alias, value));
                }
                else if (nodeTo == edge.nodeTo)  // No change
                {
                    edge.animate (null);
                }
                else  // Connect to new endpoint
                {
                    mep.undoManager.add (new ChangeVariable (variable, edge.alias, nodeTo.node.source.key ()));
                }
                edge = null;
            }
        }

        public void actionPerformed (ActionEvent e)
        {
            mouseDragged (lastEvent);
        }
    }
}
