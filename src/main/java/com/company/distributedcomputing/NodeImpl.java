package com.company.distributedcomputing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@Getter
public class NodeImpl extends UnicastRemoteObject implements NodeInterface {

    private int work;

    private boolean isActive=true;

    private int delay=0;

    @Value("${node.id}")
    private String myId;
    @Value("${node.rmi.port}")
    private int rmiPort;

    @Value("${node.rmu.host}")
    private String rmiHost;

    private final Node myNode=new Node(rmiHost, rmiPort);

    private final Topology topology;



    public Map<String,Node> joinTopology(String host, int port) {
        try {
            NodeImpl nodeImpl=getRemoteNodeImpl(host, port);
            addNeighbor(nodeImpl.getMyId(), nodeImpl.getMyNode());
            nodeImpl.addNeighbor(getMyId(), getMyNode());

            return this.topology.neighbors;
        } catch (Exception e) {
            log.error("error happened", e);
            return null;
        }
    }

    public String leaveTopology(){
        try {
            Set<String> nodeIds=this.topology.neighbors.keySet();
            for (String nodeId: nodeIds){
                Node remoteNode=this.topology.neighbors.get(nodeId);
                NodeImpl remoteNodeImpl=getRemoteNodeImpl(remoteNode.getHost(), remoteNode.getPort());
                remoteNodeImpl.removeNeighbor(nodeId);
            }
            this.topology.neighbors.clear();
            return "Removed Node "+myId;

        } catch (Exception e) {
            log.error("error happened", e);
            return null;
        }
    }

    public String kill(){
        this.isActive=false;
        return myId+ " is killed";
    }

    public String revive(){
        this.isActive=true;
        return myId+ " is revived";
    }

    public boolean checkStatus(){
        return this.isActive;
    }

    public String setDelay(int delay){
        this.delay=delay;
        return "delay is set to "+delay+" milliseconds";
    }


    private NodeImpl getRemoteNodeImpl(String host, int port) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        return (NodeImpl) registry.lookup("NodeImpl");
    }


    public void receiveWork(int work) throws NotBoundException, RemoteException {
        this.work=work;
        distributeWork();
        doWork();

    }

    public void doWork(){
        while (this.work>0){
            this.work--;
        }
    }

    public void distributeWork() throws NotBoundException, RemoteException {
        Map<String, Node> children=this.topology.neighbors;
        children.remove(this.topology.parentId);
        List<NodeImpl> workers=new ArrayList<>();
        for (String childId: children.keySet()){
            Node child=children.get(childId);
            NodeImpl remoteNodeImpl=getRemoteNodeImpl(child.getHost(), child.getPort());
            if (remoteNodeImpl.checkStatus() && remoteNodeImpl.getTopology().parentId==null){
                remoteNodeImpl.updateParentId(getMyId());
                workers.add(remoteNodeImpl);
            }

        }
        int portion=(int)Math.ceil((double)this.work/workers.size());
        for (NodeImpl worker: workers){
            worker.receiveWork(Math.min(portion, this.work));
            this.work-=Math.min(portion, this.work);
        }
    }

    public void addNeighbor(String id, Node node){
        this.topology.addNeighbor(id, node);
    }

    public void removeNeighbor(String id){
        this.topology.removeNeighbor(id);
    }

    public void updateParentId(String id) {
        this.topology.parentId=id;
    }
}
