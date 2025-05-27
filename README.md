# spring-boot-chat

## Architecture

```mermaid
graph TD
    subgraph "Cluster"
        subgraph "Node 1"
            WSH1[ChatWebSocketHandler]
            UA1_1[UserActor]
        end

        subgraph "Node 2"
            WSH2[ChatWebSocketHandler]
            UA2_1[UserActor]
        end

        subgraph "Node 3"
            WSH3[ChatWebSocketHandler]
            UA3_1[UserActor]
        end

        CRA[ChatRoomActor]
    end

    %% Relationships
    WS1 <-->|"1:1"| WSH1
    WS2 <-->|"1:1"| WSH2
    WS3 <-->|"1:1"| WSH3

    WSH1 -->|"creates 1:1"| UA1_1
    WSH2 -->|"creates 1:1"| UA2_1
    WSH3 -->|"creates 1:1"| UA3_1

    UA1_1 -->|"N:1"| CRA
    UA2_1 -->|"N:1"| CRA
    UA3_1 -->|"N:1"| CRA

    %% Styling
    classDef local fill:#f9f,stroke:#333,stroke-width:2px;
    classDef cluster fill:#bbf,stroke:#333,stroke-width:2px;
    class UA1_1,UA1_2,UA2_1,UA2_2,UA3_1,UA3_2 local;
    class CRA cluster;
```

## Key Characteristics

- **WebSocket <-> UserActor**: 1:1 relationship
- **UserActor <-> ChatRoomActor**: N:1 relationship
- **UserActor**: Local actor that exists only on the node where the WebSocket connection is established
- **ChatRoomActor**: Sharded entity that lives across the cluster

## Usage

```shell
# start cluster 
$ sh cluster-start.sh 8080 2551 3

# stop cluster 
$ sh cluster-stop.sh   
```
