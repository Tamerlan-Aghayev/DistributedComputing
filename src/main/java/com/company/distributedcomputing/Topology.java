package com.company.distributedcomputing;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
@Component
public class Topology {
    public Map<String,Node> neighbors=new HashMap<>();
    public String parentId;


    public void addNeighbor(String id, Node node){
        neighbors.put(id, node);
    }

    public void removeNeighbor(String id){
        neighbors.remove(id);
    }
}
