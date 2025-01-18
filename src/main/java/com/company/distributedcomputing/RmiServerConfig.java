package com.company.distributedcomputing;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

@Configuration
@RequiredArgsConstructor
public class RmiServerConfig {
    @Value("${node.rmi.port}")
    private int rmiPort;

    @Bean
    public Registry rmiRegistry(NodeImpl nodeImpl) throws Exception {
        Registry registry = LocateRegistry.createRegistry(rmiPort);
        registry.rebind("NodeImpl", nodeImpl);
        System.out.println("RMI Server started on port " + rmiPort);
        return registry;
    }
}