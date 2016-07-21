package edu.msu.mi.forum.algo

import cern.colt.matrix.tdouble.DoubleMatrix2D
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra
import cern.colt.matrix.tdouble.algo.decomposition.DenseDoubleEigenvalueDecomposition
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D
import edu.msu.mi.forum.graph.AttributeEdge
import edu.msu.mi.forum.graph.JGraphTUtils
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import org.apache.commons.math3.stat.regression.SimpleRegression
import org.jgrapht.WeightedGraph

/**
 *
 * Computes core and periphery using a low rank approximation to the matrix, following by local hill-climbing to find
 * a more optimal cut.
 *
 * see Mihai Cucuringu, M. Puck Rombach, Sang Hoon Lee, and Mason A. Porter. 2014.
 * Detection of Core-Periphery Structure in Networks Using Spectral Methods and Geodesic
 * Paths. arXiv:1410.6572 [cond- mat, physics:physics].
 * Retrieved May 16, 2015 from http://arxiv.org/abs/1410.6572
 *
 * Created by josh on 4/29/15.
 */
class LowRankCore<V, E> {

    WeightedGraph<V, E> graph

    List<String> vertices

    Set<String> core  = [] as Set


    DoubleMatrix2D matrix

    double alpha = 0.0

    double cliff = 0.4

    boolean cliffEnabled = true

    SimpleRegression regression = new SimpleRegression()

    Closure flattenfx = { weight->flatten(weight)}

    /**
     * Calculate core / periphery on a weighted graph
     *
     * @param graph The graph to process
     * @param flattenfx  A function used to threshold the weight to determine a connection between nodes
     * @param alpha Used to weight the correlation
     */

    public LowRankCore(WeightedGraph<V, E> graph, Closure flattenfx = null, double alpha = 0.0) {
        this.graph = graph
        this.alpha = alpha
        if (flattenfx) this.flattenfx = flattenfx
        vertices = JGraphTUtils.sortByEdgeWeight(graph)
        matrix = JGraphTUtils.asMatrix(graph, vertices,{flattenfx(it)})
        if (vertices.size() > 0) {
            rankVertices()
        }
        (0..<vertices.size()).each { x ->
            ((x + 1)..<vertices.size()).each { y ->
                regression.addData(getWeight(vertices[x], vertices[y]),0.0)

            }
        }

    }

    /**
     * Calculate core / periphery on a weighted graph
     *
     * @param graph  The graph to process
     * @param cliffEnabled use a simple cliffing function to determine whether an edge is in or out
     * @param alpha Used to weight correlation
     * @param cliff The value used as a cuttoff weight
     */
    public LowRankCore(WeightedGraph<V, E> graph, boolean cliffEnabled, double alpha = 0.0, double cliff=0.4) {
        this.graph = graph
        this.alpha = alpha
        this.cliff = cliff
        this.cliffEnabled = cliffEnabled
        vertices = JGraphTUtils.sortByEdgeWeight(graph)
        matrix = JGraphTUtils.asMatrix(graph, vertices,{flattenfx(it)})
        if (vertices.size() > 0) {
            rankVertices()
        }
        (0..<vertices.size()).each { x ->
            ((x + 1)..<vertices.size()).each { y ->
                regression.addData(getWeight(vertices[x], vertices[y]),0.0)

            }
        }
    }

    public double getWeight(String v1, String v2) {
        flattenfx(JGraphTUtils.safeGetWeight(graph, v1, v2))

    }

    public double flatten(double weight) {
        if (cliffEnabled) {
            weight >=cliff?1.0:0.0
        } else {
            return weight
        }

    }





    public static double blockCorrelation(Collection<String> core, WeightedGraph<String, AttributeEdge> graph, alpha = 0.0) {
        List<String> vertices = graph.vertexSet() as List
        Set coreset = core as Set
        List weights = []
        List pattern = []
        (0..<vertices.size()).each { x ->
            ((x + 1)..<vertices.size()).each { y ->
                boolean a1 = vertices[x] in coreset
                boolean a2 = vertices[y] in coreset
                if (a1 == a2) {
                    weights << JGraphTUtils.safeGetWeight(graph, vertices[x], vertices[y])
                    pattern << (a1 ? 1.0 : 0.0)
                } else if (alpha) {
                    weights << JGraphTUtils.safeGetWeight(graph, vertices[x], vertices[y])
                    pattern << alpha
                }

            }

        }
        return weights.size() >= 2 ? new PearsonsCorrelation().correlation(weights as double[], pattern as double[]) : 0.0

    }

