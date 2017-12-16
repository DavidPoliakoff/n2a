/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.MatrixDense;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.plugins.extpoints.Backend;

/**
    An Instance which contains the global variables for a given kind of part,
    and which manages the group of instances as a whole.
**/
public class Population extends Instance
{
    public int n;  // current number of live members

    protected Population (EquationSet equations, Part container)
    {
        this.equations = equations;
        this.container = container;
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        allocate (bed.countGlobalFloat, bed.countGlobalObject);
    }

    /// @return The Population associated with the given position in EquationSet.connectionBindings collection
    public Population getTarget (int i)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        return (Population) ((Part) container).valuesObject[bed.connectionTargets[i]];
    }

    public void init (Simulator simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true);
        resolve (temp.bed.globalReference);
        for (Variable v : temp.bed.globalInit)
        {
            Type result = v.eval (temp);
            if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
        }
        for (Variable v : temp.bed.globalBuffered)
        {
            temp.setFinal (v, temp.getFinal (v));
        }
        // zero external buffered variables that may be written before first finish()
        for (Variable v : temp.bed.globalBufferedExternalWrite) set (v, v.type);  // v.type should be pre-loaded with zero-equivalent values

        if (temp.bed.index != null)
        {
            valuesFloat[temp.bed.indexNext] = 0;  // Using floats directly as index counter limits us to 24 bits, or about 16 million. Internal is not intended for large simulations, so this limitation is acceptable.
            // indexAvailable is initially null
        }

        // TODO: A self-connection will have to do both resize() and connect().
        // It's just coincidental that these are mutually exclusive in the current code.
        if (equations.connectionBindings == null)
        {
            InternalBackendData bed = (InternalBackendData) equations.backendData;
            int requestedN = 1;
            if (bed.n.hasAttribute ("constant")) requestedN = (int) ((Scalar) bed.n.eval (this)).value;
            else                                 requestedN = (int) ((Scalar) get (bed.n)).value;
            resize (simulator, requestedN);
        }
        else
        {
            simulator.connect (this);  // queue to evaluate our connections
        }
    }

    public void integrate (Simulator simulator, double dt)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        for (Variable v : bed.globalIntegrated)
        {
            double a  = ((Scalar) get (v           )).value;
            double aa = ((Scalar) get (v.derivative)).value;
            setFinal (v, new Scalar (a + aa * dt));
        }
    }

    public void update (Simulator simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, false);
        for (Variable v : temp.bed.globalUpdate)
        {
            Type result = v.eval (temp);
            if (result == null)  // no condition matched
            {
                if (v.reference.variable == v  &&  v.equations.size () > 0  &&  v.readIndex != v.writeIndex) temp.set (v, temp.get (v));
            }
            else if (v.reference.variable.writeIndex >= 0)  // ensure this is not a "dummy" variable
            {
                if (v.assignment == Variable.REPLACE)
                {
                    temp.set (v, result);
                }
                else
                {
                    // the rest of these require knowing the current value of the working result, which is most likely external buffered
                    Type current = temp.getFinal (v.reference);
                    switch (v.assignment)
                    {
                        case Variable.ADD:      temp.set (v, current.add      (result)); break;
                        case Variable.MULTIPLY: temp.set (v, current.multiply (result)); break;
                        case Variable.DIVIDE:   temp.set (v, current.divide   (result)); break;
                        case Variable.MIN:      temp.set (v, current.min      (result)); break;
                        case Variable.MAX:      temp.set (v, current.max      (result)); break;
                    }
                }
            }
        }
        for (Variable v : temp.bed.globalBufferedInternalUpdate)
        {
            temp.setFinal (v, temp.getFinal (v));
        }
    }

    public boolean finish (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        // $n shares control with other specials, so coordinate them.
        // This work is split between here and after the finalize code below.
        if (bed.populationCanResize  &&  bed.populationCanGrowOrDie  &&  bed.n.derivative == null)
        {
            double oldN = ((Scalar) get      (bed.n)).value;
            double newN = ((Scalar) getFinal (bed.n)).value;
            if (newN != oldN) simulator.resize (this, (int) newN);  // $n was explicitly changed, so its value takes precedence
            else              simulator.resize (this, -1);  // -1 means to update $n from this.n. This can only be done after other parts are finalized, as they may impose structural dynamics via $p or $type.
        }

        for (Variable v : bed.globalBufferedExternal) setFinal (v, getFinal (v));
        for (Variable v : bed.globalBufferedExternalWrite)
        {
            switch (v.assignment)
            {
                case Variable.ADD:
                    set (v, v.type);  // initial value is zero-equivalent (additive identity)
                    break;
                case Variable.MULTIPLY:
                case Variable.DIVIDE:
                    // multiplicative identity
                    if (v.type instanceof Matrix) set (v, ((Matrix) v.type).identity ());
                    else                          set (v, new Scalar (1));
                    break;
                case Variable.MIN:
                    if (v.type instanceof Matrix) set (v, ((Matrix) v.type).clear (Double.POSITIVE_INFINITY));
                    else                          set (v, new Scalar (Double.POSITIVE_INFINITY));
                    break;
                case Variable.MAX:
                    if (v.type instanceof Matrix) set (v, ((Matrix) v.type).clear (Double.NEGATIVE_INFINITY));
                    else                          set (v, new Scalar (Double.NEGATIVE_INFINITY));
                    break;
                // For all other assignment types, do nothing. Effectively, buffered value is initialized to current value
            }
        }

        if (bed.populationCanResize)
        {
            int requestedN = (int) ((Scalar) get (bed.n)).value;  // This is the finalized value of $n.
            if (bed.populationCanGrowOrDie)
            {
                if (bed.n.derivative != null)  // $n' exists
                {
                    // the rate of change in $n is pre-determined, so it relentlessly overrides any other structural dynamics
                    simulator.resize (this, requestedN);
                }
            }
            else  // $n is the only kind of structural dynamics, so only do a resize() when needed
            {
                if (requestedN != n) simulator.resize (this, requestedN);
            }
        }

        return true;
    }

    public class ConnectIterator
    {
        public int                 index;
        public boolean             newOnly;  // Filter out old instances. Only an iterator with index==0 can set this true.
        public ConnectIterator     permute;
        public boolean             contained;  // Another iterator holds us in its permute reference.
        public Population          population;
        public int                 max;
        public int                 connectedCount;  // position in p.valuesFloat of $count for this connection
        public InternalBackendData pbed;
        public int                 firstborn;
        public ArrayList<Part>     instances;
        public ArrayList<Part>     filtered;  // A subset of instances selected by spatial filtering.
        public Part                c;  // The connection instance being built.
        public Part                p;  // Our current part, contributed as an endpoint of c.
        public Simulator           simulator;  // For evaluating equations

        public int size;   // Cached value of instances.size(). Does not change.
        public int count;  // Size of current subset of instances we are iterating through.
        public int offset;
        public int i;
        public int stop;

        public KDTree              NN;
        public List<KDTree.Entry>  entries;
        public Variable            project;
        public double[]            xyz;  // query value, shared across all iterators
        public InternalBackendData cbed;
        public double              rank;  // heuristic value indicating how good a candidate this endpoint is to determine C.$xyz

        @SuppressWarnings("unchecked")
        public ConnectIterator (int index, Population population, InternalBackendData cbed, Simulator simulator)
        {
            this.index      = index;
            this.population = population;
            this.cbed       = cbed;
            this.simulator  = simulator;
            pbed            = (InternalBackendData) population.equations.backendData;
            firstborn       = (int)             population.valuesFloat [pbed.firstborn];
            instances       = (ArrayList<Part>) population.valuesObject[pbed.instances];
            size            = instances.size ();

            if (cbed.max[index] != null)
            {
                max = (int) ((Scalar) get (cbed.max[index])).value;
                connectedCount = cbed.count[index];
            }

            if (cbed.project[index] != null) rank += 1;

            // Prepare nearest neighbor search structure
            int    k      = 0;
            double radius = 0;
            if (cbed.k     [index] != null) k      = (int) ((Scalar) get (cbed.k     [index])).value;
            if (cbed.radius[index] != null) radius =       ((Scalar) get (cbed.radius[index])).value;
            if (k > 0  ||  radius > 0)
            {
                rank -= 2;
                NN = new KDTree ();
                NN.k      = k      > 0 ? k      : Integer.MAX_VALUE;
                NN.radius = radius > 0 ? radius : Double.POSITIVE_INFINITY;

                entries = new ArrayList<KDTree.Entry> (size);
                project = cbed.project[index];
                c = new Part (equations, Population.this);
                for (int i = 0; i < size; i++)
                {
                    p = instances.get (i);
                    if (p == null) continue;

                    KDTree.Entry e = new KDTree.Entry ();
                    if (project == null)
                    {
                        e.point = ((MatrixDense) p.getXYZ (simulator)).getRawColumn (0);
                    }
                    else
                    {
                        c.setPart (index, p);
                        e.point = getProject ();
                    }
                    e.item = p;
                    entries.add (e);
                }
                NN.set (entries);
            }
        }

        @SuppressWarnings("unchecked")
        public double[] getProject ()
        {
            if (cbed.projectReferences[index] != null)
            {
                c.resolve ((TreeSet<VariableReference>) cbed.projectReferences[index]);
            }

            InstanceTemporaries temp = new InstanceTemporaries (c, simulator, true);  // $project is evaluated during the init cycle. TODO: change flag to $connect, and let $init be false.
            for (Object o : (ArrayList<Variable>) cbed.projectDependencies[index])
            {
                Variable v = (Variable) o;
                Type result = v.eval (temp);
                if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
            }
            return ((MatrixDense) project.eval (temp)).getRawColumn (0);
        }

        /**
            @return true If we need to advance to the next instance. This happens when p
            has reached its max number of connections.
        **/
        public boolean setProbe (Part probe)
        {
            c = probe;
            boolean result = false;
            if (p != null)
            {
                // A new connection was just made, so counts (if they are used) have been updated.
                // Step to next endpoint instance if current instance is full.
                if (max > 0  &&  p.valuesFloat[connectedCount] >= max) result = true;
                else probe.setPart (index, p);
            }
            if (permute != null  &&  permute.setProbe (probe))
            {
                i = stop;  // next() will trigger a reset
                result = true;
            }
            return result;
        }

        /**
            Restarts this iterator at a random point.
            Called multiple times, depending on how many times permute.next() returns true.
        **/
        public void reset (boolean newOnly)
        {
            this.newOnly = newOnly;
            if (NN != null  &&  permute != null)
            {
                double[] query = ((MatrixDense) c.getXYZ (simulator)).getRawColumn (0);
                List<KDTree.Entry> result = NN.find (query);
                count = result.size ();
                filtered = new ArrayList<Part> (count);
                for (KDTree.Entry e : result) filtered.add ((Part) e.item);
                i = 0;
            }
            else
            {
                if (newOnly) count = size - firstborn;
                else         count = size;
                if (count > 1) i = (int) Math.round (Math.random () * (count - 1));
                else           i = 0;
            }
            stop = i + count;
        }

        /**
            Indicates that all iterators from this level down returned a part that is old.
        **/
        public boolean old ()
        {
            if (p.valuesFloat[pbed.newborn] != 0) return false;
            if (permute != null) return permute.old ();
            return true;
        }

        /**
            Advances to next part that meets criteria, and sets the appropriate endpoint in probe.
            @return false If no more parts are available.
        **/
        public boolean next ()
        {
            while (true)
            {
                if (i >= stop)  // Need to either reset or terminate, depending on whether we have something left to permute with.
                {
                    if (permute == null)
                    {
                        if (stop > 0) return false;  // We already reset once, so done.
                        // A unary connection should only iterate over new instances.
                        // The innermost (slowest) iterator of a multi-way connection should iterate over all instances.
                        reset (! contained);
                    }
                    else
                    {
                        if (! permute.next ()) return false;
                        if (contained) reset (false);
                        else           reset (permute.old ());
                    }
                }

                if (NN != null  &&  permute != null)  // Note: One endpoint must must act as anchor for spatial constraints.
                {
                    for (; i < stop; i++)
                    {
                        p = filtered.get (i);
                        if (p.valuesFloat[pbed.newborn] != 0)
                        {
                            if (max == 0) break;
                            if (p.valuesFloat[connectedCount] < max) break;
                        }
                    }
                }
                else if (newOnly)
                {
                    for (; i < stop; i++)
                    {
                        p = instances.get (i % count + firstborn);
                        if (p != null  &&  p.valuesFloat[pbed.newborn] != 0)
                        {
                            if (max == 0) break;
                            if (p.valuesFloat[connectedCount] < max) break;
                        }
                    }
                }
                else
                {
                    for (; i < stop; i++)
                    {
                        p = instances.get (i % count);
                        if (p != null)
                        {
                            if (max == 0) break;
                            if (p.valuesFloat[connectedCount] < max) break;
                        }
                    }
                }

                i++;
                if (p != null  &&  i <= stop)
                {
                    c.setPart (index, p);
                    if (permute == null  &&  xyz != null)  // Spatial filtering is on, and we are the endpoint that determines the query
                    {
                        // Obtain C.$xyz, the best way we can
                        if (cbed.xyz != null)  // C explicitly defines $xyz
                        {
                            // This is a very minimal evaluation. No reference resolution. No temporaries. TODO: deeper eval of C.$xyz ?
                            xyz = ((MatrixDense) c.getXYZ (simulator)).getRawColumn (0);
                        }
                        // TODO: if (NN != null) we could look up the previously calculated value
                        // That would require sorting them in a way that can be indexed.
                        // Could make an ArrayList<double[]> with exactly the same structure as instances.
                        else if (project == null)
                        {
                            xyz = ((MatrixDense) p.getXYZ (simulator)).getRawColumn (0);
                        }
                        else
                        {
                            xyz = getProject ();
                        }
                    }
                    return true;
                }
            }
        }
    }

    public void connect (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        int count = equations.connectionBindings.size ();
        ArrayList<ConnectIterator> iterators = new ArrayList<ConnectIterator> (count);
        boolean nothingNew = true;
        boolean spatialFiltering = false;
        for (int i = 0; i < count; i++)
        {
            Population population = getTarget (i);
            if (population == null  ||  population.n == 0) return;  // Nothing to connect. This should never happen.

            ConnectIterator it = new ConnectIterator (i, population, bed, simulator);
            iterators.add (it);
            if (it.firstborn < it.instances.size ()) nothingNew = false;
            if (it.NN != null) spatialFiltering = true;
            simulator.clearNew (population);
        }
        if (nothingNew) return;

        // Sort so that population with the most old entries is the outermost iterator.
        // That allows the most number of old entries to be skipped.
        // This is a simple insertion sort ...
        for (int i = 1; i < count; i++)
        {
            for (int j = i; j > 0; j--)
            {
                ConnectIterator A = iterators.get (j-1);
                ConnectIterator B = iterators.get (j);
                if (A.firstborn >= B.firstborn) break;
                iterators.set (j-1, B);
                iterators.set (j,   A);
            }
        }

        // For spatial filtering, make the innermost iterator be the one that best defines C.$xyz
        if (spatialFiltering)
        {
            double[] xyz = new double[3];
            for (int i = 0; i < count; i++) iterators.get (i).xyz = xyz;

            if (bed.xyz == null  ||  bed.xyz.equations.size () == 0)  // connection's own $xyz is not defined, so must get it from some $project
            {
                int last = count - 1;
                ConnectIterator A = iterators.get (last);
                int    bestIndex = last;
                double bestRank  = A.rank;
                for (int i = 0; i < last; i++)
                {
                    A = iterators.get (i);
                    if (A.rank > bestRank)
                    {
                        bestIndex = i;
                        bestRank  = A.rank;
                    }
                }
                if (bestIndex != last)
                {
                    A = iterators.remove (bestIndex);
                    iterators.add (A);
                }
            }
        }

        for (int i = 1; i < count; i++)
        {
            ConnectIterator A = iterators.get (i-1);
            ConnectIterator B = iterators.get (i);
            A.permute   = B;
            B.contained = true;
        }

        // TODO: implement $min, or consider eliminating it from the language
        // $max is easy, but $min requires one or more forms of expensive accounting to do correctly.
        // Problems include:
        // 1) need to prevent duplicate connections
        // 2) should pick the highest probability connections
        // A list of connections held by each target could solve #1.
        // Such an approach may be necessary for ongoing maintenance of connections, beyond just this new-connection process.
        // A temporary list of connections that were rejected, sorted by probability, could solve issue #2.
        // However, this is more difficult to implement for any but the outer loop. Could implement an
        // outer loop for each of the other populations, just for fulfilling $min.

        ConnectIterator outer = iterators.get (0);
        Part c = new Part (equations, this);
        outer.setProbe (c);
        while (outer.next ())
        {
            c.resolve ();
            double create = c.getP (simulator);
            if (create <= 0  ||  create < 1  &&  create < simulator.random.nextDouble ()) continue;  // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it should have no effect.
            ((Part) container).event.enqueue (c);
            c.init (simulator);
            c = new Part (equations, this);
            outer.setProbe (c);
        }
    }

    @SuppressWarnings("unchecked")
    public void clearNew ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
        int count     = instances.size ();
        int firstborn = (int) valuesFloat[bed.firstborn];
        for (int i = firstborn; i < count; i++)
        {
            Part p = instances.get (i);
            if (p == null) continue;
            p.valuesFloat[bed.newborn] = 0;
        }
        valuesFloat[bed.firstborn] = count;
    }

    @SuppressWarnings("unchecked")
    public void insert (Part p)
    {
        n++;

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.index != null)
        {
            int index;
            if (valuesObject[bed.indexAvailable] == null)
            {
                index = (int) valuesFloat[bed.indexNext]++;
            }
            else
            {
                ArrayList<Integer> availableIndex = (ArrayList<Integer>) valuesObject[bed.indexAvailable];
                index = availableIndex.remove (availableIndex.size () - 1);
                if (availableIndex.size () < 1) valuesObject[bed.indexAvailable] = null;
            }
            p.set (bed.index, new Scalar (index));

            if (bed.instances >= 0)
            {
                ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
                if (instances == null)
                {
                    instances = new ArrayList<Part> (index + 1);
                    valuesObject[bed.instances] = instances;
                }
                for (int size = instances.size (); size <= index; size++) instances.add (null);
                instances.set (index, p);

                if (equations.connected)
                {
                    p.valuesFloat[bed.newborn] = 1;
                    valuesFloat[bed.firstborn] = Math.min (valuesFloat[bed.firstborn], index);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void remove (Part p)
    {
        n--;  // presuming that p is actually here

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.index != null)
        {
            int index = (int) ((Scalar) p.get (bed.index)).value;

            ArrayList<Integer> availableIndex = (ArrayList<Integer>) valuesObject[bed.indexAvailable];
            if (availableIndex == null)
            {
                availableIndex = new ArrayList<Integer> ();
                valuesObject[bed.indexAvailable] = availableIndex;
            }
            availableIndex.add (index);

            if (bed.instances >= 0)
            {
                ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
                instances.set (index, null);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void resize (Simulator simulator, int requestedN)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        if (requestedN < 0)  // indicates to update $n from actual part count
        {
            int currentN = (int) ((Scalar) get (bed.n)).value;
            // In general, $n can be fractional, which allows gradual growth over many cycles.
            // Only change $n if it does not truncate to same as actual n.
            if (currentN != n) setFinal (bed.n, new Scalar (n));
            return;
        }

        while (n < requestedN)
        {
            Part p = new Part (equations, this);
            ((Part) container).event.enqueue (p);
            p.resolve ();
            p.init (simulator);
        }

        if (n > requestedN)
        {
            ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
            for (int i = instances.size () - 1; i >= 0  &&  n > requestedN; i--)
            {
                Part p = instances.get (i);
                if (p == null) continue;
                p.die ();  // Part.die() is responsible to call remove(), which decreases n. p itself won't dequeue until next simulator cycle.
            }
        }
    }
}
