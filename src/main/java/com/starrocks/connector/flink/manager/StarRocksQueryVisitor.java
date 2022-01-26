/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starrocks.connector.flink.manager;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.sql.ResultSetMetaData;
import java.util.Map;

import com.starrocks.connector.flink.connection.StarRocksJdbcConnectionProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
public class StarRocksQueryVisitor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(StarRocksQueryVisitor.class);

    private final StarRocksJdbcConnectionProvider jdbcConnProvider;
    private final String database;
    private final String table;

    public StarRocksQueryVisitor(StarRocksJdbcConnectionProvider jdbcConnProvider, String database, String table) {
        this.jdbcConnProvider = jdbcConnProvider;
        this.database = database;
        this.table = table;
    }

    public List<Map<String, Object>> getTableColumnsMetaData() {
        final String query = "select `COLUMN_NAME`, `COLUMN_KEY`, `DATA_TYPE`, `COLUMN_SIZE`, `DECIMAL_DIGITS` from `information_schema`.`COLUMNS` where `TABLE_SCHEMA`=? and `TABLE_NAME`=?;";
        List<Map<String, Object>> rows;
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Executing query '%s'", query));
            }
            rows = executeQuery(query, this.database, this.table);
        } catch (ClassNotFoundException se) {
            throw new IllegalArgumentException("Failed to find jdbc driver." + se.getMessage(), se);
        } catch (SQLException se) {
            throw new IllegalArgumentException("Failed to get table schema info from StarRocks. " + se.getMessage(), se);
        }
        return rows;
    }

    private List<Map<String, Object>> executeQuery(String query, String... args) throws ClassNotFoundException, SQLException {
        Connection dbConn = jdbcConnProvider.getConnection();
        PreparedStatement stmt = dbConn.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        for (int i = 0; i < args.length; i++) {
            stmt.setString(i + 1, args[i]);
        }
        ResultSet rs = stmt.executeQuery();
        rs.next();
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        List<Map<String, Object>> list = new ArrayList<>();
        int currRowIndex = rs.getRow();
        rs.beforeFirst();
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>(columns);
            for (int i = 1; i <= columns; ++i) {
                row.put(meta.getColumnName(i), rs.getObject(i));
            }
            list.add(row);
        }
        rs.absolute(currRowIndex);
        rs.close();
        dbConn.close();
        return list;
    }

    public Long getQueryCount(String SQL) {
        Long count = 0L;
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Executing query '%s'", SQL));
            }
            List<Map<String, Object>> data = executeQuery(SQL);
            Object opCount = data.get(0).values().stream().findFirst().get();
            if (null == opCount) {
                throw new RuntimeException("Faild to get data count from StarRocks. ");
            }
            count = (Long)opCount;
        } catch (ClassNotFoundException se) {
            throw new IllegalArgumentException("Failed to find jdbc driver." + se.getMessage(), se);
        } catch (SQLException se) {
            throw new IllegalArgumentException("Failed to get data count from StarRocks. " + se.getMessage(), se);
        }
        return count;
    }
}
