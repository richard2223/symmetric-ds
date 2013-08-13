package org.jumpmind.db.platform.sqlanywhere;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;

/*
 * Reads a database model from a Sybase database.
 */
public class SqlAnywhereDdlReader extends AbstractJdbcDdlReader {

    /* The regular expression pattern for the ISO dates. */
    private Pattern isoDatePattern = Pattern.compile("'(\\d{4}\\-\\d{2}\\-\\d{2})'");

    /* The regular expression pattern for the ISO times. */
    private Pattern isoTimePattern = Pattern.compile("'(\\d{2}:\\d{2}:\\d{2})'");

    public SqlAnywhereDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern("%");
    }

    @Override
    protected Table readTable(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        Table table = super.readTable(connection, metaData, values);

        if (table != null) {
            // Sybase does not return the auto-increment status via the database
            // metadata
            determineAutoIncrementFromResultSetMetaData(connection, table, table.getColumns());
        }
        return table;
    }

    @Override
    protected Integer mapUnknownJdbcTypeForColumn(Map<String, Object> values) {
        String typeName = (String) values.get("TYPE_NAME");
        if (typeName != null && typeName.toUpperCase().startsWith("TEXT")) {
            return Types.LONGVARCHAR;
        } else {
            return super.mapUnknownJdbcTypeForColumn(values);
        }
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String,Object> values) throws SQLException {
        Column column = super.readColumn(metaData, values);

        if ((column.getMappedTypeCode() == Types.NUMERIC) && (column.getSizeAsInt() == 19)
                && (column.getScale() == 0)) {
            // Back-mapping to BIGINT
            column.setMappedTypeCode(Types.BIGINT);
        } else if (column.getDefaultValue() != null) {
            if (column.getMappedTypeCode() == Types.TIMESTAMP) {
                // Sybase maintains the default values for DATE/TIME jdbc types,
                // so we have to
                // migrate the default value to TIMESTAMP
                Matcher matcher = isoDatePattern.matcher(column.getDefaultValue());
                Timestamp timestamp = null;

                if (matcher.matches()) {
                    timestamp = new Timestamp(Date.valueOf(matcher.group(1)).getTime());
                } else {
                    matcher = isoTimePattern.matcher(column.getDefaultValue());
                    if (matcher.matches()) {
                        timestamp = new Timestamp(Time.valueOf(matcher.group(1)).getTime());
                    }
                }
                if (timestamp != null) {
                    column.setDefaultValue(timestamp.toString());
                }
            } else if (TypeMap.isTextType(column.getMappedTypeCode())) {
                column.setDefaultValue(unescape(column.getDefaultValue(), "'", "''"));
            }
        }
        return column;
    }

    @Override
    protected void readIndex(DatabaseMetaDataWrapper metaData, Map<String,Object> values, Map<String,IIndex> knownIndices)
            throws SQLException {
        if (getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn()) {
            String indexName = (String) values.get("INDEX_NAME");

            // Sometimes, Sybase keeps the delimiter quotes around the index
            // names
            // when returning them in the metadata, so we strip them
            if (indexName != null) {
                String delimiter = getPlatformInfo().getDelimiterToken();

                if ((indexName != null) && indexName.startsWith(delimiter)
                        && indexName.endsWith(delimiter)) {
                    indexName = indexName.substring(delimiter.length(), indexName.length()
                            - delimiter.length());
                    values.put("INDEX_NAME", indexName);
                }
            }
        }
        super.readIndex(metaData, values, knownIndices);
    }


    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) throws SQLException {
        // We can simply check the sysindexes table where a specific flag is set
        // for pk indexes
        StringBuffer query = new StringBuffer();

        query.append("SELECT name = sysindexes.name FROM sysindexes, sysobjects WHERE sysobjects.name = '");
        query.append(table.getName());
        query.append("' AND sysindexes.name = '");
        query.append(index.getName());
        query.append("' AND sysobjects.id = sysindexes.id AND (sysindexes.status & 2048) > 0");

        Statement stmt = connection.createStatement();

        try {
            ResultSet rs = stmt.executeQuery(query.toString());
            boolean result = rs.next();

            rs.close();
            return result;
        } finally {
            stmt.close();
        }
    }
}
