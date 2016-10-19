package edu.msu.mi.forum.graph

import cern.colt.matrix.tdouble.DoubleMatrix2D
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D
import org.jgrapht.Graph
import org.jgrapht.VertexFactory
import org.jgrapht.WeightedGraph
import org.jgrapht.ext.IntegerEdgeNameProvider
import org.jgrapht.ext.VertexNameProvider
import org.jgrapht.generate.RandomGraphGenerator
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.graph.SimpleGraph
import org.jgrapht.graph.SimpleWeightedGraph

/**
 * Created by josh on 4/4/15.
 */
class JGraphTUtils {

    static <T, E> E safeAdd(Graph<T, E> graph, T from, T to) {
        if (graph.containsEdge(from, to)) {
            return graph.getEdge(from, to)
        }
        if (!graph.containsVertex(from)) {
            graph.addVertex(from)
        }
        if (!graph.containsVertex(to)) {
            graph.addVertex(to)
        }

        graph.addEdge(from, to)

    }

    static <T, E> E safeAddWithDefault(WeightedGraph<T, E> graph, T from, T to, double weight) {
        if (graph.containsEdge(from, to)) {
            return graph.getEdge(from, to)
        }
        if (!graph.containsVertex(from)) {
            graph.addVertex(from)
        }
        if (!graph.containsVertex(to)) {
            graph.addVertex(to)
        }

        E edge = graph.addEdge(from, to)
        graph.setEdgeWeight(edge,weight)
        edge

    }

    static <T, E> E safeAdd(WeightedGraph<T, E> graph, T from, T to, double weight) {
        E result = safeAdd(graph, from, to)
        graph.setEdgeWeight(result, weight)
        result
    }



    static <T, E> E safeAddIncrement(WeightedGraph<T, E> graph, T from, T to, double weight) {
        boolean inc = graph.containsEdge(from, to)
        E result = safeAdd(graph, from, to)
        if (inc) {
            double current = graph.getEdgeWeight(result)
            graph.setEdgeWeight(result, current + weight)
        } else {
            graph.setEdgeWeight(result, weight)
        }
        result
    }

    static <T, E> double safeGetWeight(WeightedGraph<T, E> graph, T from, T to) {
        E edge = graph.getEdge(from, to)
        edge ? graph.getEdgeWeight(edge) : 0.0
    }

    static <T, E> List<T> sortByEdgeWeight(WeightedGraph<T, E> graph, Closure fx = {
        vertex -> -graph.edgesOf(vertex).sum { graph.getEdgeWeight(it) }
    }) {
        List<T> v = graph.vertexSet() as List<T>
        v.sort(true, fx)
    }

    static <V, E> DoubleMatrix2D asMatrix(WeightedGraph<V, E> graph, List vertices = graph.vertexSet() as List, Closure weightfunc = { weight -> weight }) {
        DoubleMatrix2D result = new SparseDoubleMatrix2D(vertices.size(), vertices.size())
        vertices.eachWithIndex { f, i1 ->
            result.setQuick(i1, i1, 0.0)
            vertices.subList(i1, vertices.size()).eachWithIndex { t, ix ->
                int i2 = ix + i1
                result.setQuick(i1, i2, weightfunc(safeGetWeight(graph, f, t)))
                result.setQuick(i2, i1, weightfunc(safeGetWeight(graph, t, f)))

            }
        }
        result

    }

    static <V, E> V getAlter(WeightedGraph<V, E> graph, V node, E edge) {
        graph.getEdgeSource(edge) == node ? graph.getEdgeTarget(edge) : graph.getEdgeSource(edge)
    }



    static SimpleDirectedWeightedGraph<AttributeVertex,AttributeEdge> createRandomWeightedGraph(int nodecount, double density) {
        SimpleDirectedWeightedGraph<AttributeVertex,AttributeEdge> graph = new SimpleDirectedWeightedGraph<AttributeVertex,AttributeEdge>(AttributeEdge.class) {
            public String toString() {
                edgeSet().collect {
                    "${getEdgeSource(it).id}->${getEdgeTarget(it).id} (${getEdgeWeight(it)})"
                }.join(",")
            }
        }
        int nedges = (nodecount*(nodecount-1))*density
        new RandomGraphGenerator(nodecount,nedges).generateGraph(graph,new VertexFactory<AttributeVertex>() {

            private int localid = 0;

            @Override
            AttributeVertex createVertex() {
                return new AttributeVertex("N"+(this.localid++))
            }
        },null)
        return graph
    }

    static SimpleGraph<AttributeVertex,AttributeEdge> createRandomUnweightedGraph(int nodecount, double density) {
        SimpleGraph<AttributeVertex,AttributeEdge> graph = new SimpleGraph<AttributeVertex,AttributeEdge>(AttributeEdge.class);
        int nedges = (nodecount*(nodecount-1)/2)*density
        new RandomGraphGenerator(nodecount,nedges).generateGraph(graph,new VertexFactory<AttributeVertex>() {

            private int localid = 0;

            @Override
            AttributeVertex createVertex() {
                return new AttributeVertex("N"+(this.localid++))
            }
        },null)
        return graph
    }

    static void randomizeWeights(WeightedGraph<AttributeVertex,AttributeEdge> graph, double min=0.0, double max=1.0) {
        Random r = new Random()
        graph.edgeSet().each {
            graph.setEdgeWeight(it,(r.nextDouble()*(max-min))+min)
        }
    }

    static <V,E> WeightedGraph<V,E> copy(WeightedGraph<V,E> from, WeightedGraph<V,E> to) {
        to.removeAllEdges(to.edgeSet())
        from.edgeSet().each { E edge->
            safeAdd(to,from.getEdgeSource(edge),from.getEdgeTarget(edge),from.getEdgeWeight(edge))
        }
        to
    }

    static <V,E> void approach(WeightedGraph<V,E> from, WeightedGraph<V,E> to, double amount = 0.0) {
        to.edgeSet().each { E edge->
            V s = to.getEdgeSource(edge)
            V d = to.getEdgeTarget(edge)
            E ne = safeAddWithDefault(from,s,d,0.0)
            double fw = from.getEdgeWeight(ne)
            double tw = to.getEdgeWeight(edge)
            from.setEdgeWeight(ne,fw + (tw-fw)*amount)
        }

        new HashSet<>(from.edgeSet()).each { E edge->
            V s = to.getEdgeSource(edge)
            V d = to.getEdgeTarget(edge)
            E te = to.getEdge(s,d)
            if (!te) {
                double fw = from.getEdgeWeight(edge)
                double nw = fw - (fw*amount)
                if (nw == 0) {
                    from.removeEdge(edge)
                } else {
                    from.setEdgeWeight(edge,nw)
                }
            }
        }
    }



}
