# Dijkstra-Scholten Algorithm using Java RMI

## Objective
Implement the **Dijkstra-Scholten algorithm** for detecting the termination of a distributed computation using **Java RMI**. The implementation must support specific functionality, testing scenarios, and requirements outlined below.

---

## Requirements

### 1. Virtual Machine and Node Configuration
- The algorithm should run on **three virtual machines (VMs)**:
    - VM 1: **2 nodes**
    - VM 2: **2 nodes**
    - VM 3: **1 node**

### 2. Dijkstra-Scholten Algorithm
- Detect distributed waiting across nodes in a distributed computation.
- Ensure the algorithm handles **node crashes** and allows **nodes to join or leave** dynamically.

### 3. REST API Functions
Provide the following REST API functions:

#### Core API:
1. **startWork**: Initiates the distributed computation.
2. **join**: Adds a node to the topology.
3. **leave**: Disconnects a node from the topology cleanly.
4. **kill**: Simulates an abrupt node failure (cuts off communication).
5. **revive**: Restores communication for a previously failed node.

#### Testing API:
1. **setDelay**: Adds a delay to simulate slower communication channels.

---

## Topology Considerations
- Topologies may include **loops or cycles** (e.g., a node assigning work to itself transitively).
- Nodes must implement a **mechanism to obtain additional work** when finished by querying neighbors.

### Work Distribution
1. Enable cycles: A node may assign work to another node, which in turn assigns it back to the first node.
2. Work query mechanism: When a node finishes its assigned task, it queries **x neighbors** to check for additional work.

---

## Testing Scenarios

### Scenario 1: Node Removal and Addition
1. Start with a fully functional system (**5+ nodes**).
2. Remove nodes **one by one**, testing functionality after each removal until only one node remains.
3. Gradually **add nodes back** one at a time, testing functionality after each addition until the system has 5 nodes again.

### Scenario 2: Message Delays
1. Simulate a **slower communication channel** by introducing delays in message delivery.
2. Use the **setDelay** API to apply and reset delays dynamically during the computation.

---

## Implementation Details

### 1. Message Delay
- Implement a mechanism to introduce delays (e.g., a `sleep` before sending messages).
- Use the **setDelay** API to configure delays.

### 2. Work Distribution
- Implement an algorithm capable of dividing a given task among multiple nodes.
- Example: Solving optimization problems or dividing a search space.

### 3. Dijkstra-Scholten Algorithm Functional Tests
#### a. Work Distribution and Cycle Creation
- Ensure the algorithm handles cycles (e.g., a node transitively assigning work to itself).
- Verify termination detection in such scenarios.

#### b. Work Query Implementation
- When a node completes its task, it must query **neighbors** for additional work.
- Ensure proper communication between nodes for work delegation and termination.

---

## API Summary

### REST API Functions

| API Name       | Description                                                |
|----------------|------------------------------------------------------------|
| **startWork**  | Initial impulse to start the distributed computation.      |
| **join**       | Adds a new node to the topology.                           |
| **leave**      | Removes a node cleanly from the topology.                  |
| **kill**       | Simulates abrupt failure of a node (cutting off communication). |
| **revive**     | Restores communication for a previously failed node.       |
| **setDelay**   | Configures a delay for message delivery.                   |

---

## Testing Checklist
1. **Message Delay Simulation**:
    - Verify message delays are applied and removed correctly.
    - Test communication under simulated slower channels.
2. **Node Removal and Addition**:
    - Remove nodes one by one and validate functionality.
    - Add nodes back dynamically and validate functionality.
3. **Work Distribution**:
    - Ensure cycles are created in the topology.
    - Verify nodes can query neighbors for additional work upon task completion.

---

## Notes
- The implementation must handle **node crashes** gracefully.
- Testing must include scenarios with both functional and faulty nodes.



# Message Delay and Distributed Work Testing

## Message Delay (Simulating a Slow Communication Channel)

### Requirements:
A delay should be introduced before sending a message to simulate a slower communication channel.

### REST API function:
- **setDelay** – Sets the communication delay.
    - Usage scenario: Set the delay when sending a message, then send the message to delay. After that, set the delay back to `0`.

---

## Task Distribution and Algorithm Preparation

### Functionality:
You need to prepare a function (or another algorithm) to test whether it has finished. This algorithm should be able to split the given task across multiple nodes (e.g., optimization problems – splitting the search space).

#### Suggested Approach: Distributed Waiting
- **Input**: Time for which the nodes will collectively wait.
- **Goal**: Split the waiting task across multiple nodes to simulate a distributed computation model.

#### Key Features:
- **Cycle Creation**: The task distribution should allow the creation of cycles where a node assigns work to another node, which assigns it to another, and so on, creating a cycle back to the first node.
- **Work Request Mechanism**: When a node finishes, it should request more work from its neighbors if they have any available tasks.

---

## Dijkstra-Scholten Algorithm

### What We Are Testing:
1. **Cycle Creation**: Verify that a node can transitively give work to itself.
2. **Work Query**: When a node finishes, it should ask several of its neighbors if they have work for it.

### REST API function:
- **startWork** – The initial impulse to start the computation.

---

## Topological Maintenance

### Goal:
Understand that the topology required for implementing one algorithm may not be the same as the one used for the entire system. This often involves an overlay/virtual topology.

---

## Fault Recovery Mechanism

### Goal:
Implement a mechanism to fix the topology in case of the failure of a single node. (For testing purposes, failure will involve only one node at a time.)
