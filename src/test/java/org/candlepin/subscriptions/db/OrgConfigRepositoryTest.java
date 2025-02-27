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
package org.candlepin.subscriptions.db;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class OrgConfigRepositoryTest {

  @Autowired private OrgConfigRepository repository;
  private ApplicationClock clock = new FixedClockConfiguration().fixedClock();

  @BeforeAll
  void cleanUpDatabase() {
    repository.deleteAll();
  }

  @Test
  void saveAndUpdate() {
    OffsetDateTime creation = clock.now();
    OffsetDateTime expectedUpdate = creation.plusDays(1);

    String org = "test-org";
    OrgConfig config = new OrgConfig(org);
    config.setOptInType(OptInType.JMX);
    config.setSyncEnabled(true);
    config.setCreated(creation);
    config.setUpdated(expectedUpdate);

    repository.saveAndFlush(config);

    OrgConfig found = repository.getOne(org);
    assertNotNull(found);
    assertEquals(config, found);

    found.setSyncEnabled(false);
    found.setOptInType(OptInType.API);
    repository.saveAndFlush(found);

    OrgConfig updated = repository.getOne(org);
    assertNotNull(updated);
    assertEquals(Boolean.FALSE, updated.getSyncEnabled());
    assertEquals(OptInType.API, updated.getOptInType());
  }

  @Test
  void testDelete() {
    OrgConfig config = createConfig("an-org", true);
    repository.saveAndFlush(config);

    OrgConfig toDelete = repository.getOne(config.getOrgId());
    assertNotNull(toDelete);
    repository.delete(toDelete);
    repository.flush();

    assertTrue(repository.findById(config.getOrgId()).isEmpty());
  }

  @Test
  void testFindOrgsWithEnabledSync() {
    repository.saveAll(
        Arrays.asList(
            createConfig("Org1", true),
            createConfig("Org2", true),
            createConfig("Org3", false),
            createConfig("Org4", false)));
    repository.flush();

    List<String> orgsWithSync = repository.findSyncEnabledOrgs().collect(Collectors.toList());
    assertEquals(2, orgsWithSync.size());
    assertTrue(orgsWithSync.containsAll(Arrays.asList("Org1", "Org2")));
  }

  @Test
  void existsByOrgId() {
    repository.saveAll(Arrays.asList(createConfig("Org1", true)));
    repository.flush();
    assertTrue(repository.existsByOrgId("Org1"));
    assertFalse(repository.existsByOrgId("Not_Found"));
    assertFalse(repository.existsByOrgId(null));
  }

  private OrgConfig createConfig(String org, boolean canSync) {
    OrgConfig config = new OrgConfig(org);
    config.setOptInType(OptInType.API);
    config.setSyncEnabled(canSync);
    config.setCreated(clock.now());
    config.setUpdated(config.getCreated().plusDays(1));
    return config;
  }
}
