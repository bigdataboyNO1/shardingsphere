/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.mode.manager.cluster.coordinator.registry.metadata.subscriber;

import com.google.common.eventbus.Subscribe;
import org.apache.shardingsphere.infra.util.eventbus.EventSubscriber;
import org.apache.shardingsphere.metadata.persist.data.ShardingSphereDataPersistService;
import org.apache.shardingsphere.mode.manager.cluster.coordinator.registry.data.event.ShardingSphereSchemaDataAlteredEvent;
import org.apache.shardingsphere.mode.repository.cluster.ClusterPersistRepository;

/**
 * ShardingSphere schema data registry subscriber.
 */
public final class ShardingSphereSchemaDataRegistrySubscriber implements EventSubscriber {
    
    private final ShardingSphereDataPersistService persistService;
    
    public ShardingSphereSchemaDataRegistrySubscriber(final ClusterPersistRepository repository) {
        persistService = new ShardingSphereDataPersistService(repository);
    }
    
    /**
     * Update when ShardingSphere schema data altered.
     *
     * @param event schema altered event
     */
    @Subscribe
    public void update(final ShardingSphereSchemaDataAlteredEvent event) {
        String databaseName = event.getDatabaseName();
        String schemaName = event.getSchemaName();
        persistService.getTableRowDataPersistService().persist(databaseName, schemaName, event.getTableName(), event.getAddedRows());
        persistService.getTableRowDataPersistService().persist(databaseName, schemaName, event.getTableName(), event.getUpdatedRows());
        persistService.getTableRowDataPersistService().delete(databaseName, schemaName, event.getTableName(), event.getDeletedRows());
    }
}
