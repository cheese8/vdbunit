/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.dataset.datatype;

import java.sql.Types;
import java.util.Collection;
import java.util.Collections;

import org.dbunit.dataset.datatype.ToleratedDeltaMap.ToleratedDelta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic factory that handle standard JDBC types.
 *
 * @author Manuel Laflamme
 * @since May 17, 2003
 * @version $Revision$
 */
public class DefaultDataTypeFactory implements IDataTypeFactory, IDbProductRelatable {

    private final ToleratedDeltaMap _toleratedDeltaMap = new ToleratedDeltaMap();

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DefaultDataTypeFactory.class);
    /**
     * Database product names supported.
     */
    private static final Collection<String> DATABASE_PRODUCTS = Collections.singletonList("derby");

    /**
     * @see IDbProductRelatable#getValidDbProducts()
     */
    @Override
    public Collection<String> getValidDbProducts() {
        return DATABASE_PRODUCTS;
    }

    /**
     * @see org.dbunit.dataset.datatype.IDataTypeFactory#createDataType(int,
     *      java.lang.String)
     */
    @Override
    public DataType createDataType(int sqlType, String sqlTypeName) throws DataTypeException {
        if (logger.isDebugEnabled()) {
            logger.debug("createDataType(sqlType={}, sqlTypeName={}) - start", sqlType, sqlTypeName);
        }

        DataType dataType = DataType.UNKNOWN;
        if (sqlType != Types.OTHER) {
            dataType = DataType.forSqlType(sqlType);
        } else {
            // Necessary for compatibility with DbUnit 1.5 and older
            // BLOB
            if ("BLOB".equals(sqlTypeName)) {
                return DataType.BLOB;
            }

            // CLOB
            if ("CLOB".equals(sqlTypeName)) {
                return DataType.CLOB;
            }
        }
        return dataType;
    }

    /**
     * @see org.dbunit.dataset.datatype.IDataTypeFactory#createDataType(int,
     *      java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public DataType createDataType(int sqlType, String sqlTypeName, String tableName, String columnName)
            throws DataTypeException {
        if (logger.isDebugEnabled()) {
            logger.debug("createDataType(sqlType={} , sqlTypeName={}, tableName={}, columnName={}) - start",
                    sqlType, sqlTypeName, tableName, columnName);
        }

        if (sqlType == Types.NUMERIC || sqlType == Types.DECIMAL) {
            // Check if the user has set a tolerance delta for this floating point field
            ToleratedDelta delta = _toleratedDeltaMap.findToleratedDelta(tableName, columnName);
            // Found a toleratedDelta object
            if (delta != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Creating NumberTolerantDataType for table={}, column={}, toleratedDelta={}",
                            tableName, columnName, delta.getToleratedDelta());
                }

                return new NumberTolerantDataType("NUMERIC_WITH_TOLERATED_DELTA", sqlType, delta.getToleratedDelta());
            }
        }

        // In all other cases (default) use the default data type creation
        return this.createDataType(sqlType, sqlTypeName);
    }

    /**
     * @return The whole map of tolerated delta objects that have been set until now
     * @since 2.3.0
     */
    public ToleratedDeltaMap getToleratedDeltaMap() {
        return _toleratedDeltaMap;
    }

    /**
     * Adds a tolerated delta to this data type factory to be used for numeric
     * comparisons
     * 
     * @param delta The new tolerated delta object
     * @since 2.3.0
     */
    public void addToleratedDelta(ToleratedDelta delta) {
        this._toleratedDeltaMap.addToleratedDelta(delta);
    }

    /**
     * Returns a string representation of this {@link DefaultDataTypeFactory}
     * instance
     * 
     * @since 2.4.6
     */
    @Override
    public String toString() {
        return getClass().getName() + "[" +
                "_toleratedDeltaMap=" + _toleratedDeltaMap +
                "]";
    }
}
