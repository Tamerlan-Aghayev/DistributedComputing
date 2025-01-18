//package com.company.distributedcomputing;
//
//import org.springframework.stereotype.Component;
//import org.yaml.snakeyaml.DumperOptions;
//import org.yaml.snakeyaml.Yaml;
//import java.io.*;
//import java.util.LinkedHashMap;
//import java.util.Map;
//import java.util.Set;
//
//@Component
//public class YamlManager {
//    private final String yamlPath = "src/main/resources/application.yaml";
//    private Map<String, Object> yamlMap;
//
//    public YamlManager() {
//        loadYaml();
//    }
//
//    private void loadYaml() {
//        try (InputStream input = new FileInputStream(yamlPath)) {
//            yamlMap = new Yaml().load(input);
//        } catch (IOException e) {
//            throw new RuntimeException("Cannot load YAML file", e);
//        }
//    }
//
//    public Map<String, Object> getNodeRmiConfig(String nodeId) {
//        Map<String, Object> allNodes = (Map<String, Object>) yamlMap.get("all-nodes");
//
//        if (allNodes == null || !allNodes.containsKey(nodeId)) {
//            throw new RuntimeException("Node " + nodeId + " not found in 'all-nodes'");
//        }
//
//        Map<String, Object> nodeConfig = (Map<String, Object>) allNodes.get(nodeId);
//        Map<String, Object> rmiConfig = (Map<String, Object>) nodeConfig.get("rmi");
//
//        if (rmiConfig == null) {
//            throw new RuntimeException("RMI configuration not found for node " + nodeId);
//        }
//
//        return rmiConfig;
//    }
//
//    public Set<String> getAllNodeIds() {
//        Map<String, Object> allNodes = (Map<String, Object>) yamlMap.get("all-nodes");
//
//        if (allNodes == null) {
//            throw new RuntimeException("'all-nodes' section not found in YAML");
//        }
//
//        return allNodes.keySet();
//    }
//
//    private void saveYaml() {
//        DumperOptions options = new DumperOptions();
//        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
//
//        try (FileWriter writer = new FileWriter(yamlPath)) {
//            new Yaml(options).dump(yamlMap, writer);
//        } catch (IOException e) {
//            throw new RuntimeException("Cannot save YAML file", e);
//        }
//    }
//}
