# Dijkstra-Scholten Algorithm using Java RMI

## Overview
This project implements the **Dijkstra-Scholten Algorithm** for detecting the termination of a distributed computation using **Java RMI (Remote Method Invocation)**. The system consists of multiple distributed nodes running across three virtual machines, where nodes can dynamically join, leave, fail, and revive while ensuring correct termination detection.

## Features
- **Distributed termination detection** using the Dijkstra-Scholten algorithm.
- **Dynamic topology management**, allowing nodes to join and leave at runtime.
- **Fault tolerance** with simulated node failures and recovery.
- **REST API for controlling the distributed system**, including work initiation, node management, and delay simulation.
- **Support for cyclic work distribution**, ensuring the algorithm handles looped task assignments.

## System Architecture
The system runs on three virtual machines (VMs), each hosting a set of distributed nodes:
- **VM 1**: 2 nodes
- **VM 2**: 2 nodes
- **VM 3**: 1 node

Nodes communicate with each other using Java RMI to coordinate work distribution and termination detection.

## Installation & Setup
### Prerequisites
- Java Development Kit (JDK 11 or higher)
- Gradle
- A REST API server (Spring Boot preferred)

### Steps to Run the Project
1. **Clone the repository**
   ```sh
   git clone https://github.com/your-repo/dijkstra-scholten-rmi.git
   cd dijkstra-scholten-rmi
   ```
2. **Build the project**
   
3. **Start the project**
  
4. **Launch nodes on each VM**
   Repeat for each VM and node, specifying the correct VM and node ID.


## REST API Endpoints
| Endpoint      | Description |
|--------------|-------------|
| **POST /startWork** | Starts the distributed computation |
| **POST /join** | Adds a new node to the topology |
| **POST /leave** | Removes a node cleanly from the topology |
| **POST /kill** | Simulates an abrupt node failure |
| **POST /revive** | Restores a failed node |
| **POST /setDelay** | Simulates network latency |

## Testing Scenarios
### 1. Node Removal and Addition
- Start with 5 nodes, remove nodes one by one, testing system stability.
- Reintroduce nodes dynamically and check for proper reintegration.

### 2. Message Delay Simulation
- Use the `setDelay` API to introduce communication delays.
- Verify the system's ability to detect termination despite network delays.

## Work Distribution Strategy
- Nodes distribute work dynamically, forming **cyclic dependencies**.
- If a node completes its work, it queries its neighbors for more tasks.
- The Dijkstra-Scholten algorithm ensures termination is detected correctly, even in cyclic topologies.

## Fault Tolerance & Recovery
- Nodes can **fail abruptly** (simulated using `kill` API) and recover (`revive` API).
- The system ensures **topology self-repair**, rerouting work assignments when failures occur.

## Conclusion
This project successfully implements the **Dijkstra-Scholten termination detection algorithm** in a distributed system using **Java RMI**. It provides a robust and fault-tolerant framework for managing dynamic distributed computations, making it suitable for real-world applications requiring **dynamic node participation, fault recovery, and termination detection**.

## Future Enhancements
- Implement **leader election** for better fault recovery.
- Introduce **persistent storage** for state recovery after crashes.
- Improve **load balancing** to optimize work distribution.

---
**Author:** Tamerlan Aghayev  

