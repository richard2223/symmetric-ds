/*
 * SymmetricDS is an open source database synchronization solution.
 *   
 * Copyright (C) Chris Henson <chenson42@users.sourceforge.net>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.jumpmind.symmetric.db.mssql;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jumpmind.symmetric.db.AbstractDbDialect;
import org.jumpmind.symmetric.db.IDbDialect;

public class MsSqlDbDialect extends AbstractDbDialect implements IDbDialect {

    static final Log logger = LogFactory.getLog(MsSqlDbDialect.class);

    protected void initForSpecificDialect() {
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(String schema, String tableName, String triggerName) {
        return jdbcTemplate.queryForInt(
                "select count(*) from sysobjects where type = 'TR' AND name = ?"                
                        , new Object[] { triggerName }) > 0;
    }

    public void disableSyncTriggers() {
        jdbcTemplate.update("set context_info 0x1");
    }

    public void enableSyncTriggers() {
        jdbcTemplate.update("set context_info 0x0");
    }

    public String getSyncTriggersExpression() {
        return "select @SyncEnabled = context_info from master.dbo.sysprocesses where spid=@@SPID";
    }

    public String getTransactionTriggerExpression() {
        return "@TransactionId";
    }

    /**
     * SQL Server always pads character fields out to the right to fill out field with space characters.
     * @return true always
     */
    public boolean isCharSpacePadded() {
        return true;
    }

    /**
     * @return false always
     */
    public boolean isCharSpaceTrimmed() {
        return false;
    }

    /**
     * SQL Server pads an empty string with spaces.
     * @return false always
     */
    public boolean isEmptyStringNulled() {
        return false;
    }

    /**
     * Nothing to do for SQL Server
     */
    public void purge() {
    }

    public String getDefaultCatalog() {
        return (String) jdbcTemplate.queryForObject("select DB_NAME()", String.class);
    }

    public String getDefaultSchema() {
        return (String) jdbcTemplate.queryForObject("select SCHEMA_NAME()", String.class);
    }

    public void removeTrigger(String schemaName, String triggerName, String tableName) {
        schemaName = schemaName == null ? "" : (schemaName + ".");
        try {
            jdbcTemplate.update("drop trigger " + schemaName + triggerName);
        } catch (Exception e) {
            logger.warn("Trigger does not exist");
        }
    }
    
    public void removeTrigger(String schemaName, String triggerName) {
        removeTrigger(schemaName, triggerName, null);
    }

    /**
     * SQL Server is case insensitive.
     * @return false always
     */
    public boolean supportsMixedCaseNamesInCatalog() {
        return false;
    }


}
