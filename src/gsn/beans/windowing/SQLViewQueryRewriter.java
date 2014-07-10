/**
* Global Sensor Networks (GSN) Source Code
* Copyright (c) 2006-2014, Ecole Polytechnique Federale de Lausanne (EPFL)
* 
* This file is part of GSN.
* 
* GSN is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* GSN is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with GSN.  If not, see <http://www.gnu.org/licenses/>.
* 
* File: src/gsn/beans/windowing/SQLViewQueryRewriter.java
*
* @author gsn_devs
* @author Ali Salehi
* @author Mehdi Riahi
* @author Timotee Maret
*
*/

package gsn.beans.windowing;

import gsn.Main;
import gsn.beans.DataField;
import gsn.beans.StreamElement;
import gsn.storage.SQLUtils;
import gsn.storage.StorageManager;

import java.io.Serializable;
import java.sql.SQLException;

import org.apache.log4j.Logger;

public abstract class SQLViewQueryRewriter extends QueryRewriter {

    private static final transient Logger logger = Logger.getLogger(SQLViewQueryRewriter.class);
    protected static StorageManager storageManager = Main.getWindowStorage();
    public static final CharSequence VIEW_HELPER_TABLE = Main.getWindowStorage().tableNameGeneratorInString("_SQL_VIEW_HELPER_".toLowerCase());
    private static DataField[] viewHelperFields = new DataField[]{new DataField("u_id", "varchar(17)")};

    static {
        try {
            if (storageManager.tableExists(VIEW_HELPER_TABLE)) {
                storageManager.executeDropTable(VIEW_HELPER_TABLE);
            }
            storageManager.executeCreateTable(VIEW_HELPER_TABLE, viewHelperFields, false);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }
    protected StringBuilder cachedSqlQuery;

    @Override
    public boolean initialize() {
        if (streamSource == null) {
            throw new RuntimeException("Null Pointer Exception: streamSource is null");
        }
        try {
            // Initializing view helper table entry for this stream source
            storageManager.executeInsert(VIEW_HELPER_TABLE, viewHelperFields, new StreamElement(viewHelperFields,
                    new Serializable[]{streamSource.getUIDStr().toString()}, -1));

            storageManager.executeCreateView(streamSource.getUIDStr(), createViewSQL());
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return true;
    }

    @Override
    public StringBuilder rewrite(String query) {
        if (streamSource == null) {
            throw new RuntimeException("Null Pointer Exception: streamSource is null");
        }
        return SQLUtils.newRewrite(query, streamSource.getAlias(), streamSource.getUIDStr());
    }

    @Override
    public void dispose() {
        if (streamSource == null) {
            throw new RuntimeException("Null Pointer Exception: streamSource is null");
        }
        try {
            storageManager.executeDropView(streamSource.getUIDStr());
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public boolean dataAvailable(long timestamp) {
        try {
            //TODO : can we use prepareStatement instead of creating a new query each time
            StringBuilder query = new StringBuilder("update ").append(VIEW_HELPER_TABLE);
            query.append(" set timed=").append(timestamp).append(" where u_id='").append(streamSource.getUIDStr());
            query.append("' ");
            storageManager.executeUpdate(query);
            // can be ignored if no queries specified on the view. Every new data-item will then be visible when inserted, so it always returns true.
           // if (storageManager.isThereAnyResult(new StringBuilder("select * from ").append(streamSource.getUIDStr()))) {
                if (logger.isDebugEnabled()) {
                    logger.debug(streamSource.getWrapper().getWrapperName() + " - Output stream produced/received from a wrapper " + streamSource.toString());
                }
                return streamSource.windowSlided();
            //}
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    public abstract CharSequence createViewSQL();
}
