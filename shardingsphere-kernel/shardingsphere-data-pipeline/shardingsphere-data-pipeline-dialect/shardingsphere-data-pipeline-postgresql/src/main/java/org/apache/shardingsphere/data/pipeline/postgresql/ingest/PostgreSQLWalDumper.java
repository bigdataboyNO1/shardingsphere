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

package org.apache.shardingsphere.data.pipeline.postgresql.ingest;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.data.pipeline.api.config.ingest.DumperConfiguration;
import org.apache.shardingsphere.data.pipeline.api.executor.AbstractLifecycleExecutor;
import org.apache.shardingsphere.data.pipeline.api.ingest.channel.Channel;
import org.apache.shardingsphere.data.pipeline.api.ingest.position.IngestPosition;
import org.apache.shardingsphere.data.pipeline.api.ingest.record.Record;
import org.apache.shardingsphere.data.pipeline.core.ingest.exception.IngestException;
import org.apache.shardingsphere.data.pipeline.core.util.ThreadUtil;
import org.apache.shardingsphere.data.pipeline.postgresql.ingest.wal.LogicalReplication;
import org.apache.shardingsphere.data.pipeline.postgresql.ingest.wal.WalEventConverter;
import org.apache.shardingsphere.data.pipeline.postgresql.ingest.wal.WalPosition;
import org.apache.shardingsphere.data.pipeline.postgresql.ingest.wal.decode.DecodingPlugin;
import org.apache.shardingsphere.data.pipeline.postgresql.ingest.wal.decode.PostgreSQLLogSequenceNumber;
import org.apache.shardingsphere.data.pipeline.postgresql.ingest.wal.decode.PostgreSQLTimestampUtils;
import org.apache.shardingsphere.data.pipeline.postgresql.ingest.wal.decode.TestDecodingPlugin;
import org.apache.shardingsphere.data.pipeline.postgresql.ingest.wal.event.AbstractWalEvent;
import org.apache.shardingsphere.data.pipeline.postgresql.ingest.wal.event.PlaceholderEvent;
import org.apache.shardingsphere.data.pipeline.spi.ingest.dumper.IncrementalDumper;
import org.apache.shardingsphere.infra.config.datasource.jdbc.config.impl.StandardJDBCDataSourceConfiguration;
import org.postgresql.jdbc.PgConnection;
import org.postgresql.replication.PGReplicationStream;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * PostgreSQL WAL dumper.
 */
@Slf4j
public final class PostgreSQLWalDumper extends AbstractLifecycleExecutor implements IncrementalDumper {
    
    private final WalPosition walPosition;
    
    private final DumperConfiguration dumperConfig;
    
    private final LogicalReplication logicalReplication = new LogicalReplication();
    
    private final WalEventConverter walEventConverter;
    
    @Setter
    private Channel channel;
    
    public PostgreSQLWalDumper(final DumperConfiguration dumperConfig, final IngestPosition<WalPosition> position) {
        walPosition = (WalPosition) position;
        if (!StandardJDBCDataSourceConfiguration.class.equals(dumperConfig.getDataSourceConfig().getClass())) {
            throw new UnsupportedOperationException("PostgreSQLWalDumper only support JDBCDataSourceConfiguration");
        }
        this.dumperConfig = dumperConfig;
        walEventConverter = new WalEventConverter(dumperConfig);
    }
    
    @Override
    public void start() {
        super.start();
        dump();
    }
    
    private void dump() {
        try (Connection pgConnection = logicalReplication.createPgConnection((StandardJDBCDataSourceConfiguration) dumperConfig.getDataSourceConfig());
             PGReplicationStream stream = logicalReplication.createReplicationStream(pgConnection, PostgreSQLPositionInitializer.getUniqueSlotName(pgConnection), walPosition.getLogSequenceNumber())) {
            PostgreSQLTimestampUtils utils = new PostgreSQLTimestampUtils(pgConnection.unwrap(PgConnection.class).getTimestampUtils());
            DecodingPlugin decodingPlugin = new TestDecodingPlugin(utils);
            while (isRunning()) {
                ByteBuffer message = stream.readPending();
                if (null == message) {
                    ThreadUtil.sleep(10L);
                    continue;
                }
                AbstractWalEvent event = decodingPlugin.decode(message, new PostgreSQLLogSequenceNumber(stream.getLastReceiveLSN()));
                Record record = walEventConverter.convert(event);
                if (!(event instanceof PlaceholderEvent) && log.isDebugEnabled()) {
                    log.debug("dump, event={}, record={}", event, record);
                }
                pushRecord(record);
            }
        } catch (final SQLException ex) {
            throw new IngestException(ex);
        }
    }
    
    private void pushRecord(final Record record) {
        try {
            channel.pushRecord(record);
        } catch (final InterruptedException ignored) {
        }
    }
}

