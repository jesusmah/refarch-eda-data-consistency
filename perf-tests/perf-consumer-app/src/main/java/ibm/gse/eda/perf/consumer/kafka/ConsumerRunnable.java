package ibm.gse.eda.perf.consumer.kafka;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import ibm.gse.eda.perf.consumer.app.Message;

import static java.lang.Math.max;
import static java.lang.Math.min;
/**
 * Consume kafka message until the process is stopped or issue on communication with the broker
 */
@ApplicationScoped
public class ConsumerRunnable implements Runnable {
    private static final Logger logger = Logger.getLogger(ConsumerRunnable.class.getName());
 
    private KafkaConsumer<String, String> kafkaConsumer = null;
    private boolean running = true;
    @Inject
    private KafkaConfiguration kafkaConfiguration;

    private Collection<Message> messages;

    private  long maxLatency = 0;
    private  long minLatency = Integer.MAX_VALUE;
    private  long lagSum = 0;
    private  int count = 0;
    private  long averageLatency = 0;

    public ConsumerRunnable() {
    }

    private void init(){
        kafkaConsumer = new KafkaConsumer<>(getConfig().getConsumerProperties());
        kafkaConsumer.subscribe(Collections.singletonList(getConfig().getMainTopicName()),
            new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                }

                @Override
                public void onPartitionsAssigned(Collection<org.apache.kafka.common.TopicPartition> partitions) {
                    try {
                        logger.log(Level.WARNING, "Partitions " + partitions + " assigned, consumer seeking to end.");

                        for (TopicPartition partition : partitions) {
                            long position = kafkaConsumer.position(partition);
                            logger.log(Level.WARNING, "current Position: " + position);

                            logger.log(Level.WARNING, "Seeking to end...");
                            kafkaConsumer.seekToEnd(Arrays.asList(partition));
                            logger.log(Level.WARNING,
                                    "Seek from the current position: " + kafkaConsumer.position(partition));
                            kafkaConsumer.seek(partition, position);
                        }
                        logger.log(Level.WARNING, "Producer can now begin producing messages.");
                    } catch (final Exception e) {
                        logger.log(Level.SEVERE,"Error when assigning partitions" + e.getMessage());
                    }
                }
        });
    }

    @Override
    public void run() {
        logger.log(Level.SEVERE,"Start runnable");
        init();
        int i = 0;
        while(running) {
            try {
                ConsumerRecords<String, String> records = kafkaConsumer.poll(kafkaConfiguration.getPollTimeOut());
                for (ConsumerRecord<String, String> record : records) {
                    logger.log(Level.SEVERE, "Consumer Record - key: " 
                            + record.key() 
                            + " timestamp: "
                            + record.timestamp()
                            + " value: " 
                            + record.value() 
                            + " partition: " 
                            + record.partition() 
                            + " offset: " + record.offset() + "\n");
                    // TODO assess when to use payload timestamp to compute lag
                    Date date = new Date();
                    long now = date.getTime();
                    long difference = now - record.timestamp();
                    maxLatency = max(maxLatency, difference);
                    minLatency = min(minLatency, difference);
                    lagSum += difference;
                    count = count +1;
                    averageLatency = lagSum / count;
                    logger.log(Level.SEVERE,Long.toString(averageLatency) + " " + Long.toString(minLatency));
                }
                logger.log(Level.SEVERE, "in thread");
            } catch (final Exception e) {
                logger.log(Level.SEVERE, "Consumer loop has been unexpectedly interrupted");
                stop();
            }
            if (getConfig().getCommit()) {
                logger.log(Level.SEVERE, "Consumer auto commit");
                kafkaConsumer.commitSync();
            }
        } //loop

    }

    public void stop(){
        logger.log(Level.INFO, "Stop consumer");
        running = false;
    }

    public void reStart(){
        logger.log(Level.INFO, "ReStart consumer");
        running = true;
    }

    public boolean isRunning(){
        return running;
    }

    private KafkaConfiguration getConfig(){
        return kafkaConfiguration;
    }

	public Collection<Message> getMessages() {
		return messages;
	}

    public long getAverageLatency() {
		return this.averageLatency;
    }
    
    public long getMinLatency() {
		return this.minLatency;
	}

    public long getMaxLatency() {
		return this.maxLatency;
	}



}