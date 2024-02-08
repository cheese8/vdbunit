/*
 * Copyright 2002-2016 the original author or authors
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
 */

package com.github.springtestdbunit;

import java.sql.SQLException;
import java.util.Map;

import org.dbunit.database.AbstractDatabaseConnection;
import org.dbunit.database.IDatabaseConnection;

/**
 * Holds a number of {@link IDatabaseConnection} beans.
 *
 * @author Vasiliy Gagin
 */
public class DatabaseConnections {

    private final Map<String, AbstractDatabaseConnection> connectionByName;
    private final String defaultName;

    public DatabaseConnections(Map<String, AbstractDatabaseConnection> connectionByName, String defaultName) {
        this.connectionByName = connectionByName;
        this.defaultName = defaultName;
    }

    public Map<String, AbstractDatabaseConnection> getConnectionByName() {
        return connectionByName;
    }

    public void closeAll() throws SQLException {
        for (AbstractDatabaseConnection connection : this.connectionByName.values()) {
            connection.close();
        }
    }

    public String determineConnectionName(String name) {
        if (name == null || name.length() == 0) {
            if (defaultName == null) {
                throw new IllegalArgumentException(
                        "Requested a IDatabaseConnection without specifying name, but multiple connections available: "
                                + connectionByName.keySet() + ", Please provide connection name");
            }
            name = defaultName;
        }
        return name;
    }

    public AbstractDatabaseConnection get(String name) {
        name = determineConnectionName(name);
        AbstractDatabaseConnection connection = connectionByName.get(name);
        if (connection == null) {
            throw new IllegalStateException("Unable to find IDatabaseConnection named " + name);
        }
        return connection;
    }
}
