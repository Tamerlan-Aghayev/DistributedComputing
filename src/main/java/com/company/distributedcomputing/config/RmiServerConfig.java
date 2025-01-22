package com.company.distributedcomputing.config;

import com.company.distributedcomputing.service.impl.NodeImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class RmiServerConfig {
    @Value("${node.rmi.port}")
    private int rmiPort;

    @Value("${node.rmi.host")
    private String rmiHost;

    private final NodeImpl nodeImpl;

    @Bean
    public Registry rmiRegistry() throws Exception {
        System.setProperty("java.rmi.server.hostname",rmiHost);
        Registry registry = LocateRegistry.createRegistry(rmiPort);
        registry.rebind("NodeImpl", nodeImpl);
        String[] boundNames = registry.list();
        System.out.println("Bound names: " + Arrays.toString(boundNames));

        System.out.println("RMI Server started on port " + rmiPort);
        return registry;
    }
}