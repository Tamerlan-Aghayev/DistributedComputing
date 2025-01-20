package com.company.distributedcomputing;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final ConcurrentHashMap<String, Phaser> completionLatches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Node> helperInvitations = new ConcurrentHashMap<>();
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


    public synchronized void receiveWork(int workAmount, String senderId, WorkContext context) throws RemoteException, NotBoundException {
        if (!isActive.get()) {
            throw new RemoteException("Node " + myId + " is not active");
        }

        // Initialize work tracking for this context
        activeContexts.put(context.getContextId(), context);
        pendingAcks.put(context.getContextId(), ConcurrentHashMap.newKeySet());

        // Update parent if this is new work
        if (topology.getParentId() == null && senderId != null) {
            topology.setParentId(senderId);
        }

        try {
            work.set(workAmount);
            distributeWork(context);
        } catch (Exception e) {
            log.error("Error processing work", e);
        }
        offerHelp();
    }

    private void distributeWork(WorkContext context) throws RemoteException, NotBoundException {
        log.info("Worker {} started distributing. Context ID: {}, Work Remaining: {}", myId, context.getContextId(), work.get());

        if (work.get() <= 0) {
            log.info("Worker {} has no work left to distribute. Sending acknowledgment for Context ID: {}", myId, context.getContextId());
            sendAcknowledgment(context);
            return;
        }

        log.info("Worker {} fetching available workers.", myId);
        Map<String, Node> availableWorkers = new ConcurrentHashMap<>(topology.getNeighbors());

        availableWorkers.remove(context.getRootId());
        if(getParentId()!=null)
            availableWorkers.remove(getParentId());

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


        log.info("Distributing work among {} workers. Total work: {}", remoteWorkers.size(), work.get());

        int totalWork = work.get();
        int numWorkers = remoteWorkers.size();
        int baseWork = numWorkers > 0 ? totalWork / (numWorkers + 1) : totalWork; // Reserve work for self
        int extraWork = numWorkers > 0 ? totalWork % (numWorkers + 1) : 0;

        log.debug("Work distribution calculated. Base work: {}, Extra work: {}", baseWork, extraWork);
        Phaser phaser = new Phaser(Math.max(1,numWorkers));
        completionLatches.put(context.getContextId(), phaser);
        log.info("Created CountDownLatch for Context ID: {} with count: {}", context.getContextId(), numWorkers);

        List<CompletableFuture<Void>> distributionFutures = new ArrayList<>();
        AtomicInteger assigned = new AtomicInteger(0);

        // Distribute work in parallel
        remoteWorkers.forEach((workerId, workerNode) -> {
            int workerShare = baseWork + (assigned.getAndIncrement() < extraWork ? 1 : 0);

            if (workerShare > 0) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        log.info("Assigning {} units of work to worker {}. Remaining work before assignment: {}",
                                workerShare, workerId, work.get());
                        pendingAcks.get(context.getContextId()).add(workerId);

                        WorkContext workerContext = new WorkContext(context, context.getDepth() + 1);
                        log.debug("Sending work to worker {}. Context ID: {}, Parent ID: {}, Depth: {}",
                                workerId, workerContext.getContextId(), myId, workerContext.getDepth());
                        work.addAndGet(-workerShare);
                        workerNode.receiveWork(workerShare, myId, workerContext);

                    } catch (Exception e) {
                        log.error("Failed to distribute work to worker {}. Reducing latch count for Context ID: {}",
                                workerId, context.getContextId(), e);
                        phaser.arriveAndDeregister();
                    }
                }, executorService);

                distributionFutures.add(future);
            } else {
                log.debug("No work assigned to worker {} as calculated share is zero.", workerId);
            }
        });

//        // Wait for all distributions to complete
//        CompletableFuture.allOf(distributionFutures.toArray(new CompletableFuture[0])).join();

        log.info("Worker {} finished distributing. Remaining work: {}", myId, work.get());

        doWork(context, distributionFutures, phaser);

        log.info("Worker {} completed its own work. Awaiting completion of distributed work for Context ID: {}",
                myId, context.getContextId());

        try {
            if (numWorkers == 0) {
                phaser.arriveAndDeregister();
            }
            phaser.awaitAdvance(phaser.getPhase());
            log.info("All workers have completed their work. Sending acknowledgment for Context ID: {}",
                    context.getContextId());
            sendAcknowledgment(context);

            this.topology.setParentId(null);
            log.info("Parent ID reset for worker {}", myId);

        } catch (Exception e) {
            log.error("Worker {} interrupted while waiting for latch countdown. Context ID: {}",
                    myId, context.getContextId(), e);
            Thread.currentThread().interrupt();
        }
    }


    private void doWork(WorkContext workContext, List<CompletableFuture<Void>> distributionFutures, Phaser phaser) throws NotBoundException, RemoteException {
        System.out.println("Worker " + myId + " started doing");
        while (work.get() > 0) {
            if (!isActive.get()) {
                break;
            }
            if (!helperInvitations.isEmpty()){
                receiveHelp(workContext, distributionFutures, phaser);
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
            Phaser phaser = completionLatches.get(contextId);
            if (phaser != null) {
                phaser.arriveAndDeregister();
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
        try {
            Thread.sleep(delay.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    public void receiveHelp(WorkContext context, List<CompletableFuture<Void>> distributionFutures, Phaser phaser) throws NotBoundException, RemoteException {
        for (String helperId: helperInvitations.keySet()) {
            Node helper = helperInvitations.get(helperId);
            NodeInterface helperImpl = getRemoteNodeImpl(helper.getHost(), helper.getPort());
            int workerShare = work.get() / 2;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    log.info("Assigning {} units of work to helper {}. Remaining work before assignment: {}",
                            work, helperId, work.get());
                    pendingAcks.get(context.getContextId()).add(helperId);

                    WorkContext workerContext = new WorkContext(context, context.getDepth() + 1);
                    log.debug("Sending work to helper {}. Context ID: {}, Parent ID: {}, Depth: {}",
                            helperId, workerContext.getContextId(), myId, workerContext.getDepth());

                    helperImpl.receiveWork(workerShare, myId, workerContext);
                    work.addAndGet(-workerShare);
                    log.info("Work successfully sent to helper {}. Remaining work after assignment: {}",
                            helperId, work.get());
                } catch (Exception e) {
                    log.error("Failed to distribute work to worker {}. Reducing latch count for Context ID: {}",
                            helperId, context.getContextId(), e);
                    phaser.arriveAndDeregister();
                }
            }, executorService);
            work.getAndUpdate(currentWork -> currentWork - workerShare);
            distributionFutures.add(future);
            helperInvitations.remove(helperId);
        }
        // Wait for all distributions to complete
        CompletableFuture.allOf(distributionFutures.toArray(new CompletableFuture[0])).join();

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

    public int getWork(){
        return this.work.get();
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

    public void addToInvitation(String id, Node node){
        this.helperInvitations.put(id, node);
    }
    public void offerHelp() throws NotBoundException, RemoteException {
        NodeInterface biggestNode=null;
        int max=0;
        Map<String, Node> available=this.topology.getNeighbors();
        available.remove(getMyId());
        for (String id: available.keySet()){
            Node neighbor=topology.getNeighbor(id);
            NodeInterface remoteNeighbor=getRemoteNodeImpl(neighbor.getHost(), neighbor.getPort());
            if (remoteNeighbor.getWork()>max && remoteNeighbor.checkStatus()){
                max=remoteNeighbor.getWork();
                biggestNode=remoteNeighbor;
            }
        }
        if (max>4) biggestNode.addToInvitation(getMyId(), getMyNode());
    }
}