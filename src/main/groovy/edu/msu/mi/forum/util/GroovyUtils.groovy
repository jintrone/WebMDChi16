package edu.msu.mi.forum.util

/**
 * Created by josh on 7/14/16.
 */
class GroovyUtils {

    static collectItems(Map collect, def key, def value) {
        collect[key] = (collect[key]?:[] as Set)+value
        collect
    }

    static countOccurrences(Map collect,Collection items) {
        items.each {
            collect[it] = (collect[it]?:0)+1
        }
        collect
    }


    static collectItemsByMap(Map collect, def key, Map value, Closure aggregate = null) {
        if (collect[key]) {
            value.each {k,v->
                if (collect[key][k]) {
                    collect[key][k] = aggregate?aggregate(collect[key][k],v):v
                }
            }
        } else {
            collect[key] = [:]+value
        }
        collect
    }

    static sumCollectedItems(Map... maps) {
        maps.inject { Map collect, Map sample->
            sample.each {k, Set v ->
                collectItems(collect,k,v)

            }
            collect
        }
    }
}
