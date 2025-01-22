package com.company.distributedcomputing.controller;

import com.company.distributedcomputing.model.Node;
import com.company.distributedcomputing.service.NodeInterface;
import com.company.distributedcomputing.service.impl.NodeImpl;
import com.company.distributedcomputing.model.WorkContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NodeController {
    private final NodeInterface nodeImpl;
    @PostMapping("/join/{host}/{port}")
    public ResponseEntity<Map<String, Node>> join(@PathVariable("host") String host, @PathVariable("port") int port) throws RemoteException {
        return ResponseEntity.ok(nodeImpl.joinTopology(host,port));
    }

    @PostMapping("/leave")
    public ResponseEntity<String> leave() throws RemoteException {
        return ResponseEntity.ok(nodeImpl.leaveTopology());
    }
    @PostMapping("/kill")
    public ResponseEntity<String> kill() throws NotBoundException, RemoteException {
        return ResponseEntity.ok(nodeImpl.kill());
    }
    @PostMapping("/revive")
    public ResponseEntity<String> revive() throws NotBoundException, RemoteException {
        return ResponseEntity.ok(nodeImpl.revive());
    }
    @PostMapping("/setDelay/{delay}")
    public ResponseEntity<String> revive(@PathVariable("delay") int delay) throws RemoteException {
        return ResponseEntity.ok(nodeImpl.setDelay(delay));
    }

    @PostMapping("/startWork/{work}")
    public ResponseEntity<String> startWork(@PathVariable("work") int work) throws RemoteException, NotBoundException {
        long startTime = System.nanoTime();
        nodeImpl.receiveWork(work, null, new WorkContext(nodeImpl.getMyId(), work));
        long endTime = System.nanoTime();
        long latencyInMillis = (endTime - startTime) / 1_000_000_000;

        return ResponseEntity.ok("Time to finish the process: "+ latencyInMillis+ " second(s)");
    }

    @GetMapping("/neighbors")
    public Map<String, Node> getNeighbors() throws RemoteException {
        return nodeImpl.getNeighbors();
    }
}