    private rankVertices() {

        DenseDoubleEigenvalueDecomposition decomp = new DenseDoubleAlgebra().eig(matrix)
        List eigenValues = decomp.realEigenvalues.toArray() as List
        int l1 = eigenValues.indexOf(eigenValues.max {
            Math.abs(it)
        })
        eigenValues[l1] = 0.0
        int l2 = eigenValues.indexOf(eigenValues.max {
            Math.abs(it)
        })
        DoubleMatrix2D partVec = decomp.getV().viewSelection(null, [l1, l2] as int[])
        DoubleMatrix2D partVal = new DenseDoubleMatrix2D([[l1, 0.0], [0.0, l2]] as double[][])
        DoubleMatrix2D est = partVec.zMult(partVal, partVec.like()).zMult(partVec.viewDice(), matrix.like())

        Map weight = (0..<est.rows()).collectEntries {
            [vertices[it], est.viewRow(it).zSum()]
        }
        vertices.sort(true) {
            -(weight[it])
        }
        vertices
    }

    public Set fullyOptimize() {
        int corecount = core.size()
        while (corecount < {optimize();core.size()}()) {
            corecount = core.size()
        }
    }

    public Set optimize(min_ftness = 0.1) {

        double best = fitness()
        Set toremove = [] as Set


        Map closest = [:]
        def addToClosest = { cnode ->
            if (closest[cnode]) {
                closest.remove(cnode)
            }
            graph.edgesOf(cnode).each { AttributeEdge edge ->
                def alter = JGraphTUtils.getAlter(graph, cnode, edge)
                if (!core.contains(alter)) {
                    closest[(alter)] = (closest[(alter)] ?: 0)+graph.getEdgeWeight(edge)
//                    if (flatten(graph.getEdgeWeight(edge)) > 0) {
//                        closest[(alter)] = 1 + (closest[(alter)] ?: 0)
//                    }
                }

            }
        }

        def resort = { (closest.keySet() as List).sort { closest[it] } }
        int size = core.size()
        core.each { cnode -> addToClosest(cnode) }
        List totest = resort()
        print("Optimizing ${best}...")
        while (totest) {


            def node_to_add = totest.pop()
            addToCore(node_to_add)
            double nfitness = fitness()
            if (nfitness >= (best - min_ftness)) {

                if (nfitness > best) {
                    //println "Best ${best} and new fitness ${nfitness} = ${best-nfitness}"
                    best = nfitness
                    toremove.clear()
                } else {
                    toremove<< node_to_add
                }
                addToClosest(node_to_add)
                //totest = resort()
            } else {
                removeFromCore(node_to_add)
            }

        }
        toremove.each {

            removeFromCore(it)
        }
        println "Final fitness ${fitness()} (${core.size()-size} nodes added)"
        core


    }

    public void addToCore(String node) {
        if (node in core) {
            println "Already in core"

        } else {
            vertices.each {
                if (it in core) {
                    regression.addData(getWeight(node,it),1.0)
                    if (alpha > 0) {
                        regression.removeData(getWeight(node,it), alpha)
                    }

                } else {
                    regression.removeData(getWeight(node,it),0.0)
                    if (alpha > 0) {
                        regression.addData(getWeight(node,it), alpha)
                    }

                }

            }
            core.add(node)
        }
    }

    public void removeFromCore(String node) {
        if (!core.contains(node)) {
            println "Not in core"
        } else {
            core.remove(node)
            vertices.each {
                if (it in core) {
                    regression.removeData(getWeight(node,it),1.0)
                    if (alpha > 0) {
                        regression.addData(getWeight(node,it),alpha)
                    }
                } else {
                    regression.addData(getWeight(node,it),0.0)
                    if (alpha > 0) {
                        regression.removeData(getWeight(node,it),alpha)
                    }
                }

            }

        }

    }

    public double fitness() {
        return regression.getR()
    }

    public Set findCut(List scoredVertices=null) {
        scoredVertices = vertices
        double max = -1
        int maxidx = -1
        int offpeak = 0
        Set toremove = [] as Set
        if (scoredVertices.size() > 2) {
            (0..<scoredVertices.size()).find {
                addToCore(scoredVertices[it])
                double correlation = fitness()
                //double ofun = approxObjectiveFunction(scoredVertices.subList(0, it))
                if (Double.isNaN(correlation)) {
                    println("Skipping")
                } else if (max < correlation) {
                    max = correlation
                    maxidx = it
                    offpeak = 0
                    toremove.clear()
                    println "$it.${scoredVertices[it]} : $correlation "
                } else {
                    offpeak++
                    toremove<<scoredVertices[it]
                    //println "Decending: ${correlation} "
                }
                if (offpeak > 10) {
                    //println "Stopping off peak by $offpeak"
                    return true
                } else {
                    return false
                }
            }
            toremove.each {removeFromCore(it)}
            return scoredVertices.subList(0, maxidx + 1) as Set
        } else {
            return scoredVertices
        }


    }

    public double pearsonCorrelation(Collection corelist) {
        blockCorrelation(corelist, graph)
    }


}
