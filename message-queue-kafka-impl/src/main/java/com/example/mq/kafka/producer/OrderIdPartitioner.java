package com.example.mq.kafka.producer;

import org.apache.kafka.clients.producer.RoundRobinPartitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;

import java.util.List;

public class OrderIdPartitioner extends RoundRobinPartitioner {

    @Override
    public int partition(String topic,
                         Object key,
                         byte[] keyBytes,
                         Object value,
                         byte[] valueBytes,
                         Cluster cluster) {
        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();
        // 按订单ID哈希值分配分区
        return Math.abs((topic + "::" + key).hashCode()) % numPartitions;
    }

}
