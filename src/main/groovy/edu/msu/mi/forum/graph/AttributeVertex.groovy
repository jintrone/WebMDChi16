package edu.msu.mi.forum.graph

/**
 * Created by josh on 5/30/15.
 */
class AttributeVertex {

    final String id
    Map attr = [:]


    public AttributeVertex(String id) {
        this.id = id
    }

    public void setAttribute(String key, def value) {
        attr[key]=value
    }

    def getAttributeValue(String key) {
        attr[key]
    }

    public Set<String> getAttributes() {
        attr.keySet()
    }

    public Map getAttributeMap() {
        attr
    }

    public int hashCode() {
        return (id.hashCode()+getClass().hashCode())*13
    }

    public boolean equals(Object o) {
        o.getClass()==this.getClass() &&  o.id==this.id
    }


}
