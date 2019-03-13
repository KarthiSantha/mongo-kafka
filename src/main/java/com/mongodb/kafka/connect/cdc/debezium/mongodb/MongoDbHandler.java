/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original Work: Apache License, Version 2.0, Copyright 2017 Hans-Peter Grahsl.
 */

package com.mongodb.kafka.connect.cdc.debezium.mongodb;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.connect.errors.DataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bson.BsonDocument;

import com.mongodb.client.model.WriteModel;

import com.mongodb.kafka.connect.MongoSinkConnectorConfig;
import com.mongodb.kafka.connect.cdc.CdcOperation;
import com.mongodb.kafka.connect.cdc.debezium.DebeziumCdcHandler;
import com.mongodb.kafka.connect.cdc.debezium.OperationType;
import com.mongodb.kafka.connect.converter.SinkDocument;

public class MongoDbHandler extends DebeziumCdcHandler {
    static final String ID_FIELD = "_id";
    static final String JSON_ID_FIELD = "id";

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDbHandler.class);

    public MongoDbHandler(final MongoSinkConnectorConfig config) {
        super(config);
        final Map<OperationType, CdcOperation> operations = new HashMap<>();
        operations.put(OperationType.CREATE, new MongoDbInsert());
        operations.put(OperationType.READ, new MongoDbInsert());
        operations.put(OperationType.UPDATE, new MongoDbUpdate());
        operations.put(OperationType.DELETE, new MongoDbDelete());
        registerOperations(operations);
    }

    public MongoDbHandler(final MongoSinkConnectorConfig config,
                          final Map<OperationType, CdcOperation> operations) {
        super(config);
        registerOperations(operations);
    }

    @Override
    public Optional<WriteModel<BsonDocument>> handle(final SinkDocument doc) {

        BsonDocument keyDoc = doc.getKeyDoc().orElseThrow(
                () -> new DataException("Error: key document must not be missing for CDC mode")
        );

        BsonDocument valueDoc = doc.getValueDoc()
                .orElseGet(BsonDocument::new);

        if (keyDoc.containsKey(JSON_ID_FIELD)
                && valueDoc.isEmpty()) {
            LOGGER.debug("skipping debezium tombstone event for kafka topic compaction");
            return Optional.empty();
        }

        LOGGER.debug("key: " + keyDoc.toString());
        LOGGER.debug("value: " + valueDoc.toString());

        return Optional.of(getCdcOperation(valueDoc).perform(doc));
    }

}