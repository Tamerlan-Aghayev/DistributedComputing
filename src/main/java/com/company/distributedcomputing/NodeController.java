package com.company.distributedcomputing;

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
    private final NodeImpl nodeImpl;
    @PostMapping("/join/{host}/{port}")
    public ResponseEntity<Map<String,Node>> join(@PathVariable("host") String host, @PathVariable("port") int port){
        return ResponseEntity.ok(nodeImpl.joinTopology(host,port));
    }

    @PostMapping("/leave")
    public ResponseEntity<String> leave(){
        return ResponseEntity.ok(nodeImpl.leaveTopology());
    }
    @PostMapping("/kill")
    public ResponseEntity<String> kill(){
        return ResponseEntity.ok(nodeImpl.kill());
    }
    @PostMapping("/revive")
    public ResponseEntity<String> revive(){
        return ResponseEntity.ok(nodeImpl.revive());
    }
    @PostMapping("/setDelay/{delay}")
    public ResponseEntity<String> revive(@PathVariable("delay") int delay){
        return ResponseEntity.ok(nodeImpl.setDelay(delay));
    }

    @PostMapping("/startWork/{work}")
    public void startWork(@PathVariable("work") int work) throws RemoteException, NotBoundException {
        nodeImpl.receiveWork(work, null, new WorkContext(nodeImpl.getMyId(), work));
    }

    @GetMapping("/neighbors")
    public Map<String, Node> getNeighbors() throws RemoteException {
        return nodeImpl.getNeighbors();
    }
}
