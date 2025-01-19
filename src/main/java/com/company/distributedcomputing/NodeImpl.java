package com.company.distributedcomputing;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLOutput;
import java.util.HashMap;
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

    private Node myNode;
    @PostConstruct
    private void init() {
        this.myNode = new Node(rmiHost, rmiPort);
    }
    private final Topology topology=new Topology();


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
        log.info("Worker {} started distributing. Context ID: {}, Work Remaining: {}", myId, context.getContextId(), work.get());

        if (work.get() <= 0) {
            log.info("Worker {} has no work left to distribute. Sending acknowledgment for Context ID: {}", myId, context.getContextId());
            sendAcknowledgment(context);
            return;
        }

        log.info("Worker {} fetching available workers (excluding parent for this round).", myId);
        Map<String, Node> availableWorkers = new ConcurrentHashMap<>(topology.getNeighbors());
        availableWorkers.remove(context.getRootId());
        log.debug("Available workers after removing root {}: {}", context.getRootId(), availableWorkers.keySet());

        Map<String, NodeInterface> remoteWorkers = new ConcurrentHashMap<>();
        for (Map.Entry<String, Node> entry : availableWorkers.entrySet()) {
            String workerId = entry.getKey();
            Node workerNode = entry.getValue();

            log.debug("Attempting to connect to worker {} at host: {}, port: {}", workerId, workerNode.getHost(), workerNode.getPort());
            try {
                NodeInterface worker = getRemoteNodeImpl(workerNode.getHost(), workerNode.getPort());
                if (worker.checkStatus() && worker.getParentId() == null) {
                    log.info("Worker {} is available and has no parent. Assigning current worker {} as parent.", workerId, myId);
                    worker.updateParentId(getMyId());
                    remoteWorkers.put(workerId, worker);
                } else {
                    log.warn("Worker {} is unavailable or already has a parent. Skipping.", workerId);
                }
            } catch (Exception e) {
                log.error("Failed to connect or verify status for worker {}. Skipping this worker.", workerId, e);
            }
        }

        if (remoteWorkers.isEmpty()) {
            log.info("No available workers found for distribution. Worker {} will perform all work locally.", myId);
            doWork();
            sendAcknowledgment(context);
            this.topology.setParentId(null);
            return;
        }

        log.info("Distributing work among {} workers. Total work: {}", remoteWorkers.size(), work.get());

        int totalWork = work.get();
        int numWorkers = remoteWorkers.size();
        int baseWork = totalWork / numWorkers;
        int extraWork = totalWork % numWorkers;

        log.debug("Work distribution calculated. Base work: {}, Extra work: {}", baseWork, extraWork);

        CountDownLatch workLatch = new CountDownLatch(numWorkers);
        completionLatches.put(context.getContextId(), workLatch);

        log.info("Created CountDownLatch for Context ID: {} with count: {}", context.getContextId(), numWorkers);

        int assigned = 0;
        for (Map.Entry<String, NodeInterface> entry : remoteWorkers.entrySet()) {
            String workerId = entry.getKey();
            NodeInterface workerNode = entry.getValue();

            int workerShare = baseWork + (assigned < extraWork ? 1 : 0);
            assigned++;

            if (workerShare > 0) {
                try {
                    log.info("Assigning {} units of work to worker {}. Remaining work before assignment: {}", workerShare, workerId, work.get());
                    pendingAcks.get(context.getContextId()).add(workerId);

                    WorkContext workerContext = new WorkContext(context, context.getDepth() + 1);
                    log.debug("Sending work to worker {}. Context ID: {}, Parent ID: {}, Depth: {}", workerId, workerContext.getContextId(), myId, workerContext.getDepth());

                    workerNode.receiveWork(workerShare, myId, workerContext);
                    this.work.addAndGet(-workerShare);
                    log.info("Work successfully sent to worker {}. Remaining work after assignment: {}", workerId, work.get());
                } catch (Exception e) {
                    log.error("Failed to distribute work to worker {}. Reducing latch count for Context ID: {}", workerId, context.getContextId(), e);
                    workLatch.countDown();
                }
            } else {
                log.debug("No work assigned to worker {} as calculated share is zero.", workerId);
            }
        }

        log.info("Worker {} finished distributing. Remaining work after distribution: {}", myId, work.get());

        doWork();

        log.info("Worker {} completed its own work. Awaiting completion of distributed work for Context ID: {}", myId, context.getContextId());

        try {
            workLatch.await();
            log.info("All workers have completed their work. Sending acknowledgment for Context ID: {}", context.getContextId());
            sendAcknowledgment(context);
            this.topology.setParentId(null);
            log.info("Parent ID reset for worker {}", myId);
        } catch (InterruptedException e) {
            log.error("Worker {} interrupted while waiting for latch countdown. Context ID: {}", myId, context.getContextId(), e);
            Thread.currentThread().interrupt();
        }
    }


    private void doWork() {
        System.out.println("Worker " + myId + " started doing");
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
        System.out.println("Worker " + myId + " finished doing");
    }

    
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
                    NodeInterface parent = getRemoteNodeImpl(parentNode.getHost(), parentNode.getPort());
                    parent.receiveAcknowledgment(myId, context.getContextId());
                }
            } catch (Exception e) {
                log.error("Failed to send acknowledgment to parent", e);
            }
        } else if (myId.equals(context.getRootId())) {
            log.info("Work completed for context: " + context.getContextId());
        }
    }

    
    public boolean checkStatus() throws RemoteException {
        return isActive.get();
    }

    public Map<String, Node> joinTopology(String host, int port) {
        try {
            if (this.rmiHost.equals(host) && this.rmiPort==port) {
                log.warn("It is node itself");
                return topology.getNeighbors();
            }
            NodeInterface remoteNodeImpl = getRemoteNodeImpl(host, port);
            String remoteId = remoteNodeImpl.getMyId();
            Node remoteNode = remoteNodeImpl.getMyNode();
            // Check if we're already connected to this node
            if (topology.getNeighbor(remoteId) != null) {
                log.warn("Already connected to node {}", remoteId);
                return topology.getNeighbors();
            }

            // Atomic operation to add the connection
            synchronized (this) {
                // Double-check in case of race condition
                if (topology.getNeighbor(remoteId) != null) {
                    return topology.getNeighbors();
                }

                // Add remote node to our topology
                topology.addNeighbor(remoteId, remoteNode);

                try {
                    // Add ourselves to remote node's topology
                    remoteNodeImpl.addNeighbor(myId, myNode);
                } catch (Exception e) {
                    // Rollback our change if remote update fails
                    topology.removeNeighbor(remoteId);
                    throw new RuntimeException("Failed to establish bidirectional connection", e);
                }
            }

            return topology.getNeighbors();
        } catch (Exception e) {
            log.error("Failed to join topology", e);
            throw new RuntimeException("Failed to join topology", e);
        }
    }

    public String leaveTopology() {
        try {
            synchronized (this) {
                // Check if we're already disconnected
                if (topology.getNeighbors().isEmpty()) {
                    return "Node " + myId + " is already disconnected";
                }

                // Copy the neighbors map to avoid concurrent modification
                Map<String, Node> currentNeighbors = new HashMap<>(topology.getNeighbors());
                NodeInterface newRemoteNodeImpl=null;
                for (Map.Entry<String, Node> entry : currentNeighbors.entrySet()) {
                    String remoteId = entry.getKey();
                    Node remoteNode = entry.getValue();
                    try {
                        NodeInterface remoteNodeImpl = getRemoteNodeImpl(remoteNode.getHost(), remoteNode.getPort());
                        if (remoteNodeImpl.checkStatus() && newRemoteNodeImpl==null){
                            newRemoteNodeImpl=remoteNodeImpl;
                            remoteNodeImpl.removeNeighbor(myId);
                            continue;
                        }
                        assert newRemoteNodeImpl != null;
                        newRemoteNodeImpl.joinTopology(remoteNode.getHost(), remoteNode.getPort());
                        remoteNodeImpl.removeNeighbor(myId);


                    } catch (Exception e) {
                        log.warn("Failed to notify node {} about departure", remoteId, e);
                    }
                }

                topology.clear();
                return "Successfully removed Node " + myId;
            }
        } catch (Exception e) {
            log.error("Failed to leave topology", e);
            throw new RuntimeException("Failed to leave topology", e);
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

    @Override
    public String getParentId() throws RemoteException {
        return this.topology.getParentId();
    }

    private NodeInterface getRemoteNodeImpl(String host, int port) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        return (NodeInterface) registry.lookup("NodeImpl");
    }

    public void addNeighbor(String id, Node node) {
        topology.addNeighbor(id, node);
    }

    public void removeNeighbor(String id) {
        topology.removeNeighbor(id);
    }

    
    public void updateParentId(String id) throws RemoteException {
        topology.setParentId(id);
    }

    
    public Map<String, Node> getNeighbors() throws RemoteException {
        return this.topology.getNeighbors();
    }
}