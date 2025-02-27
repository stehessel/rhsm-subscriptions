/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.rhmarketplace;

import io.micrometer.core.annotation.Timed;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/** Worker that maps tally summaries and submits them to Marketplace. */
@Slf4j
@Service
public class RhMarketplaceWorker extends SeekableKafkaConsumer {

  private final RhMarketplaceProducer producer;
  private final RhMarketplacePayloadMapper rhMarketplacePayloadMapper;
  @Getter private final boolean enabled;

  @Autowired
  public RhMarketplaceWorker(
      @Qualifier("rhMarketplaceTasks") TaskQueueProperties taskQueueProperties,
      RhMarketplaceProducer producer,
      RhMarketplacePayloadMapper rhMarketplacePayloadMapper,
      KafkaConsumerRegistry kafkaConsumerRegistry) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.producer = producer;
    this.rhMarketplacePayloadMapper = rhMarketplacePayloadMapper;
    this.enabled = taskQueueProperties.isEnabled();
  }

  @Timed("rhsm-subscriptions.marketplace.tally-summary")
  @KafkaListener(
      id = "#{__listener.groupId}",
      autoStartup = "#{__listener.enabled}",
      topics = "#{__listener.topic}",
      containerFactory = "kafkaTallySummaryListenerContainerFactory")
  public void receive(TallySummary tallySummary) {
    log.debug("Tally Summary received by RHM for account {}!", tallySummary.getAccountNumber());
    rhMarketplacePayloadMapper
        .createUsageRequests(tallySummary)
        .forEach(producer::submitUsageRequest);
  }
}
