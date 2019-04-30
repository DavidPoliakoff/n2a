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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.ViewportLayout;
import javax.swing.event.MouseInputAdapter;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.ui.eq.GraphEdge.Vector2;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.n2a.ui.eq.undo.ChangeVariable;

@SuppressWarnings("serial")
public class PanelEquationGraph extends JScrollPane
{
    protected PanelEquations   container;
    protected GraphPanel       graphPanel;
    protected Map<MNode,Point> focusCache = new HashMap<MNode,Point> ();
    public    NodePart         part;  // The node that contains the current graph. Can be container.root or a deeper node (via drill-down).

    // Convenience references
    protected JViewport  vp;
    protected JScrollBar hsb;
    protected JScrollBar vsb;

    protected static Color background = new Color (0xF0F0F0);  // light gray

    public PanelEquationGraph (PanelEquations container)
    {
        this.container = container;
        graphPanel = new GraphPanel ();
        setViewportView (graphPanel);

        setTransferHandler (new GraphTransferHandler ());

        vp  = getViewport ();
        hsb = getHorizontalScrollBar ();
        vsb = getVerticalScrollBar ();

        hsb.addAdjustmentListener (new AdjustmentListener ()
        {
            public void adjustmentValueChanged (AdjustmentEvent e)
            {
                Point p = vp.getViewPosition ();
                int i = e.getValue ();
                if (i != p.x)
                {
                    p.x = i;
                    vp.setViewPosition (p);
                }
            }
        });

        vsb.addAdjustmentListener (new AdjustmentListener ()
        {
            public void adjustmentValueChanged (AdjustmentEvent e)
            {
                Point p = vp.getViewPosition ();
                int i = e.getValue ();
                if (i != p.y)
                {
                    p.y = i;
                    vp.setViewPosition (p);
                }
            }
        });

        addMouseWheelListener (new MouseWheelListener ()
        {
            public void mouseWheelMoved (MouseWheelEvent e)
            {
                if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL)
                {
                    // Should really get scaling from scrollbar, but since we configure it to do pixel increments,
                    // we can hard code the multiplier in terms of how many pixels the scroll wheel should move.
                    if (vsb.isVisible ()) vsb.setValue (vsb.getValue () + e.getUnitsToScroll () * 15);
                }
            }
        });

        addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent me)
            {
                if (me.getButton () == MouseEvent.BUTTON1  &&  me.getClickCount () == 2)
                {
                    // Drill up
                    // This code is duplicated in GraphMouseListener. We need to listen both at
                    // the panel and at the scroll pane, since the panel can sometimes not cover the whole area.
                    NodePart parent = (NodePart) part.getParent ();
                    if (parent == null) return;
                    load (parent);
                    container.panelEquationTree.scrollToVisible (parent);
                }
            }
        });
    }

    public void saveFocus ()
    {
        if (part == null) return;
        Point focus = vp.getViewPosition ();
        focus.x -= graphPanel.offset.x;
        focus.y -= graphPanel.offset.y;
        focusCache.put (part.source, focus);
    }

    public void load (NodePart part)
    {
        saveFocus ();
        this.part = part;
        graphPanel.clear ();
        graphPanel.load ();
        container.updateBreadcrumbs (part);
        paintImmediately ();
    }

    public void recordDeleted ()
    {
        part = null;
        graphPanel.clear ();
        paintImmediately ();
    }

    public void addPart (NodePart node)
    {
        if (node.getParent () == part) graphPanel.addPart (node);
    }

    public void removePart (NodePart node)
    {
        if (node.graph != null) graphPanel.removePart (node);
    }

    public void updatePart (NodePart node)
    {
        if (node.graph == null) return;

        GraphNode gn = node.graph;
        gn.label.setText (node.source.key ()); // JLabel invalidates everything up to scroll pane. However, JTextField would not, because it is an invalidate root.
        Rectangle old = gn.getBounds ();
        gn.setSize (gn.getPreferredSize ());  // GraphLayout won't do this, so we have to do it manually.
        Rectangle next = gn.getBounds ();
        graphPanel.layout.componentMoved (gn);
        graphPanel.paintImmediately (old.union (next));
    }

    public void reconnect ()
    {
        graphPanel.rebuildEdges ();
    }

    public void paintImmediately ()
    {
        paintImmediately (getBounds ());
    }

    public void updateUI ()
    {
        GraphNode.RoundedBorder.updateUI ();
        background = UIManager.getColor ("ScrollPane.background");
    }

    public void doLayout ()
    {
        super.doLayout ();

        // Update scroll bars
        Point p          = vp.getViewPosition ();
        Dimension size   = vp.getViewSize ();
        Dimension extent = vp.getExtentSize ();
        if (size.width > extent.width)
        {
            hsb.setValues (p.x, extent.width, 0, size.width);
        }
        if (size.height > extent.height)
        {
            vsb.setValues (p.y, extent.height, 0, size.height);
        }
    }

    public JViewport createViewport ()
    {
        return new JViewport ()
        {
            public LayoutManager createLayoutManager ()
            {
                return new ViewportLayout ()
                {
                    public void layoutContainer(Container parent)
                    {
                        // The original version of this code (in OpenJDK) justifies (moves) the view if it is smaller
                        // than the viewport extent. We don't want to move the viewport, so simply set its preferred size.
                        // The original code also expands the view to fill the viewport. Presumably this is to
                        // ensure everything gets painted. However, the display seems to work fine without doing that,
                        // and the simpler approach seems to produce more reliable behavior.
                        vp.setViewSize (graphPanel.getPreferredSize ());
                    }
                };
            }
        };
    }

    public class GraphPanel extends JPanel
    {
        protected GraphLayout     layout;  // For ease of access, to avoid calling getLayout() all the time.
        protected List<GraphEdge> edges  = new ArrayList<GraphEdge> (); // Note that GraphNodes are stored directly as Swing components.
        protected Point           offset = new Point ();  // Offset from persistent coordinates to pixels. Add this to a stored (x,y) value to get non-negative coordinates that can be painted.

        public GraphPanel ()
        {
            super (new GraphLayout ());
            layout = (GraphLayout) getLayout ();

            MouseAdapter mouseListener = new GraphMouseListener ();
            addMouseListener (mouseListener);
            addMouseMotionListener (mouseListener);
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
                gn.node.graph = null;
            }

            // Flush all data
            removeAll ();
            edges.clear ();
            layout.bounds = new Rectangle ();
            offset = new Point ();
            vp.setViewPosition (new Point ());
            hsb.setValue (0);
            vsb.setValue (0);
        }

        public void load ()
        {
            Enumeration<?> children = part.children ();
            boolean newLayout = children.hasMoreElements ();
            while (children.hasMoreElements ())
            {
                Object c = children.nextElement ();
                if (c instanceof NodePart)
                {
                    GraphNode gn = new GraphNode (this, (NodePart) c);
                    add (gn);
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
                    if (! container.locked)
                    {
                        MNode bounds = ((GraphNode) c).node.source.childOrCreate ("$metadata", "gui", "bounds");
                        bounds.set (x, "x");  // offset should be zero for new load, so don't worry about adding it
                        bounds.set (y, "y");
                    }
                    x += c.getWidth () + gap;
                }

                // TODO: the equation tree should be rebuilt, so that new metadata nodes become visible.
            }

            buildEdges ();

            validate ();  // Runs layout, so negative focus locations can work, or so that origin (0,0) is meaningful.
            Point focus = focusCache.get (part.source);
            if (focus == null)
            {
                focus = new Point ();  // (0,0)
            }
            else
            {
                focus.x += graphPanel.offset.x;
                focus.y += graphPanel.offset.y;
                focus.x = Math.max (0, focus.x);
                focus.y = Math.max (0, focus.y);
                Dimension extent = vp.getExtentSize ();
                focus.x = Math.min (focus.x, Math.max (0, layout.bounds.width  - extent.width));
                focus.y = Math.min (focus.y, Math.max (0, layout.bounds.height - extent.height));
            }
            vp.setViewPosition (focus);
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
            remove (node.graph);
            node.graph = null;
            revalidate ();
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
            return container.panelEquationTree.transferHandler.canImport (xfer);
        }

        public boolean importData (TransferSupport xfer)
        {
            return container.panelEquationTree.transferHandler.importData (xfer);
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
            if (me.getButton () == MouseEvent.BUTTON1  &&  me.getClickCount () == 2)
            {
                // Drill up
                NodePart parent = (NodePart) part.getParent ();
                if (parent == null) return;
                load (parent);
                container.panelEquationTree.scrollToVisible (parent);
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
            switch (me.getButton ())
            {
                case MouseEvent.BUTTON1:
                    if (container.locked) return;
                    Point p = me.getPoint ();
                    edge = graphPanel.findTipAt (p);
                    if (edge != null)
                    {
                        setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
                        edge.animate (p);
                    }
                    break;
                case MouseEvent.BUTTON2:
                    startPan = me.getPoint ();
                    setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
                    break;
            }
        }

        public void mouseDragged (MouseEvent me)
        {
            Point pp = vp.getLocationOnScreen ();
            Point pm = me.getLocationOnScreen ();
            pm.x -= pp.x;
            pm.y -= pp.y;
            Dimension extent = vp.getExtentSize ();
            boolean auto =  me == lastEvent;
            if (pm.x < 0  ||  pm.x > extent.width  ||  pm.y < 0  ||  pm.y > extent.height)  // out of bounds
            {
                if (edge == null) return;
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

            Point here = me.getPoint ();
            if (edge != null)
            {
                edge.animate (here);
            }
            else if (startPan != null)
            {
                int dx = here.x - startPan.x;
                int dy = here.y - startPan.y;
                if (dx != 0  &&  hsb.isVisible ())
                {
                    int old = hsb.getValue ();
                    hsb.setValue (old - dx);
                }
                if (dy != 0  &&  vsb.isVisible ())
                {
                    int old = vsb.getValue ();
                    vsb.setValue (old - dy);
                }
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
                    MPart mchild = variable.source;
                    if (mchild.isOverridden ())
                    {
                        String original = mchild.getOriginal ().get ();
                        if (Operator.containsConnect (original)) value = original;
                    }
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