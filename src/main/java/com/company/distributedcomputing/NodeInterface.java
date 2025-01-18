package com.company.distributedcomputing;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface NodeInterface extends Remote {
    void receiveWork(int work, String senderId, WorkContext context) throws RemoteException;
    void receiveAcknowledgment(String fromId, String contextId) throws RemoteException;
    boolean checkStatus() throws RemoteException;
    void updateParentId(String id) throws RemoteException;
    Map<String, Node> getNeighbors() throws RemoteException;
}