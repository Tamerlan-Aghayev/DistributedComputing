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
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

@Service
@Slf4j
@Getter
public class NodeImpl extends UnicastRemoteObject implements NodeInterface {
    public NodeImpl() throws RemoteException {
        super();
    }
    private final AtomicInteger work = new AtomicInteger(0);
    private final AtomicBoolean isActive = new AtomicBoolean(true);
    private final AtomicInteger delay = new AtomicInteger(0);

    // Thread pool for handling concurrent operations
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // Concurrent collections for tracking work and acknowledgments
    private final ConcurrentHashMap<String, CountDownLatch> completionLatches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> pendingAcks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkContext> activeContexts = new ConcurrentHashMap<>();

    @Value("${node.id}")
    private String myId;

    @Value("${node.rmi.port}")
    private int rmiPort;

    @Value("${node.rmi.host}")
    private String rmiHost;

    private final Node myNode=new Node(rmiHost, rmiPort);
    private final Topology topology=new Topology();

    @Override
    public synchronized void receiveWork(int workAmount, String senderId, WorkContext context) throws RemoteException {
        if (!isActive.get()) {
            throw new RemoteException("Node " + myId + " is not active");
        }

        try {
            Thread.sleep(delay.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Initialize work tracking for this context
        activeContexts.put(context.getContextId(), context);
        pendingAcks.put(context.getContextId(), ConcurrentHashMap.newKeySet());

        // Update parent if this is new work
        if (topology.getParentId() == null && senderId != null) {
            topology.setParentId(senderId);
        }

        // Process work asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                work.set(workAmount);
                distributeWork(context);
            } catch (Exception e) {
                log.error("Error processing work", e);
            }
        }, executorService);
    }

    private void distributeWork(WorkContext context) throws RemoteException, NotBoundException {
        if (work.get() <= 0) {
            sendAcknowledgment(context);
            return;
        }

        // Get available workers (excluding parent for this round)
        Map<String, Node> availableWorkers = new ConcurrentHashMap<>(topology.getNeighbors());
        if (topology.getParentId() != null) {
            availableWorkers.remove(topology.getParentId());
        }

        if (availableWorkers.isEmpty()) {
            doWork();
            sendAcknowledgment(context);
            return;
        }

        // Calculate work distribution
        int totalWork = work.get();
        int numWorkers = availableWorkers.size();
        int baseWork = totalWork / numWorkers;
        int extraWork = totalWork % numWorkers;

        // Create completion latch
        CountDownLatch workLatch = new CountDownLatch(numWorkers);
        completionLatches.put(context.getContextId(), workLatch);

        // Distribute work
        int assigned = 0;
        for (Map.Entry<String, Node> entry : availableWorkers.entrySet()) {
            String workerId = entry.getKey();
            Node workerNode = entry.getValue();

            try {
                int workerShare = baseWork + (assigned < extraWork ? 1 : 0);
                assigned++;

                if (workerShare > 0) {
                    NodeImpl worker = getRemoteNodeImpl(workerNode.getHost(), workerNode.getPort());
                    if (worker.checkStatus()) {
                        pendingAcks.get(context.getContextId()).add(workerId);
                        WorkContext workerContext = new WorkContext(context, context.getDepth() + 1);
                        worker.receiveWork(workerShare, myId, workerContext);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to distribute work to worker " + workerId, e);
                workLatch.countDown();
            }
        }

        // Do remaining work
        doWork();

        // Wait for all workers to complete
        try {
            workLatch.await();
            sendAcknowledgment(context);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void doWork() {
        while (work.get() > 0) {
            if (!isActive.get()) {
                break;
            }
            work.decrementAndGet();
            try {
                Thread.sleep(1000); // Simulate work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void receiveAcknowledgment(String fromId, String contextId) throws RemoteException {
        Set<String> pending = pendingAcks.get(contextId);
        if (pending != null) {
            pending.remove(fromId);
            CountDownLatch latch = completionLatches.get(contextId);
            if (latch != null) {
                latch.countDown();
            }

            // If all acknowledgments received, send to parent
            if (pending.isEmpty()) {
                WorkContext context = activeContexts.get(contextId);
                if (context != null) {
                    sendAcknowledgment(context);
                }
            }
        }
    }

    private void sendAcknowledgment(WorkContext context) {
        if (topology.getParentId() != null) {
            try {
                Node parentNode = topology.getNeighbor(topology.getParentId());
                if (parentNode != null) {
                    NodeImpl parent = getRemoteNodeImpl(parentNode.getHost(), parentNode.getPort());
                    parent.receiveAcknowledgment(myId, context.getContextId());
                }
            } catch (Exception e) {
                log.error("Failed to send acknowledgment to parent", e);
            }
        } else if (myId.equals(context.getRootId())) {
            log.info("Work completed for context: " + context.getContextId());
        }
    }

    @Override
    public boolean checkStatus() throws RemoteException {
        return isActive.get();
    }

    public Map<String, Node> joinTopology(String host, int port) {
        try {
            NodeImpl nodeImpl = getRemoteNodeImpl(host, port);
            addNeighbor(nodeImpl.getMyId(), nodeImpl.getMyNode());
            nodeImpl.addNeighbor(myId, myNode);
            return topology.getNeighbors();
        } catch (Exception e) {
            log.error("Failed to join topology", e);
            return null;
        }
    }

    public String leaveTopology() {
        try {
            for (Map.Entry<String, Node> entry : topology.getNeighbors().entrySet()) {
                Node remoteNode = entry.getValue();
                NodeImpl remoteNodeImpl = getRemoteNodeImpl(remoteNode.getHost(), remoteNode.getPort());
                remoteNodeImpl.removeNeighbor(myId);
            }
            topology.clear();
            return "Removed Node " + myId;
        } catch (Exception e) {
            log.error("Failed to leave topology", e);
            return null;
        }
    }

    public String kill() {
        isActive.set(false);
        return myId + " is killed";
    }

    public String revive() {
        isActive.set(true);
        return myId + " is revived";
    }

    public String setDelay(int newDelay) {
        delay.set(newDelay);
        return "delay is set to " + newDelay + " milliseconds";
    }

    private NodeImpl getRemoteNodeImpl(String host, int port) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        return (NodeImpl) registry.lookup("NodeImpl");
    }

    public void addNeighbor(String id, Node node) {
        topology.addNeighbor(id, node);
    }

    public void removeNeighbor(String id) {
        topology.removeNeighbor(id);
    }

    @Override
    public void updateParentId(String id) throws RemoteException {
        topology.setParentId(id);
    }

    @Override
    public Map<String, Node> getNeighbors() throws RemoteException {
        return this.topology.getNeighbors();
    }
}