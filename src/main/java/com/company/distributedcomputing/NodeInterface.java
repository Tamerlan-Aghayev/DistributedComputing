package com.company.distributedcomputing;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface NodeInterface extends Remote {
    void receiveWork(int workAmount, String senderId, WorkContext context) throws RemoteException, NotBoundException;
    void receiveAcknowledgment(String fromId, String contextId) throws RemoteException;
    boolean checkStatus() throws RemoteException;
    Map<String, Node> getNeighbors() throws RemoteException;
    void updateParentId(String id) throws RemoteException;
    void addNeighbor(String id, Node node) throws RemoteException;
    void removeNeighbor(String id) throws RemoteException;
    Map<String, Node> joinTopology(String host, int port) throws RemoteException;
    String leaveTopology() throws RemoteException;
    String kill() throws RemoteException;
    String revive() throws RemoteException;
    String setDelay(int newDelay) throws RemoteException;
    String getMyId() throws RemoteException;
    Node getMyNode() throws RemoteException;
    String getParentId() throws RemoteException;
    int getWork() throws RemoteException;
    void addToInvitation(String id, Node node) throws RemoteException;
}